package com.cdndash.app.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 门禁的失败处置策略（票据 09）：可 JVM 验证的只有这段纯函数 ——「用户取消不入内、
 * 验证机制不可用不锁死」。BiometricPrompt 的交互本身是设备级行为，列人工验收。
 */
class LockPolicyTest {

    @Test
    fun `用户取消保持门禁只给重试`() {
        assertEquals(LockOutcome.RETRY, lockOutcomeFor(LockError.USER_CANCEL))
    }

    @Test
    fun `验证机制不可用时临时放行`() {
        listOf(
            LockError.LOCKED_OUT,
            LockError.NO_DEVICE_CREDENTIAL,
            LockError.MECHANISM_UNAVAILABLE,
        ).forEach { error ->
            assertEquals(LockOutcome.BYPASS, lockOutcomeFor(error))
        }
    }
}
