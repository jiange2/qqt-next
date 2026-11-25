package com.cdndash.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 本机生物识别可用性：决定设置页是否展示开关（spec「设备不支持时隐藏并说明」）。
 */
enum class BiometricAvailability(val explanation: String?) {
    AVAILABLE(null),
    NO_HARDWARE("本机没有指纹或面容识别硬件，因此不提供生物识别解锁。"),
    NOT_ENROLLED("系统里还没有录入指纹或面容；录入后回到这里即可开启。"),
    HARDWARE_UNAVAILABLE("指纹或面容传感器当前不可用（可能被系统停用），稍后可再试。"),
    SECURITY_UPDATE_REQUIRED("系统需要先安装安全更新才能使用生物识别。"),
    UNKNOWN("无法确认本机生物识别状态，暂时不提供该开关。"),
    ;

    /** 只有真正能验证的设备才展示开关，避免开了也验不了的死配置。 */
    val canShowSwitch: Boolean get() = this == AVAILABLE
}

/** 把 [BiometricManager.canAuthenticate] 的返回值映射为可用性。 */
fun biometricAvailabilityOf(canAuthenticateResult: Int): BiometricAvailability =
    when (canAuthenticateResult) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            BiometricAvailability.SECURITY_UPDATE_REQUIRED
        else -> BiometricAvailability.UNKNOWN
    }

/** 一次系统身份验证的结果。 */
sealed interface BiometricResult {
    data object Succeeded : BiometricResult
    data class Failed(val error: LockError) : BiometricResult
}

/**
 * 系统 BiometricPrompt 的薄封装：只管「问一次」，不做门禁状态决策。
 *
 * 宿主必须是 [FragmentActivity]（androidx.biometric 的限制），[MainActivity] 已按此调整。
 * 26–29 没有「生物识别或设备凭据」的组合式鉴权器，用旧的 setDeviceCredentialAllowed
 * 打开 PIN/图案回退；30+ 用 setAllowedAuthenticators。两条路都允许回退到设备凭据。
 */
class BiometricUnlock(private val activity: FragmentActivity) {

    /** 设置页与门禁共用的可用性判断：只认「硬件在且已录入」。 */
    fun availability(): BiometricAvailability = biometricAvailabilityOf(
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK),
    )

    /**
     * 拉起系统验证。回调只会来一次；任何异常（宿主已销毁、系统弹窗不可用等）都归为
     * 「机制不可用」，由 [lockOutcomeFor] 决定临时放行，绝不让门禁卡住。
     */
    fun authenticate(onResult: (BiometricResult) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Succeeded)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(BiometricResult.Failed(lockErrorOf(errorCode)))
            }

            // onAuthenticationFailed / onAuthenticationHelp：系统仍停在验证界面，不打断使用者。
        }
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(promptInfo())
        } catch (error: Exception) {
            onResult(BiometricResult.Failed(LockError.MECHANISM_UNAVAILABLE))
        }
    }

    @Suppress("DEPRECATION") // setAllowedAuthenticators 要 API 30+，26–29 只能用旧 API 开凭据回退
    private fun promptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁 CDN 看板")
            .setSubtitle("验证指纹或面容后可查看账号流量")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build()
        } else {
            builder.setDeviceCredentialAllowed(true).build()
        }
    }
}

/** 设置页里判定是否展示开关；Activity 不是 FragmentActivity 时按「不可用」处理。 */
@Composable
fun rememberBiometricAvailability(): BiometricAvailability {
    val context = LocalContext.current
    return remember(context) {
        context.findFragmentActivity()
            ?.let { BiometricUnlock(it).availability() }
            ?: BiometricAvailability.UNKNOWN
    }
}

/** 从 Compose 的 LocalContext（可能被主题包装过）找回宿主 Activity。 */
fun Context.findFragmentActivity(): FragmentActivity? = when {
    this is FragmentActivity -> this
    this is ContextWrapper && baseContext !== this -> baseContext.findFragmentActivity()
    else -> null
}
