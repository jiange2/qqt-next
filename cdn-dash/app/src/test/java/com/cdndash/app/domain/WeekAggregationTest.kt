package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 已结算 7 天聚合的纯函数测试（票据 04，spec「Testing Decisions」）。
 *
 * 期望值全部是独立字面量：北京日界、丢弃今天、恒 7 点、空数据全零、跨时区不变。
 * 基准时刻取北京时间 2026-11-27 10:00，此时「今天」= 11-27，窗口 = 11-20 至 11-26。
 */
class WeekAggregationTest {

    private val now: Instant = Instant.parse("2026-11-27T02:00:00Z")

    @Test
    fun `时间窗是今天减七的北京零点到今天零点`() {
        val window = settledWeekWindow(now)

        // 北京 2026-11-20 00:00 = UTC 2026-11-19T16:00；北京 2026-11-27 00:00 = UTC 2026-11-26T16:00。
        assertEquals(Instant.parse("2026-11-19T16:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-11-26T16:00:00Z"), window.end)
    }

    @Test
    fun `23点55分与次日00点05分落在不同北京日`() {
        val days = aggregateSettledDays(
            listOf(
                TrafficPoint(at = Instant.parse("2026-11-25T15:55:00Z"), bytes = 100),
                TrafficPoint(at = Instant.parse("2026-11-25T16:05:00Z"), bytes = 200),
            ),
            now,
        )

        // 北京 11-25 23:55 → 11-25（第 6 个点）；北京 11-26 00:05 → 11-26（第 7 个点）。
        assertEquals(LocalDate.parse("2026-11-25"), days[5].date)
        assertEquals(100L, days[5].bytes)
        assertEquals(LocalDate.parse("2026-11-26"), days[6].date)
        assertEquals(200L, days[6].bytes)
    }

    @Test
    fun `同一北京日的小时点求和`() {
        val days = aggregateSettledDays(
            listOf(
                TrafficPoint(at = Instant.parse("2026-11-23T17:00:00Z"), bytes = 1_024),
                TrafficPoint(at = Instant.parse("2026-11-24T03:00:00Z"), bytes = 2_048),
                TrafficPoint(at = Instant.parse("2026-11-24T15:00:00Z"), bytes = 4_096),
            ),
            now,
        )

        // 北京 11-24 01:00、11:00、23:00 三个小时点合并为 11-24 一天。
        assertEquals(LocalDate.parse("2026-11-24"), days[4].date)
        assertEquals(7_168L, days[4].bytes)
    }

    @Test
    fun `落在今天的点被丢弃且昨日不出现半截污染`() {
        val days = aggregateSettledDays(
            listOf(
                // 北京 11-26 23:55，昨天，应计入。
                TrafficPoint(at = Instant.parse("2026-11-26T15:55:00Z"), bytes = 512),
                // 北京 11-27 00:00 与 00:05，今天，应全部丢弃。
                TrafficPoint(at = Instant.parse("2026-11-26T16:00:00Z"), bytes = 1_000),
                TrafficPoint(at = Instant.parse("2026-11-26T16:05:00Z"), bytes = 2_000),
            ),
            now,
        )

        assertEquals(7, days.size)
        assertEquals(LocalDate.parse("2026-11-26"), days[6].date)
        assertEquals(512L, days[6].bytes)
        assertEquals(512L, weekTotal(days))
    }

    @Test
    fun `输出恒定 7 个日点按日升序且缺日补 0`() {
        val days = aggregateSettledDays(emptyList(), now)

        assertEquals(7, days.size)
        assertEquals(
            listOf(
                LocalDate.parse("2026-11-20"),
                LocalDate.parse("2026-11-21"),
                LocalDate.parse("2026-11-22"),
                LocalDate.parse("2026-11-23"),
                LocalDate.parse("2026-11-24"),
                LocalDate.parse("2026-11-25"),
                LocalDate.parse("2026-11-26"),
            ),
            days.map { it.date },
        )
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L), days.map { it.bytes })
        assertEquals(0L, weekTotal(days))
    }

    @Test
    fun `设备时区改变仍按北京自然日归桶`() {
        val points = listOf(
            // 北京 11-25 23:55，洛杉矶时间是 11-25 07:55、UTC 是 11-25 15:55，归属都不能变。
            TrafficPoint(at = Instant.parse("2026-11-25T15:55:00Z"), bytes = 300),
            // 北京 11-26 00:05。
            TrafficPoint(at = Instant.parse("2026-11-25T16:05:00Z"), bytes = 400),
        )
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val losAngeles = aggregateSettledDays(points, now)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val utc = aggregateSettledDays(points, now)

            assertEquals(losAngeles, utc)
            assertEquals(LocalDate.parse("2026-11-25"), losAngeles[5].date)
            assertEquals(300L, losAngeles[5].bytes)
            assertEquals(LocalDate.parse("2026-11-26"), losAngeles[6].date)
            assertEquals(400L, losAngeles[6].bytes)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `7 天合计等于 7 个日点之和`() {
        val days = aggregateSettledDays(
            listOf(
                TrafficPoint(at = Instant.parse("2026-11-19T16:30:00Z"), bytes = 64), // 北京 11-20 00:30
                TrafficPoint(at = Instant.parse("2026-11-22T08:00:00Z"), bytes = 128), // 北京 11-22 16:00
                TrafficPoint(at = Instant.parse("2026-11-26T15:00:00Z"), bytes = 256), // 北京 11-26 23:00
            ),
            now,
        )

        assertEquals(448L, weekTotal(days))
    }
}
