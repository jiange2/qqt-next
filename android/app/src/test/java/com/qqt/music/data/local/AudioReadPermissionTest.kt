package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 音频读取权限名按系统版本的契约（spec「扫描来源与权限」，票据02）：
 * Android 13（API 33）起申请音乐粒度的 `READ_MEDIA_AUDIO`；Android 12（API 32）及以下申请
 * `READ_EXTERNAL_STORAGE`——清单侧同版本约束（后者标 `maxSdkVersion="32"`）。
 * 断言用字面量而不是 `Manifest.permission` 常量：契约本身就是「请求的是哪一个权限名」，
 * 字面量是独立于实现的事实源（常量与实现同源会变成恒真断言）。
 */
class AudioReadPermissionTest {

    @Test
    fun android13AndAboveRequestsMusicGranularPermission() {
        assertEquals("android.permission.READ_MEDIA_AUDIO", audioReadPermission(33))
        assertEquals("android.permission.READ_MEDIA_AUDIO", audioReadPermission(34))
        assertEquals("android.permission.READ_MEDIA_AUDIO", audioReadPermission(35))
    }

    @Test
    fun android12AndBelowRequestsLegacyStoragePermission() {
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", audioReadPermission(32))
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", audioReadPermission(29))
        // 运行时权限从 API 23 起才有；低版本清单声明即授予，分支上仍按旧权限名返回
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", audioReadPermission(23))
    }
}
