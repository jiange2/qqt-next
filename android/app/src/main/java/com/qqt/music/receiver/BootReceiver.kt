package com.qqt.music.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.qqt.music.service.KeepAliveService

/**
 * 设备启动接收器
 *
 * 监听 BOOT_COMPLETED 和 QUICKBOOT_POWERON 广播，设备重启后自动启动 KeepAliveService。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val serviceIntent = Intent(context, KeepAliveService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.d(TAG, "✅ KeepAliveService started on boot")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start service on boot", e)
            }
        }
    }
}
