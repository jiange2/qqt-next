package com.cdndash.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶部汇总的纯函数测试（票据 06，spec「Testing Decisions」）。
 *
 * 期望值全部是独立字面量：停用排除、失败排除并计数、全部失败全零、无账号全零、
 * 部分失败时合计只含成功账号。失败账号仍带着上次成功的旧值，用来确认旧值也不并入合计。
 */
class TopSummaryTest {

    @Test
    fun `停用账号不计入合计也不计入缺失`() {
        val summary = summarizeTop(
            listOf(
                TopSummaryAccount(
                    enabled = true,
                    todayBytes = 1_000L,
                    weekBytes = 7_000L,
                    failed = false,
                ),
                TopSummaryAccount(
                    enabled = false,
                    todayBytes = 9_999L,
                    weekBytes = 99_999L,
                    failed = true,
                ),
            ),
        )

        assertEquals(1_000L, summary.todayTotalBytes)
        assertEquals(7_000L, summary.weekTotalBytes)
        assertEquals(0, summary.missingAccountCount)
    }

    @Test
    fun `失败账号从合计排除并计入缺失数`() {
        val summary = summarizeTop(
            listOf(
                TopSummaryAccount(true, todayBytes = 2_048L, weekBytes = 14_336L, failed = false),
                // 失败但留着上次成功的旧值：不得并入合计。
                TopSummaryAccount(true, todayBytes = 4_096L, weekBytes = 28_672L, failed = true),
                TopSummaryAccount(true, todayBytes = 8_192L, weekBytes = 57_344L, failed = true),
            ),
        )

        assertEquals(2_048L, summary.todayTotalBytes)
        assertEquals(14_336L, summary.weekTotalBytes)
        assertEquals(2, summary.missingAccountCount)
    }

    @Test
    fun `全部账号失败时两数为零且缺失数等于账号数`() {
        val summary = summarizeTop(
            listOf(
                TopSummaryAccount(true, todayBytes = 1_024L, weekBytes = 8_192L, failed = true),
                TopSummaryAccount(true, todayBytes = 2_048L, weekBytes = 16_384L, failed = true),
            ),
        )

        assertEquals(0L, summary.todayTotalBytes)
        assertEquals(0L, summary.weekTotalBytes)
        assertEquals(2, summary.missingAccountCount)
    }

    @Test
    fun `没有账号时全零且不崩溃`() {
        val summary = summarizeTop(emptyList())

        assertEquals(0L, summary.todayTotalBytes)
        assertEquals(0L, summary.weekTotalBytes)
        assertEquals(0, summary.missingAccountCount)
    }

    @Test
    fun `部分失败时合计只含成功账号`() {
        val summary = summarizeTop(
            listOf(
                TopSummaryAccount(true, todayBytes = 1_024L, weekBytes = 8_192L, failed = false),
                TopSummaryAccount(true, todayBytes = 9_999L, weekBytes = 88_888L, failed = true),
                TopSummaryAccount(true, todayBytes = 3_072L, weekBytes = 16_384L, failed = false),
            ),
        )

        assertEquals(4_096L, summary.todayTotalBytes)
        assertEquals(24_576L, summary.weekTotalBytes)
        assertEquals(1, summary.missingAccountCount)
    }

    @Test
    fun `尚未取到数据且未失败的账号按零累加不计缺失`() {
        val summary = summarizeTop(
            listOf(
                TopSummaryAccount(true, todayBytes = null, weekBytes = null, failed = false),
                TopSummaryAccount(true, todayBytes = 512L, weekBytes = 4_096L, failed = false),
            ),
        )

        assertEquals(512L, summary.todayTotalBytes)
        assertEquals(4_096L, summary.weekTotalBytes)
        assertEquals(0, summary.missingAccountCount)
    }
}
