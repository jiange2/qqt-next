package com.cdndash.app.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 花费纯函数测试（票据 08）：12 个月窗口、剔除当月、预付费标注规则、24 小时新鲜度与合计。
 * 期望值全部是独立字面量，不调用被测函数复算。
 */
class BillRulesTest {

    @Test
    fun `最近 12 个月为北京时间已出账完整月且不含当月`() {
        // 北京时间 2026-11-26 10:30。
        val now = Instant.parse("2026-11-26T02:30:00Z")

        val months = recentBillingMonths(now)

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 11), months.first())
        assertEquals(YearMonth.of(2026, 10), months.last())
        assertFalse(months.contains(YearMonth.of(2026, 11)))
    }

    @Test
    fun `北京时间月初边界仍按北京月份剔除当月`() {
        // 2026-10-31T16:30Z = 北京时间 2026-11-01 00:30，已进入 11 月。
        val justAfterBeijingMidnight = Instant.parse("2026-10-31T16:30:00Z")
        assertEquals(YearMonth.of(2026, 10), recentBillingMonths(justAfterBeijingMidnight).last())

        // 2026-10-31T15:30Z = 北京时间 2026-10-31 23:30，仍是 10 月。
        val beforeBeijingMidnight = Instant.parse("2026-10-31T15:30:00Z")
        assertEquals(YearMonth.of(2026, 9), recentBillingMonths(beforeBeijingMidnight).last())
    }

    @Test
    fun `订阅型 CDN 明细才构成预付费证据`() {
        assertTrue(isPrepaidCdnBillLine("Subscription", "cdn"))
        assertTrue(isPrepaidCdnBillLine("Subscription", "cdnflowbag"))
        assertTrue(isPrepaidCdnBillLine("subscription", " CDN "))

        assertFalse(isPrepaidCdnBillLine("PayAsYouGo", "cdn"))
        assertFalse(isPrepaidCdnBillLine("Subscription", "oss"))
        // 全站加速（dcdn）不在看板口径内，不能误标。
        assertFalse(isPrepaidCdnBillLine("Subscription", "dcdn"))
    }

    @Test
    fun `明细字段缺失时不标预付费`() {
        assertFalse(isPrepaidCdnBillLine(null, "cdn"))
        assertFalse(isPrepaidCdnBillLine("Subscription", null))
        assertFalse(isPrepaidCdnBillLine(null, null))
        assertFalse(isPrepaidCdnBillLine("", ""))
    }

    @Test
    fun `24 小时内快照新鲜超时或缺失需重取`() {
        val nowMillis = 1_700_000_000_000L
        val hourMillis = 3_600_000L

        assertTrue(isBillCacheFresh(nowMillis - 23 * hourMillis, nowMillis))
        assertFalse(isBillCacheFresh(nowMillis - 24 * hourMillis, nowMillis))
        assertFalse(isBillCacheFresh(null, nowMillis))
        // 时钟回拨导致抓取时间在未来：按新鲜处理，避免反复请求。
        assertTrue(isBillCacheFresh(nowMillis + hourMillis, nowMillis))
    }

    @Test
    fun `账期窗口整体平移后快照不可用`() {
        val nowMillis = 1_700_000_000_000L
        val expected = recentBillingMonths(Instant.parse("2026-11-26T02:30:00Z"))
        val shifted = expected.drop(1) + YearMonth.of(2026, 11)

        assertTrue(isBillCacheUsable(expected, expected, nowMillis - 3_600_000L, nowMillis))
        assertFalse(isBillCacheUsable(shifted, expected, nowMillis - 3_600_000L, nowMillis))
        assertFalse(isBillCacheUsable(expected, expected, nowMillis - 25 * 3_600_000L, nowMillis))
    }

    @Test
    fun `全年合计用十进制相加并保留两位小数`() {
        val bills = listOf(
            bill("100.10"),
            bill("23.45"),
            bill("0.005"),
        )

        assertEquals(BigDecimal("123.555"), totalBillAmount(bills))
        assertEquals("123.56", formatMoney(totalBillAmount(bills)))
    }

    private fun bill(amount: String): MonthlyBill = MonthlyBill(
        month = YearMonth.of(2026, 1),
        pretaxAmount = BigDecimal(amount),
        cashAmount = BigDecimal.ZERO,
        voucherAmount = BigDecimal.ZERO,
    )
}
