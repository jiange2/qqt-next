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
 * 资源包端点（票据 05）：`DescribeCdnUserResourcePackage`。
 *
 * - 不传 `Status`：默认只返回 `valid`，会把已耗尽/已关闭的包藏起来；
 *   余量视图要把它们一并列出（User Story 18 的反面：也别漏掉）。
 * - **只接受 `CommodityCode=cdnflowbag`**（CDN 流量包），全站加速/OSS 的包一律丢弃。
 * - 响应条目形如 `ResourcePackageInfos.ResourcePackageInfo[]`，单条目时阿里云历史上
 *   可能返回对象而不是数组，两种都接受。
 * - 容量字段 `InitCapacity`（总量）/`CurrCapacity`（剩余量）可能是字符串；
 *   时间字段 `StartTime`/`EndTime` 是 UTC ISO 8601。字段缺失不崩溃：
 *   容量按 0、时间按 EPOCH、区域按 [ResourcePackage.UNKNOWN_REGION] 兜底。
 * - 分组、排序、「未生效」标注是纯函数（`domain/PackageGrouping.kt`），本类只做请求与解析。
 *
 * 字段名以阿里云文档为准（兼容 `Total`/`Remaining`/`EffectiveTime`/`ExpireTime` 的旧别名，
 * 只要文档或历史返回出现过就接受，避免某版本改名把整页打空）。
 */
class PackageEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun resourcePackages(
        account: AccountCredentials,
        @Suppress("UNUSED_PARAMETER") now: Instant,
    ): ApiResult<List<ResourcePackage>> =
        rpc.call(
            account = account,
            action = ACTION,
            version = API_VERSION,
        ).map { body -> parse(body) }

    private fun parse(body: JsonObject): List<ResourcePackage> =
        body.packageInfos()
            .mapNotNull { it as? JsonObject }
            .mapIndexedNotNull { index, item -> item.toResourcePackage(index) }

    private fun JsonObject.toResourcePackage(index: Int): ResourcePackage? {
        // 没有商品码的条目无法证明是 CDN 流量包，同样排除（宁缺毋滥）。
        val commodityCode = string("CommodityCode") ?: return null
        if (commodityCode != ResourcePackage.CDN_FLOW_PACKAGE_COMMODITY_CODE) return null
        return ResourcePackage(
            instanceId = string("InstanceId") ?: "resource-package-$index",
            commodityCode = commodityCode,
            region = string("Region") ?: ResourcePackage.UNKNOWN_REGION,
            totalBytes = bytes("InitCapacity", "Total"),
            remainingBytes = bytes("CurrCapacity", "Remaining"),
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
    }
}
