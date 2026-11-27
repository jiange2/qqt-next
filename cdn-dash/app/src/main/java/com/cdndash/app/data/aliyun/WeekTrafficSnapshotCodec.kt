package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.DayTrafficPoint
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 已结算 7 天快照的 JSON 编解码：写 AccountStore 的 `WEEK_TREND` 模块时使用（票据 04、票据 10）。
 *
 * 只存领域结果的稳定字段（北京自然日 ISO 日期 + 字节数 + 区域拆分），不存接口原始响应；
 * 展示旧快照时复用 [decode]，损坏的载荷返回 null 而不是崩溃。
 *
 * 区域字段是后加的：旧快照没有 `domesticBytes`/`overseasBytes` 时解码成 0，
 * 估算层据此识别「区域数据缺失」并静默，不会把历史快照误读成零消耗（ADR 0018）。
 */
object WeekTrafficSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(days: List<DayTrafficPoint>): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(
                days = days.map { day ->
                    DayPayload(
                        date = day.date.toString(),
                        bytes = day.bytes,
                        domesticBytes = day.domesticBytes,
                        overseasBytes = day.overseasBytes,
                    )
                },
            ),
        )

    fun decode(raw: String): List<DayTrafficPoint>? = runCatching {
        json.decodeFromString(Payload.serializer(), raw).days.map { day ->
            DayTrafficPoint(
                date = LocalDate.parse(day.date),
                bytes = day.bytes,
                domesticBytes = day.domesticBytes,
                overseasBytes = day.overseasBytes,
            )
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
        val domesticBytes: Long = 0L,
        val overseasBytes: Long = 0L,
    )
}
