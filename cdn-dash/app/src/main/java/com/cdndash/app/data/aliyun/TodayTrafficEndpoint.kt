package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.TodayTraffic
import com.cdndash.app.domain.todayTrafficWindow
import java.time.Instant
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
 *
 * 签名、HTTP、错误归一全部在 [AliyunRpcClient]；Action/Version 与载荷展开复用 [TrafficPayload]，
 * 本类只负责时间窗、业务参数与聚合。
 */
class TodayTrafficEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun todayTraffic(account: AccountCredentials, now: Instant): ApiResult<TodayTraffic> {
        val window = todayTrafficWindow(now)
        return rpc.call(
            account = account,
            action = TRAFFIC_ACTION,
            version = TRAFFIC_API_VERSION,
            businessParams = mapOf(
                "StartTime" to formatRpcTimestamp(window.start),
                "EndTime" to formatRpcTimestamp(window.end),
                "Interval" to INTERVAL_SECONDS,
            ),
        ).map { body -> parse(body, now) }
    }

    private fun parse(body: JsonObject, now: Instant): TodayTraffic {
        val modules = body.trafficDataModules()
        var total = 0L
        var latest: Instant? = null
        for (module in modules) {
            val dataPoint = module as? JsonObject ?: continue
            total += dataPoint["Value"].trafficBytesValue()
            val timestamp = (dataPoint["TimeStamp"] as? JsonPrimitive)
                ?.contentOrNull
                ?.let(::parseAliyunTimestamp)
            if (timestamp != null && (latest == null || timestamp.isAfter(latest))) {
                latest = timestamp
            }
        }
        return TodayTraffic(bytes = total, dataThrough = latest ?: now)
    }

    companion object {
        const val INTERVAL_SECONDS: String = "300"
    }
}
