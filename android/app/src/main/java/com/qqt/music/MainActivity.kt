package com.qqt.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.LastPlayedStore
import com.qqt.music.player.PlayerSettingsManager
import com.qqt.music.service.KeepAliveService
import com.qqt.music.player.MusicPlayerService
import com.qqt.music.ui.components.AppUpdateDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(applicationContext)
        FavoriteStore.init()
        LocalPlaylistStore.init()
        RatingStore.init()
        DownloadManager.init(applicationContext)
        PlayerSettingsManager.init(applicationContext)

        // 1. 启动双层前台服务保活
        //    MusicPlayerService 中的 ExoPlayer 将被所有 UI 使用
        try {
            ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, MusicPlayerService::class.java))
            Log.d(TAG, "✅ KeepAliveService & MusicPlayerService started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start services", e)
        }

        // 2. 尝试恢复上次播放进度（可选）
        tryRestoreLastPlayed()

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
            }
        }

        // 3. 异步检查 App 更新（失败/已最新时静默跳过；仅本次创建时检查一次）
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

    /**
     * 尝试恢复上次播放的进度
     *
     * 如果有保存的播放记录，从 API 加载该分类的歌曲列表，并设置起始播放位置。
     */
    private fun tryRestoreLastPlayed() {
        val lastPlayed = LastPlayedStore.load(applicationContext)
        if (lastPlayed.categoryId != -1) {
            Log.d(TAG, "🔄 Detected last played category ID: ${lastPlayed.categoryId}")
            // TODO: 异步加载该分类的歌曲列表，然后调用 playerViewModel.restoreLastPlayed()
            // 由于目前没有 Repository 直接在 ViewModel 中注入，这里先保留注释
            // val tracks = /* load from API */ 
            // playerViewModel.restoreLastPlayed(tracks, lastPlayed.trackIndex, lastPlayed.positionMs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // MediaController 的释放交由 PlayerViewModel.onCleared() 处理
        Log.d(TAG, "🛑 MainActivity destroyed")
    }
}
