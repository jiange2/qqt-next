package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.ResourcePackage
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 资源包端点（票据 05）：CDN `DescribeCdnUserResourcePackage` 与
 * DCDN `DescribeDcdnUserResourcePackage` 双端点合并。
 *
 * - 新版 CDN 资源包是 CDN/全站加速共享商品（`dcdnpaybag`），可能只出现在 DCDN 端点，
 *   所以两个端点都查，按 `InstanceId` 去重（CDN 端点优先）。
 * - 只查 `Status=valid`：已耗尽/已关闭的包不展示；`valid` 但剩余量为 0（刚用完、
 *   状态还没翻转）的条目也在解析时排除。
 * - 只接受 [ResourcePackage.FLOW_PACKAGE_COMMODITY_CODES]；`dcdnpaybag` 商品也涵盖
 *   静态 HTTPS 请求数包，再按容量单位（流量包 Byte、请求数包「次」）排除非流量包。
 * - 响应条目形如 `ResourcePackageInfos.ResourcePackageInfo[]`，单条目时阿里云历史上
 *   可能返回对象而不是数组，两种都接受。
 * - 容量字段 `InitCapacity`（总量）/`CurrCapacity`（剩余量）可能是字符串；
 *   时间字段 `StartTime`/`EndTime` 是 UTC ISO 8601。字段缺失不崩溃：
 *   容量按 0、时间按 EPOCH、区域按 [ResourcePackage.UNKNOWN_REGION] 兜底。
 * - 失败语义：CDN 是主端点，失败即整体失败（不用残缺数据，保留快照回落）；
 *   DCDN 是补充端点，失败容忍（账号可能未开通全站加速或 RAM 未授权该接口）。
 * - 分组、排序、「未生效」标注是纯函数（`domain/PackageGrouping.kt`），本类只做请求与解析。
 */
class PackageEndpoint(
    private val cdnRpc: AliyunRpcClient,
    private val dcdnRpc: AliyunRpcClient,
) {

    suspend fun resourcePackages(
        account: AccountCredentials,
        @Suppress("UNUSED_PARAMETER") now: Instant,
    ): ApiResult<List<ResourcePackage>> {
        val cdnResult = fetchEndpoint(cdnRpc, account, ACTION, API_VERSION)
        if (cdnResult !is ApiResult.Ok) return cdnResult
        val dcdnResult = fetchEndpoint(dcdnRpc, account, DCDN_ACTION, DCDN_API_VERSION)
        val merged = (cdnResult.value + dcdnResult.successOrNull().orEmpty())
            .distinctBy { it.instanceId }
        return ApiResult.Ok(merged)
    }

    /** 单端点只查 valid；失败即整端点失败，避免把残缺列表当全量。 */
    private suspend fun fetchEndpoint(
        rpc: AliyunRpcClient,
        account: AccountCredentials,
        action: String,
        version: String,
    ): ApiResult<List<ResourcePackage>> {
        val result = rpc.call(
            account = account,
            action = action,
            version = version,
            businessParams = mapOf("Status" to "valid"),
        )
        return when (result) {
            is ApiResult.Ok -> ApiResult.Ok(parse(result.value))
            is ApiResult.Failure -> result
        }
    }

    private fun parse(body: JsonObject): List<ResourcePackage> =
        body.packageInfos()
            .mapNotNull { it as? JsonObject }
            .mapIndexedNotNull { index, item -> item.toResourcePackage(index) }

    private fun JsonObject.toResourcePackage(index: Int): ResourcePackage? {
        // 没有商品码的条目无法证明是 CDN 流量包，同样排除（宁缺毋滥）。
        val commodityCode = string("CommodityCode") ?: return null
        if (commodityCode !in ResourcePackage.FLOW_PACKAGE_COMMODITY_CODES) return null
        // dcdnpaybag 也涵盖 HTTPS 请求数包（单位「次」）；单位字段缺失时容忍放行。
        val baseUnit = string("InitCapacityBaseUnit", "CurrCapacityBaseUnit")
        if (baseUnit != null && !baseUnit.equals("Byte", ignoreCase = true)) return null
        // 用完的包不展示：valid 状态可能滞后于余量归零。
        val remainingBytes = bytes("CurrCapacity", "Remaining")
        if (remainingBytes <= 0L) return null
        return ResourcePackage(
            instanceId = string("InstanceId") ?: "resource-package-$index",
            commodityCode = commodityCode,
            region = string("Region") ?: ResourcePackage.UNKNOWN_REGION,
            totalBytes = bytes("InitCapacity", "Total"),
            remainingBytes = remainingBytes,
            effectiveAt = timestamp("StartTime", "EffectiveTime") ?: Instant.EPOCH,
            expiresAt = timestamp("EndTime", "ExpireTime") ?: Instant.EPOCH,
        )
    }

    private fun JsonObject.packageInfos(): List<JsonElement> {
        val node = (this["ResourcePackageInfos"] as? JsonObject)?.get("ResourcePackageInfo")
        return when (node) {
            is JsonArray -> node
            is JsonObject -> listOf(node)
            else -> emptyList()
        }
    }

    /** 取第一个存在且非空的字符串字段；缺失返回 null。 */
    private fun JsonObject.string(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }

    /** 容量可能是字符串或数字、带小数；缺失/非法按 0，空数据不崩溃。 */
    private fun JsonObject.bytes(vararg names: String): Long =
        names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.roundToLong()
        } ?: 0L

    private fun JsonObject.timestamp(vararg names: String): Instant? =
        names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.contentOrNull?.let(::parseAliyunTimestamp)
        }

    companion object {
        const val ACTION: String = "DescribeCdnUserResourcePackage"

        /**
         * CDN OpenAPI 现行版本。旧值 `2017-11-10` 会返回 `InvalidVersion`，把整块资源包打空。
         */
        const val API_VERSION: String = "2018-05-10"

        /** DCDN（全站加速）端点：新版共享商品 `dcdnpaybag` 的包在这里返回。 */
        const val DCDN_ACTION: String = "DescribeDcdnUserResourcePackage"

        const val DCDN_API_VERSION: String = "2018-01-15"
    }
}