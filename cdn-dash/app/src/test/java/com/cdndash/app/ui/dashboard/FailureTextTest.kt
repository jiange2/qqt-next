package com.cdndash.app.ui.dashboard

import com.cdndash.app.data.aliyun.ApiFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片错误文案的 JVM 测试（票据 07，User Story 34）：三类错误不合并成「出错了」，
 * 每类都要有一句可操作提示。只断言纯函数输出，不碰 Compose 布局。
 */
class FailureTextTest {

    @Test
    fun `三类错误的标签各不相同`() {
        assertEquals(
            listOf("凭证失效", "权限不足", "网络错误"),
            ApiFailureKind.entries.map { it.label() },
        )
    }

    @Test
    fun `每类错误都有各自的可操作提示`() {
        val hints = ApiFailureKind.entries.associateWith { it.actionHint() }

        hints.values.forEach { hint -> assertTrue(hint.isNotBlank()) }
        assertEquals(ApiFailureKind.entries.size, hints.values.toSet().size)
        assertTrue(hints.getValue(ApiFailureKind.CREDENTIAL_INVALID).contains("AccessKey"))
        assertTrue(hints.getValue(ApiFailureKind.PERMISSION_DENIED).contains("权限"))
        assertTrue(hints.getValue(ApiFailureKind.NETWORK).contains("网络"))
    }

    @Test
    fun `花费页只有权限类改称费用权限`() {
        assertEquals("费用权限未开启", ApiFailureKind.PERMISSION_DENIED.spendLabel())
        assertEquals(
            listOf("凭证失效", "网络错误"),
            listOf(
                ApiFailureKind.CREDENTIAL_INVALID.spendLabel(),
                ApiFailureKind.NETWORK.spendLabel(),
            ),
        )
    }

    @Test
    fun `花费页提示同样每类一句且带旧账单时说明数据时间`() {
        val hints = ApiFailureKind.entries.associateWith { it.spendActionHint(cachedAtHourMinute = null) }

        hints.values.forEach { hint -> assertTrue(hint.isNotBlank()) }
        assertEquals(ApiFailureKind.entries.size, hints.values.toSet().size)

        val withCache = ApiFailureKind.NETWORK.spendActionHint(cachedAtHourMinute = "10:30")
        assertTrue(withCache.contains("10:30"))
        assertTrue(withCache != ApiFailureKind.NETWORK.spendActionHint(cachedAtHourMinute = null))
    }
}
