package com.qqt.music

import android.Manifest
import android.content.ContentResolver
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
import androidx.core.content.IntentCompat
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
import com.qqt.music.ui.screens.localmusic.LocalMusicViewModel
import com.qqt.music.ui.theme.QQTMusicTheme
import com.qqt.music.update.AppUpdateChecker
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val playerViewModel: PlayerViewModel by viewModels()

    /**
     * 本地音乐导入的意图接收方（票据02）：与 AppNavigation 的 Compose `viewModel()` 命中同一
     * Activity ViewModelStore + 默认 factory + 默认 key = 同一实例（无 owner 覆写、无自定义 factory，
     * PlayerViewModel 的 by viewModels() 先例同机制），AppNavigation 零改动。
     */
    private val localMusicViewModel: LocalMusicViewModel by viewModels()

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

        // 3. 冷启动导入：来源 App「用其他应用打开 / 分享」音频选 qqt（在运行复用走 onNewIntent）。
        //    提示走 VM notice → 导航层 collector，StateFlow 保值，首帧组合后照样弹
        handleImportIntent(intent)
    }

    /** 在运行复用（launchMode=singleTask）：新意图走这里，setIntent 后与冷启动同一解析口径 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /**
     * 导入意图胶水（spec「意图胶水」，票据02）：只「解析 URI + 转交 ViewModel」，不含复制/命名/刷新
     * 逻辑（在接缝与 VM），也不在这里弹 Toast（提示走 VM notice → 导航层既有 collector，避免双弹）。
     * ACTION_VIEW 取 data；ACTION_SEND 取 EXTRA_STREAM（单文件）；SEND_MULTIPLE 本期不支持，静默忽略。
     * 防御性再校验（intent-filter 已限 audio 类 MIME）：非 content scheme 拒收；MIME 取 intent.type，
     * VIEW 无 type 时退回 contentResolver.getType(uri)，拿不到按拒收。
     */
    private fun handleImportIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) return
        val mime = intent.type ?: contentResolver.getType(uri)
        if (mime == null || !mime.startsWith("audio/", ignoreCase = true)) return
        localMusicViewModel.importAudio(uri)
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
