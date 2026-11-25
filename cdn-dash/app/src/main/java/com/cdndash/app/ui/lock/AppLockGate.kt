package com.cdndash.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.withResumed
import com.cdndash.app.data.AccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 门禁在应用内容出现之前的可见状态。 */
sealed interface LockPhase {
    /** 正在读开关；既不显示应用内容，也不显示门禁文案（避免开关关闭时闪一帧锁屏）。 */
    data object Reading : LockPhase

    /** 已拉起（或即将拉起）系统验证，[attempt] 每次递增以驱动重新弹窗。 */
    data class Locked(val attempt: Int) : LockPhase

    /** 使用者取消或验证未通过：保持门禁，只给「重试」。 */
    data class Retry(val reason: String) : LockPhase

    /** 验证机制本身不可用：明确提示后临时放行。 */
    data class Bypassed(val reason: String) : LockPhase

    /** 门禁通过（或开关本来就是关闭的）。 */
    data object Unlocked : LockPhase
}

/**
 * 门禁状态机：读开关 → 需要就验证一次 → 通过才放行。
 *
 * 状态放在 ViewModel 里而不是 remember，是为了「转屏不重复验、进程被杀后重新验」：
 * 转屏保留已解锁，冷启动必然重新走一次验证。
 */
class AppLockViewModel(
    accountStore: AccountStore,
    private val availability: BiometricAvailability,
) : ViewModel() {

    private var attempts = 0
    private val _phase = MutableStateFlow<LockPhase>(LockPhase.Reading)
    val phase: StateFlow<LockPhase> = _phase.asStateFlow()

    init {
        viewModelScope.launch {
            val enabled = accountStore.biometricEnabled.first()
            _phase.value = when {
                // 默认关闭：不开就不打扰，连可用性都不影响进入。
                !enabled -> LockPhase.Unlocked
                // 开了但本机现在验不了（硬件被移除、指纹被删）：提示后临时放行，不锁死。
                availability != BiometricAvailability.AVAILABLE -> LockPhase.Bypassed(
                    reason = availability.explanation ?: "本机无法完成生物识别验证。",
                )
                else -> nextAttempt()
            }
        }
    }

    /**
     * 系统验证回调。带 [attempt] 是为了丢弃过期回调：转屏或重新弹窗时，旧弹窗被系统取消
     * 也会回调一次，不能让它把新一轮验证的结果覆盖成「重试」。
     */
    fun onAuthenticationResult(attempt: Int, result: BiometricResult) {
        if (phase.value != LockPhase.Locked(attempt)) return
        _phase.value = when (result) {
            BiometricResult.Succeeded -> LockPhase.Unlocked
            is BiometricResult.Failed -> when (lockOutcomeFor(result.error)) {
                LockOutcome.RETRY -> LockPhase.Retry(result.error.reason)
                LockOutcome.BYPASS -> LockPhase.Bypassed(result.error.reason)
            }
        }
    }

    /** 「重试」：换一个新的 attempt 序号，让 Compose 重新拉起系统验证。 */
    fun requestAuthentication() {
        _phase.value = nextAttempt()
    }

    /** 使用者确认「临时放行」后进入应用。 */
    fun enterAnyway() {
        _phase.value = LockPhase.Unlocked
    }

    private fun nextAttempt(): LockPhase {
        attempts += 1
        return LockPhase.Locked(attempts)
    }

    companion object {
        fun factory(
            accountStore: AccountStore,
            availability: BiometricAvailability,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppLockViewModel(accountStore, availability) }
        }
    }
}

/**
 * 生物识别门禁（票据 09）：套在导航根 [com.cdndash.app.ui.navigation.CdnDashApp] 外层，
 * 开关开启时先验证一次再渲染应用内容；开关关闭时直接渲染。
 */
@Composable
fun AppLockGate(
    accountStore: AccountStore,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    if (activity == null) {
        // 宿主不是 FragmentActivity 时无法弹系统身份验证；按「不锁死」原则直接放行。
        content()
        return
    }

    val unlock = remember(activity) { BiometricUnlock(activity) }
    val availability = remember(unlock) { unlock.availability() }
    val viewModel: AppLockViewModel = viewModel(
        factory = AppLockViewModel.factory(accountStore, availability),
    )
    val phase by viewModel.phase.collectAsState()

    when (val current = phase) {
        LockPhase.Unlocked -> content()

        // 读偏好只需毫秒级；空白一帧好过闪出看板内容或闪出锁屏文案。
        LockPhase.Reading -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}

        is LockPhase.Locked -> {
            LaunchedEffect(current) {
                // 系统验证弹窗要求宿主已 RESUMED；冷启动首帧可能还停在 STARTED，等一下就绪再弹，
                // 免得 BiometricPrompt 在未就绪时直接报错。
                activity.lifecycle.withResumed { }
                unlock.authenticate { result ->
                    viewModel.onAuthenticationResult(current.attempt, result)
                }
            }
            LockGateScreen(
                title = "需要解锁",
                message = "请完成指纹或面容验证；验证失败可回退到锁屏密码。",
            )
        }

        is LockPhase.Retry -> LockGateScreen(
            title = "解锁未完成",
            message = current.reason,
            actionLabel = "重试",
            onAction = viewModel::requestAuthentication,
            hint = "生物识别只是额外一层阻挡；重试或改用锁屏密码即可进入。",
        )

        is LockPhase.Bypassed -> LockGateScreen(
            title = "无法验证，临时放行",
            message = current.reason,
            actionLabel = "进入应用",
            onAction = viewModel::enterAnyway,
            hint = "为不把你挡在自己的数据之外，本次先放行；可在系统设置里恢复指纹或面容后再打开。",
        )
    }
}

@Composable
private fun LockGateScreen(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    hint: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
            if (hint != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
