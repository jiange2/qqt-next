package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.TrafficPoint
import com.cdndash.app.domain.aggregateSettledDays
import com.cdndash.app.domain.settledWeekWindow
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 已结算 7 天端点（票据 04）：`DescribeDomainTrafficData`。
 *
 * - 不传 `DomainName`：账号下全部加速域名（ADR 0016），与今日累计同一口径。
 * - `Interval=3600`：小时粒度，避免日粒度接口 4–24 小时延迟导致昨日半截。
 * - 时间窗：北京时间「今天-7」00:00 → 「今天」00:00（[settledWeekWindow]）。
 * - 解析出 [TrafficPoint] 后交给纯函数 [aggregateSettledDays] 按北京自然日归桶、
 *   丢弃今天、补齐 7 个日点；本类只负责业务参数与 JSON 解析。
 *
 * 签名、HTTP、错误归一全部在 [AliyunRpcClient]，本端点不重复处理。
 * 本票据只改这个文件，不碰 [AliyunApiImpl]（接缝已就位）。
 */
class TrafficWeekEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun settledDays(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<List<DayTrafficPoint>> {
        val window = settledWeekWindow(now)
        return rpc.call(
            account = account,
            action = ACTION,
            version = API_VERSION,
            businessParams = mapOf(
                "StartTime" to formatRpcTimestamp(window.start),
                "EndTime" to formatRpcTimestamp(window.end),
                "Interval" to INTERVAL_SECONDS,
            ),
        ).map { body -> aggregateSettledDays(parsePoints(body), now) }
    }

    /** 响应的小时点；缺失/非法的时间戳跳过，不崩溃（空列表由聚合函数补 7 个零点）。 */
    private fun parsePoints(body: JsonObject): List<TrafficPoint> {
        val modules = body.dataModules()
        val points = ArrayList<TrafficPoint>(modules.size)
        for (module in modules) {
            val dataPoint = module as? JsonObject ?: continue
            val timestamp = (dataPoint["TimeStamp"] as? JsonPrimitive)
                ?.contentOrNull
                ?.let(::parseAliyunTimestamp)
                ?: continue
            points += TrafficPoint(at = timestamp, bytes = dataPoint["Value"].bytesValue())
        }
        return points
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
        const val INTERVAL_SECONDS: String = "3600"
    }
}
