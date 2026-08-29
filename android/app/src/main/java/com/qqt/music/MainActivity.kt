package com.qqt.music

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.player.LastPlayedStore
import com.qqt.music.service.KeepAliveService
import com.qqt.music.player.MusicPlayerService
import com.qqt.music.ui.navigation.AppNavigation
import com.qqt.music.ui.theme.QQTMusicTheme
import com.qqt.music.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(applicationContext)

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
                AppNavigation(playerViewModel = playerViewModel)
            }
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
