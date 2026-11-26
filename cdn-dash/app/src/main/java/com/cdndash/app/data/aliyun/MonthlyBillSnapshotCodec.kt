package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.MonthlyBill
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 月账单快照的 JSON 编解码：写 AccountStore 的 `MONTHLY_BILL` 模块时使用。
 *
 * 只存领域结果的稳定字段：账期写成 `YYYY-MM`，金额写成十进制字符串（不落 Double，
 * 读回时不丢精度）；不存接口原始响应。损坏的载荷返回 null，由调用方重新取数。
 */
object MonthlyBillSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val payloadSerializer = ListSerializer(BillPayload.serializer())

    fun encode(bills: List<MonthlyBill>): String =
        json.encodeToString(payloadSerializer, bills.map { it.toPayload() })

    fun decode(raw: String): List<MonthlyBill>? = runCatching {
        json.decodeFromString(payloadSerializer, raw).map { it.toDomain() }
    }.getOrNull()

    private fun MonthlyBill.toPayload(): BillPayload = BillPayload(
        month = month.toString(),
        pretaxAmount = pretaxAmount.toPlainString(),
        cashAmount = cashAmount.toPlainString(),
        voucherAmount = voucherAmount.toPlainString(),
        currency = currency,
        includesPrepaidPackage = includesPrepaidPackage,
    )

    private fun BillPayload.toDomain(): MonthlyBill = MonthlyBill(
        month = YearMonth.parse(month),
        pretaxAmount = BigDecimal(pretaxAmount),
        cashAmount = BigDecimal(cashAmount),
        voucherAmount = BigDecimal(voucherAmount),
        currency = currency,
        includesPrepaidPackage = includesPrepaidPackage,
    )

    @Serializable
    private data class BillPayload(
        val month: String,
        val pretaxAmount: String,
        val cashAmount: String,
        val voucherAmount: String,
        val currency: String,
        val includesPrepaidPackage: Boolean = false,
    )
}
