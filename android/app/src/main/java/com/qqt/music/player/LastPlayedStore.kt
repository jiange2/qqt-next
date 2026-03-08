package com.qqt.music.player

import android.content.Context

/**
 * 最近播放的进度和位置持久化
 *
 * 在用户停止播放或 App 退出时保存，App 启动时恢复。
 */
data class LastPlayed(
    val albumId: Int = -1,           // 正在播放的专辑 ID
    val trackIndex: Int = 0,         // 队列中的曲目索引
    val positionMs: Long = 0L        // 播放进度（毫秒）
)

object LastPlayedStore {
    private const val PREFS_NAME = "last_played"
    private const val KEY_ALBUM_ID = "album_id"
    private const val KEY_TRACK_INDEX = "track_index"
    private const val KEY_POSITION_MS = "position_ms"

    /**
     * 保存当前播放进度
     */
    fun save(context: Context, albumId: Int, trackIndex: Int, positionMs: Long = 0L) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_ALBUM_ID, albumId)
            putInt(KEY_TRACK_INDEX, trackIndex)
            putLong(KEY_POSITION_MS, positionMs)
            apply()
        }
    }

    /**
     * 加载上次保存的播放进度
     *
     * 如果没有保存过，返回默认值（albumId = -1）
     */
    fun load(context: Context): LastPlayed {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LastPlayed(
            albumId = prefs.getInt(KEY_ALBUM_ID, -1),
            trackIndex = prefs.getInt(KEY_TRACK_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L)
        )
    }

    /**
     * 清除保存的进度
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
