package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.TodayTraffic
import com.cdndash.app.domain.todayTrafficWindow
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 今日累计端点（票据 03）：`DescribeDomainTrafficData`。
 *
 * - 不传 `DomainName`：账号下全部加速域名（ADR 0016），不做域名白名单。
 * - `Interval=300`：5 分钟粒度，跨度 ≤3 天，延迟约 15 分钟（ADR 0016）。
 * - 时间窗：北京时间今天 00:00 → 请求时刻 [now]（ISO 8601 UTC，见 [formatRpcTimestamp]）。
 * - 结果：各数据点 `Value` 字节数之和；数据截止时刻取最后一个数据点的 `TimeStamp`，
 *   无数据点时退化为 [now]（口径说明见 `TodayTraffic.dataThrough`）。
 */
class TodayTrafficEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun todayTraffic(account: AccountCredentials, now: Instant): ApiResult<TodayTraffic> {
        val window = todayTrafficWindow(now)
        return rpc.call(
            account = account,
            action = ACTION,
            version = API_VERSION,
            businessParams = mapOf(
                "StartTime" to formatRpcTimestamp(window.start),
                "EndTime" to formatRpcTimestamp(window.end),
                "Interval" to INTERVAL_SECONDS,
            ),
        ).map { body -> parse(body, now) }
    }

    private fun parse(body: JsonObject, now: Instant): TodayTraffic {
        val modules = body.dataModules()
        var total = 0L
        var latest: Instant? = null
        for (module in modules) {
            val dataPoint = module as? JsonObject ?: continue
            total += dataPoint["Value"].bytesValue()
            val timestamp = (dataPoint["TimeStamp"] as? JsonPrimitive)
                ?.contentOrNull
                ?.let(::parseAliyunTimestamp)
            if (timestamp != null && (latest == null || timestamp.isAfter(latest))) {
                latest = timestamp
            }
        }
        return TodayTraffic(bytes = total, dataThrough = latest ?: now)
    }

    private fun JsonObject.dataModules(): List<JsonElement> {
        val node = (this["TrafficDataPerInterval"] as? JsonObject)?.get("DataModule")
        return when (node) {
            is JsonArray -> node
            // 单点时阿里云历史上可能返回对象而不是数组，两种都接受，避免崩溃。
            is JsonObject -> listOf(node)
            else -> emptyList()
        }
    }

    /** `Value` 可能是字符串或数字；缺失/非法一律按 0，空数据不崩溃。 */
    private fun JsonElement?.bytesValue(): Long =
        (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.roundToLong() ?: 0L

    companion object {
        const val ACTION: String = "DescribeDomainTrafficData"
        const val API_VERSION: String = "2018-05-10"
        const val INTERVAL_SECONDS: String = "300"
    }
}
