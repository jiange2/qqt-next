package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.DayTrafficPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 已结算 7 天快照编解码的兼容性测试（票据 10，ADR 0018）。
 *
 * 区域字段是后加的：新快照必须往返一致，旧快照（只有 date/bytes）必须能读，
 * 且区域字段解码成 0——估算层据此静默，不会把历史数据误读成零消耗。
 */
class WeekTrafficSnapshotCodecTest {

    @Test
    fun `区域字段编码后能原样读回`() {
        val days = listOf(
            DayTrafficPoint(
                date = LocalDate.parse("2026-11-26"),
                bytes = 2_048,
                domesticBytes = 1_536,
                overseasBytes = 512,
            ),
        )

        val decoded = requireNotNull(WeekTrafficSnapshotCodec.decode(WeekTrafficSnapshotCodec.encode(days)))

        assertEquals(days, decoded)
    }

    @Test
    fun `旧快照没有区域字段时解码为零`() {
        val raw = """{"days":[{"date":"2026-11-26","bytes":1024}]}"""

        val decoded = requireNotNull(WeekTrafficSnapshotCodec.decode(raw))

        assertEquals(1, decoded.size)
        assertEquals(1_024L, decoded[0].bytes)
        assertEquals(0L, decoded[0].domesticBytes)
        assertEquals(0L, decoded[0].overseasBytes)
    }

    @Test
    fun `损坏的载荷返回空而不是崩溃`() {
        assertNull(WeekTrafficSnapshotCodec.decode("not-json"))
    }
}
