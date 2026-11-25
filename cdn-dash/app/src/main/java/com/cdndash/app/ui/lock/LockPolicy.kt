package com.cdndash.app.ui.lock

import androidx.biometric.BiometricPrompt

/** 一次验证失败的类别；把 [BiometricPrompt] 的十余个错误码归并成三种处置。 */
enum class LockError(val reason: String) {
    /** 使用者主动取消（系统弹窗的取消按钮、返回键、系统抢占焦点）。 */
    USER_CANCEL("已取消验证。"),

    /** 尝试次数过多被系统锁定，短时间内无法再验证。 */
    LOCKED_OUT("生物识别尝试次数过多，系统已暂时锁定。"),

    /** 本机既没有可用的生物识别，也没有设置锁屏密码，回退路径不存在。 */
    NO_DEVICE_CREDENTIAL("本机没有可用的生物识别，也没有设置锁屏密码。"),

    /** 硬件缺失、传感器异常、超时、厂商错误等：验证机制本身不可用。 */
    MECHANISM_UNAVAILABLE("指纹或面容验证当前不可用。"),
}

/** 验证失败后门禁的走向。 */
enum class LockOutcome {
    /** 保持门禁，只给「重试」（仍不入内）。 */
    RETRY,

    /** 明确提示后临时放行。 */
    BYPASS,
}

/**
 * 取舍：生物识别只是额外一层阻挡（spec「生物识别开关」），所以用户主动放弃时留在门禁内
 * 可重试；而任何「验证机制本身不可用」的情况一律临时放行 —— 宁可少挡一次，也不能把
 * 使用者关在自己的数据之外（票据 09「生物识别异常不导致应用无法进入」）。
 */
fun lockOutcomeFor(error: LockError): LockOutcome = when (error) {
    LockError.USER_CANCEL -> LockOutcome.RETRY
    LockError.LOCKED_OUT,
    LockError.NO_DEVICE_CREDENTIAL,
    LockError.MECHANISM_UNAVAILABLE,
    -> LockOutcome.BYPASS
}

/**
 * 把 BiometricPrompt 的错误码归并成 [LockError]。
 * 未列出的码（ERROR_NO_BIOMETRICS、ERROR_HW_NOT_PRESENT、ERROR_HW_UNAVAILABLE、
 * ERROR_UNABLE_TO_PROCESS、ERROR_TIMEOUT、ERROR_SECURITY_UPDATE_REQUIRED、ERROR_VENDOR 等）
 * 都归为「机制不可用」，走临时放行。
 */
fun lockErrorOf(biometricErrorCode: Int): LockError = when (biometricErrorCode) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> LockError.USER_CANCEL

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> LockError.LOCKED_OUT

    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> LockError.NO_DEVICE_CREDENTIAL

    else -> LockError.MECHANISM_UNAVAILABLE
}
