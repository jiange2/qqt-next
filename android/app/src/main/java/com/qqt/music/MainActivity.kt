package com.qqt.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.qqt.music.data.local.FavoriteStore
import com.qqt.music.data.local.LocalPlaylistStore
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.data.local.RatingStore
import com.qqt.music.data.local.ReadingProgressStore
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.PlayerSettingsManager
import com.qqt.music.ui.components.AppUpdateDialog
import com.qqt.music.ui.components.WhitelistGuidanceDialog
import com.qqt.music.ui.navigation.AppNavigation
import com.qqt.music.ui.theme.QQTMusicTheme
import com.qqt.music.update.AppUpdateChecker
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val playerViewModel: PlayerViewModel by viewModels()

    /** 非空时显示更新弹窗 */
    private var updateResult by mutableStateOf<AppUpdateChecker.Result?>(null)

    /** 通知权限申请结果：无论同意与否都继续，电池引导与通知权限相互独立 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { maybeShowBatteryGuidance() }

    /** 电池优化未白名单时显示引导弹窗（每次打开检查，不记忆"已询问"，ADR 0014） */
    private var showWhitelistDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(applicationContext)
        FavoriteStore.init()
        LocalPlaylistStore.init()
        RatingStore.init()
        ReadingProgressStore.init(applicationContext)
        DownloadManager.init(applicationContext)
        PlayerSettingsManager.init(applicationContext)

        // 1. app 打开即申请：先通知权限（API 33+，连续拒绝 2 次后系统自限不再弹窗），
        //    处理完接电池优化引导（ADR 0014）。MusicPlayerService 由 PlayerViewModel 的
        //    SessionToken 绑定自行拉起，无需显式 startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            maybeShowBatteryGuidance()
        }

        enableEdgeToEdge()
        setContent {
            QQTMusicTheme {
                // 下载失败等全局轻提示
                val toastContext = LocalContext.current
                LaunchedEffect(Unit) {
                    DownloadManager.errors.collect { message ->
                        Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show()
                    }
                }

                AppNavigation(playerViewModel = playerViewModel)

                updateResult?.let { result ->
                    AppUpdateDialog(
                        info = result.info,
                        forceUpdate = result.forceUpdate,
                        onUpdate = { openUpdateUrl(result.info.redirectUrl) },
                        onDismiss = { updateResult = null },
                    )
                }

                // 白名单引导：电池优化未放行时弹窗（ADR 0014）
                if (showWhitelistDialog) {
                    WhitelistGuidanceDialog(
                        onBatteryWhitelist = {
                            showWhitelistDialog = false
                            openBatteryWhitelist()
                        },
                        onDismiss = { showWhitelistDialog = false },
                    )
                }
            }
        }

        // 2. 异步检查 App 更新（失败/已最新时静默跳过；仅本次创建时检查一次）
        lifecycleScope.launch {
            updateResult = AppUpdateChecker.check(this@MainActivity)
        }
    }

    /** 跳转后台配置的下载地址（appRedirectUrl） */
    private fun openUpdateUrl(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open update url: $url", e)
        }
    }

    /** 未加入电池优化白名单时置位引导弹窗 */
    private fun maybeShowBatteryGuidance() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            showWhitelistDialog = true
        }
    }

    /** 电池优化白名单：直接请求加入，异常回退电池优化设置列表页 */
    private fun openBatteryWhitelist() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to open battery settings", e2)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // MediaController 的释放交由 PlayerViewModel.onCleared() 处理
        Log.d(TAG, "🛑 MainActivity destroyed")
    }
}
