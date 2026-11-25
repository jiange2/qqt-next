package com.cdndash.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字节与金额格式化的行为测试（票据01，spec「Testing Decisions」）。
 * 期望值全部是独立字面量：B/KB/MB/GB/TB 的 1024 进制边界与两位小数四舍五入。
 */
class FormatTest {

    @Test
    fun `字节按 1024 进制在 B 与 KB 边界切换`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("1 B", formatBytes(1L))
        assertEquals("1023 B", formatBytes(1023L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 KB", formatBytes(1536L))
    }

    @Test
    fun `字节逐级换算到 MB GB TB`() {
        assertEquals("1.0 MB", formatBytes(1_048_576L))
        assertEquals("1.0 GB", formatBytes(1_073_741_824L))
        assertEquals("1.0 TB", formatBytes(1_099_511_627_776L))
        assertEquals("2.5 TB", formatBytes(2_748_779_069_440L))
    }

    @Test
    fun `金额保留两位小数并四舍五入`() {
        assertEquals("0.00", formatMoney(0.0))
        assertEquals("12.30", formatMoney(12.3))
        assertEquals("1234.57", formatMoney(1234.567))
        assertEquals("0.01", formatMoney(0.005))
    }
}
