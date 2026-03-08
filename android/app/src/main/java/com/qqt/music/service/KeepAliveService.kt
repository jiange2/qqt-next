package com.qqt.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qqt.music.MainActivity
import com.qqt.music.R

/**
 * 轻量级前台守护服务（双层前台保活）
 *
 * 与 MusicPlayerService 共同维持进程优先级，确保国产 ROM 不会杀死进程。
 *
 * 只负责保活，不处理实际播放逻辑。通知栏优先级最低，对用户体验无影响。
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 9001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 不调用 stopSelf()，保持服务运行
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台守护",
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音乐播放器")
            .setContentText("守护中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }
}
