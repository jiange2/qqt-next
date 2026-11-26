package com.cdndash.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前台刷新节流的纯函数测试（spec「冷启动请求量」「纯函数」）。
 *
 * 只断言判定结果：从未成功过视为到期；距上次成功不足 1 小时不重取；满 1 小时（含边界）重取。
 * 期望值是手写字面量，不依赖运行时刻。
 */
class RefreshPolicyTest {

    private val lastFetchedAtMillis = 1_700_000_000_000L

    @Test
    fun `从未成功取过时视为到期`() {
        assertTrue(isWeekRefreshDue(lastFetchedAtMillis = null, nowMillis = lastFetchedAtMillis))
    }

    @Test
    fun `距上次成功不足一小时不重取`() {
        assertFalse(
            isWeekRefreshDue(
                lastFetchedAtMillis = lastFetchedAtMillis,
                nowMillis = lastFetchedAtMillis + 59 * 60_000L,
            ),
        )
    }

    @Test
    fun `距上次成功满一小时重取`() {
        assertTrue(
            isWeekRefreshDue(
                lastFetchedAtMillis = lastFetchedAtMillis,
                nowMillis = lastFetchedAtMillis + 60 * 60_000L,
            ),
        )
    }

    @Test
    fun `默认间隔为一小时且可注入`() {
        assertTrue(WEEK_REFRESH_INTERVAL_MILLIS == 3_600_000L)
        assertFalse(
            isWeekRefreshDue(
                lastFetchedAtMillis = lastFetchedAtMillis,
                nowMillis = lastFetchedAtMillis + 9 * 60_000L,
                intervalMillis = 10 * 60_000L,
            ),
        )
    }
}
