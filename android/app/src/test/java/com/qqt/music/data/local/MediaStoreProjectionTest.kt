package com.qqt.music.data.local

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MediaStore 投影列版本口径（[mediaStoreProjection]，spec「Implementation Decisions → Further Notes」：
 * 列可用性按 API 级别分支——基础列全程可用，`DISPLAY_NAME` / `RELATIVE_PATH` 为 29+、
 * `ALBUM_ARTIST` 为 30+，低版本不进投影也不读）的行为测试。列名断言引用
 * `MediaStore.Audio.Media` 常量（编译期常量，JVM 运行时不需要 android.jar 的方法桩）。
 */
class MediaStoreProjectionTest {

    private val baseColumns = listOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
    )

    @Test
    fun `Android 9 及以下只投影基础列且保持声明顺序`() {
        assertEquals(baseColumns, mediaStoreProjection(sdkInt = 21))
        assertEquals(baseColumns, mediaStoreProjection(sdkInt = 28))
    }

    @Test
    fun `Android 10 起投影加 DISPLAY_NAME 与 RELATIVE_PATH`() {
        assertEquals(
            baseColumns + listOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH),
            mediaStoreProjection(sdkInt = 29),
        )
    }

    @Test
    fun `Android 11 起投影再加 ALBUM_ARTIST`() {
        assertEquals(
            baseColumns + listOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.ALBUM_ARTIST,
            ),
            mediaStoreProjection(sdkInt = 30),
        )
    }

    @Test
    fun `声明清单与投影同源——声明顺序即投影顺序`() {
        assertEquals(
            mediaStoreColumnDeclarations().map { it.first },
            mediaStoreProjection(sdkInt = 35),
        )
    }
}
