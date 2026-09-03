package com.qqt.music.update

import android.content.Context
import android.util.Log
import com.qqt.music.data.api.model.AppUpdateInfo
import com.qqt.music.data.repository.MusicRepository

/**
 * App 更新检查：调用 app_details 拿后台配置的更新信息，
 * 与本机 versionName 比对后决定是否弹更新弹窗。
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"

    /** 一次检查的结论：更新信息 + 是否强制更新（后台未允许取消时为强更） */
    data class Result(val info: AppUpdateInfo, val forceUpdate: Boolean)

    /**
     * 检查更新；后台未开更新开关、已是最新版本、或请求失败时返回 null（不打扰用户）。
     */
    suspend fun check(context: Context): Result? {
        val info = MusicRepository.getAppDetails() ?: return null
        if (!info.isUpdateEnabled) return null
        if (info.redirectUrl.isBlank()) return null
        val current = currentVersionName(context)
        if (!isNewerVersion(current, info.newVersion)) return null
        Log.d(TAG, "Update available: current=$current, latest=${info.newVersion}, force=${!info.isCancelable}")
        return Result(info, forceUpdate = !info.isCancelable)
    }

    /** 本机 versionName（通过 PackageManager 读取，避免依赖 BuildConfig 开关） */
    fun currentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 按段比较版本号：latest 比 current 新时返回 true。
     * latest 是 Double（后台 app_new_version 字段类型），转成 "1.1" 形式再与 "1.0.3" 之类逐段比较；
     * Double 无法区分 1.1 与 1.10，是后端字段类型的固有限制，后台发版时避免用两位修订号即可。
     */
    fun isNewerVersion(current: String, latest: Double): Boolean {
        return isNewerVersion(current, latest.toString())
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val a = parseSegments(current)
        val b = parseSegments(latest)
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }

    private fun parseSegments(version: String): List<Int> =
        version.split('.').map { it.trim().toIntOrNull() ?: 0 }
}
