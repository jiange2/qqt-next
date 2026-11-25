package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.isPrepaidCdnBillLine
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 账号账单端点（票据 08）：`QueryAccountBill`（BSS OpenAPI，Version `2017-12-14`）。
 *
 * 口径（ADR 0017）：
 * - 每个账期一次请求，`BillingCycle=YYYY-MM`、`Granularity=MONTHLY`，账期由调用方给出
 *   （[com.cdndash.app.domain.recentBillingMonths] 已剔除当月）；账号内逐月串行。
 * - 不按产品过滤；主数求和 `PretaxAmount`，现金求和 `CashAmount`，
 *   代金券求和 `DeductedByCashCoupons + DeductedByCoupons`。金额全程 [BigDecimal]。
 * - `IsGroupByProduct=true`：BSS 只有按产品分组时才返回 `SubscriptionType` 与
 *   `ProductCode`，而这两项是预付费标注的唯一证据。
 * - 预付费标注只依据明细里的 `SubscriptionType=Subscription` 且 `ProductCode` 属于 CDN；
 *   字段缺失时该月保持不标注（不按金额或产品名猜测，见 [isPrepaidCdnBillLine]）。
 * - 接口无税额字段，这里也不产出税额概念。
 */
class BillEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun monthlyBills(
        account: AccountCredentials,
        months: List<YearMonth>,
    ): ApiResult<List<MonthlyBill>> {
        val bills = ArrayList<MonthlyBill>(months.size)
        for (month in months) {
            val result = monthBill(account, month)
            // Failure 不带泛型参数，先按失败分支返回，再取成功值（避免对泛型做 is 判断）。
            val failure = result.failureOrNull()
            if (failure != null) return failure
            bills += requireNotNull(result.successOrNull()) { "成功分支不应为空" }
        }
        return ApiResult.Ok(bills)
    }

    /** 取一个账期；正常账号一页（PageSize=300）就够，超限时才继续翻页，避免静默少算。 */
    private suspend fun monthBill(
        account: AccountCredentials,
        month: YearMonth,
    ): ApiResult<MonthlyBill> {
        var aggregate = Aggregate()
        var pageNum = 1
        while (true) {
            val result = rpc.call(
                account = account,
                action = ACTION,
                version = API_VERSION,
                businessParams = mapOf(
                    "BillingCycle" to month.toString(),
                    "Granularity" to GRANULARITY_MONTHLY,
                    // 获得 SubscriptionType/ProductCode，供预付费标注判断；不改变合计口径。
                    "IsGroupByProduct" to "true",
                    "PageNum" to pageNum.toString(),
                    "PageSize" to PAGE_SIZE.toString(),
                ),
            )
            val failure = result.failureOrNull()
            if (failure != null) return failure
            val body = requireNotNull(result.successOrNull()) { "成功分支不应为空" }
            val items = body.billItems()
            aggregate = aggregate.merge(items)
            val totalCount = body.totalCount()
            val fetchedAll = totalCount == null || aggregate.itemCount >= totalCount
            if (items.isEmpty() || fetchedAll || pageNum >= MAX_PAGES) break
            pageNum++
        }
        return ApiResult.Ok(aggregate.toMonthlyBill(month))
    }

    /** 一个账期多页明细的累加结果。 */
    private data class Aggregate(
        val pretaxAmount: BigDecimal = BigDecimal.ZERO,
        val cashAmount: BigDecimal = BigDecimal.ZERO,
        val voucherAmount: BigDecimal = BigDecimal.ZERO,
        /** 币种取明细里第一个非空值；缺失时按人民币展示。 */
        val currency: String? = null,
        val includesPrepaidPackage: Boolean = false,
        val itemCount: Int = 0,
    ) {

        fun merge(items: List<JsonObject>): Aggregate {
            var pretax = pretaxAmount
            var cash = cashAmount
            var voucher = voucherAmount
            var billCurrency = currency ?: items.firstNotNullOfOrNull { it.string("Currency") }
            var prepaid = includesPrepaidPackage
            for (item in items) {
                pretax += item.decimal("PretaxAmount")
                cash += item.decimal("CashAmount")
                voucher += item.decimal("DeductedByCashCoupons") +
                    item.decimal("DeductedByCoupons")
                prepaid = prepaid || isPrepaidCdnBillLine(
                    subscriptionType = item.string("SubscriptionType"),
                    productCode = item.string("ProductCode"),
                )
            }
            return Aggregate(
                pretaxAmount = pretax,
                cashAmount = cash,
                voucherAmount = voucher,
                currency = billCurrency,
                includesPrepaidPackage = prepaid,
                itemCount = itemCount + items.size,
            )
        }

        fun toMonthlyBill(month: YearMonth): MonthlyBill = MonthlyBill(
            month = month,
            pretaxAmount = pretaxAmount,
            cashAmount = cashAmount,
            voucherAmount = voucherAmount,
            currency = currency ?: DEFAULT_CURRENCY,
            includesPrepaidPackage = includesPrepaidPackage,
        )
    }

    private fun JsonObject.billItems(): List<JsonObject> {
        val itemsNode = (this["Data"] as? JsonObject)?.get("Items")
        val itemNode = (itemsNode as? JsonObject)?.get("Item")
        return when (itemNode) {
            is JsonArray -> itemNode.mapNotNull { it as? JsonObject }
            // 单条时阿里云历史上可能返回对象而不是数组，两种都接受。
            is JsonObject -> listOf(itemNode)
            else -> emptyList()
        }
    }

    private fun JsonObject.totalCount(): Int? =
        ((this["Data"] as? JsonObject)?.get("TotalCount") as? JsonPrimitive)
            ?.contentOrNull
            ?.toIntOrNull()

    companion object {
        const val ACTION: String = "QueryAccountBill"
        const val API_VERSION: String = "2017-12-14"
        const val GRANULARITY_MONTHLY: String = "MONTHLY"

        /** 单页上限（接口允许 300）；超过时按 `TotalCount` 继续翻页。 */
        const val PAGE_SIZE: Int = 300

        /** 翻页兜底：防止接口返回异常 `TotalCount` 时无限请求。 */
        const val MAX_PAGES: Int = 10
    }
}

/** 明细里没有币种时按人民币展示（接口只返回 CNY/USD/JPY）。 */
private const val DEFAULT_CURRENCY = "CNY"

/** 金额字段可能是数字或字符串；缺失/非法按 0，不崩溃也不臆造。 */
private fun JsonObject.decimal(name: String): BigDecimal =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull() ?: BigDecimal.ZERO

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
