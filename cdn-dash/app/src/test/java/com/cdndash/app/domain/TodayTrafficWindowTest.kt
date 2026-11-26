package com.cdndash.app.domain

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 今日时间窗与「截至 HH:mm」的纯函数测试（票据 03，spec「Testing Decisions」）。
 * 期望值全部是独立字面量；并显式改动 JVM 默认时区，证明结果不随设备时区变化。
 */
class TodayTrafficWindowTest {

    @Test
    fun `今日窗口起点是北京时间当天零点`() {
        val window = todayTrafficWindow(Instant.parse("2026-11-26T02:30:00Z"))

        assertEquals(Instant.parse("2026-11-25T16:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-11-26T02:30:00Z"), window.end)
    }

    @Test
    fun `跨北京零点前后落在不同自然日`() {
        // 北京 2026-11-27 00:00 整 → 新的一天从这一刻开始。
        assertEquals(
            Instant.parse("2026-11-26T16:00:00Z"),
            todayTrafficWindow(Instant.parse("2026-11-26T16:00:00Z")).start,
        )
        // 北京 2026-11-26 23:59:59 → 仍属 11-26。
        assertEquals(
            Instant.parse("2026-11-25T16:00:00Z"),
            todayTrafficWindow(Instant.parse("2026-11-26T15:59:59Z")).start,
        )
    }

    @Test
    fun `设备时区改变不影响北京日界`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(
                Instant.parse("2026-03-08T16:00:00Z"),
                todayTrafficWindow(Instant.parse("2026-03-08T17:30:00Z")).start,
            )
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                Instant.parse("2026-03-08T16:00:00Z"),
                todayTrafficWindow(Instant.parse("2026-03-08T17:30:00Z")).start,
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `截至时刻按北京时间显示两位时分`() {
        assertEquals("00:05", formatBeijingHourMinute(Instant.parse("2026-11-26T16:05:00Z")))
        assertEquals("23:55", formatBeijingHourMinute(Instant.parse("2026-11-26T15:55:00Z")))
        assertEquals("10:30", formatBeijingHourMinute(Instant.parse("2026-11-26T02:30:00Z")))
    }
}
