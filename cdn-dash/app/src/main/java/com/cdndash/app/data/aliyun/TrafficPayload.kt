package com.cdndash.app.data.aliyun

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 今日累计与已结算 7 天共用的 `DescribeDomainTrafficData` 载荷读取。
 *
 * 两个端点打同一个接口、同一版本，只有时间窗与 `Interval` 不同；把 Action/Version 常量、
 * `TrafficDataPerInterval.DataModule` 展开与 `Value` 取字节收在这里，避免两处各写一份、
 * 改漏一处导致解析口径分叉。
 */

/** 流量接口的 Action（账号全量，不传 `DomainName`）。 */
internal const val TRAFFIC_ACTION: String = "DescribeDomainTrafficData"

/** CDN OpenAPI 现行版本；旧版本会返回 `InvalidVersion` 把整页打空。 */
internal const val TRAFFIC_API_VERSION: String = "2018-05-10"

/**
 * 展开 `TrafficDataPerInterval.DataModule`：正常是数组，单点时阿里云历史上可能返回对象，
 * 两种都接受；节点缺失按空列表处理（空数据由上层补 0，不崩溃）。
 */
internal fun JsonObject.trafficDataModules(): List<JsonElement> {
    val node = (this["TrafficDataPerInterval"] as? JsonObject)?.get("DataModule")
    return when (node) {
        is JsonArray -> node
        is JsonObject -> listOf(node)
        else -> emptyList()
    }
}

/** `Value` 可能是字符串或数字；缺失/非法一律按 0，空数据不崩溃。 */
internal fun JsonElement?.trafficBytesValue(): Long =
    (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.roundToLong() ?: 0L
