package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.TodayTraffic
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 今日累计快照的 JSON 编解码：写 AccountStore 的 `TODAY_TOTAL` 模块时使用。
 *
 * 只存领域结果的稳定字段（ISO 8601 时刻），不存接口原始响应；读取（票据 07）
 * 复用 [decode]，损坏的载荷返回 null 而不是崩溃。
 */
object TodayTrafficSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(today: TodayTraffic): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(bytes = today.bytes, dataThrough = today.dataThrough.toString()),
        )

    fun decode(raw: String): TodayTraffic? = runCatching {
        val payload = json.decodeFromString(Payload.serializer(), raw)
        TodayTraffic(bytes = payload.bytes, dataThrough = Instant.parse(payload.dataThrough))
    }.getOrNull()

    @Serializable
    private data class Payload(
        val bytes: Long,
        val dataThrough: String,
    )
}
