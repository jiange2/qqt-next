package com.qqt.music.receiver

import android.content.ComponentName
import android.content.Intent
import android.os.Build

/**
 * 国产厂商自启动管理兼容层
 *
 * 某些国产 ROM（小米、华为、OPPO、VIVO）需要引导用户手动允许应用自启动。
 * 该工具类提供快速跳转到各厂商自启动管理界面的 Intent。
 */
object ManufacturerCompat {

    /**
     * 获取跳转到厂商自启动管理界面的 Intent
     *
     * @return Intent 如果识别到支持的厂商则返回对应 Intent，否则返回 null
     */
    fun getAutoStartIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("huawei") -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            manufacturer.contains("oppo") -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            }
            manufacturer.contains("vivo") -> Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            }
            else -> null
        }
    }

    /**
     * 检查设备是否为国产 ROM
     */
    fun isChineseROM(): Boolean {
        return getAutoStartIntent() != null
    }
}
