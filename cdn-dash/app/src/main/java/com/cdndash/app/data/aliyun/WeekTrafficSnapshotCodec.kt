package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.DayTrafficPoint
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 已结算 7 天快照的 JSON 编解码：写 AccountStore 的 `WEEK_TREND` 模块时使用（票据 04）。
 *
 * 只存领域结果的稳定字段（北京自然日 ISO 日期 + 字节数），不存接口原始响应；
 * 展示旧快照时复用 [decode]，损坏的载荷返回 null 而不是崩溃。
 */
object WeekTrafficSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(days: List<DayTrafficPoint>): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(days = days.map { DayPayload(date = it.date.toString(), bytes = it.bytes) }),
        )

    fun decode(raw: String): List<DayTrafficPoint>? = runCatching {
        json.decodeFromString(Payload.serializer(), raw).days.map { day ->
            DayTrafficPoint(date = LocalDate.parse(day.date), bytes = day.bytes)
        }
    }.getOrNull()

    @Serializable
    private data class Payload(
        val days: List<DayPayload>,
    )

    @Serializable
    private data class DayPayload(
        val date: String,
        val bytes: Long,
    )
}
