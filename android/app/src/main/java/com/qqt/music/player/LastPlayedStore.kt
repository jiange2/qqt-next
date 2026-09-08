package com.qqt.music.player

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qqt.music.data.api.model.Song

/**
 * 播放进度的持久化形态：整个播放队列的歌曲快照 + 队列索引 + 播放位置 + 队列来源（ADR 0010、ADR 0015）
 *
 * 有当前歌即保存（切歌/暂停立即存、5 秒轮询兜底），App 冷启动时据此静默恢复，不回源后端。
 * 旧「category_id + track_index + position_ms」三键格式废弃不迁移（恢复端从未上线，无存量数据）。
 */
data class LastPlayedSnapshot(
    val queue: List<Song>,
    val trackIndex: Int,             // 队列中的曲目索引
    val positionMs: Long,            // 播放进度（毫秒）
    val sourceDescriptor: String? = null  // 队列来源（ADR 0015）；存量快照无该字段，恢复按 default 落位
)

object LastPlayedStore {
    private const val TAG = "LastPlayedStore"
    private const val PREFS_NAME = "last_played"
    private const val KEY_QUEUE_JSON = "queue_json"
    private const val KEY_TRACK_INDEX = "track_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_SOURCE_DESCRIPTOR = "source_descriptor"

    private val gson = Gson()

    /**
     * 保存队列快照
     */
    fun save(context: Context, queue: List<Song>, trackIndex: Int, positionMs: Long, sourceDescriptor: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_QUEUE_JSON, gson.toJson(queue))
            putInt(KEY_TRACK_INDEX, trackIndex)
            putLong(KEY_POSITION_MS, positionMs)
            putString(KEY_SOURCE_DESCRIPTOR, sourceDescriptor)
            apply()
        }
    }

    /**
     * 加载上次保存的队列快照
     *
     * 没有保存过或快照损坏时返回 null，由调用方完全静默地放弃恢复。
     */
    fun load(context: Context): LastPlayedSnapshot? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_QUEUE_JSON, null) ?: return null
        return try {
            val type = object : TypeToken<List<Song>>() {}.type
            val queue: List<Song> = gson.fromJson(json, type)
            if (queue.isNullOrEmpty()) null
            else LastPlayedSnapshot(
                queue = queue,
                trackIndex = prefs.getInt(KEY_TRACK_INDEX, 0),
                positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                sourceDescriptor = prefs.getString(KEY_SOURCE_DESCRIPTOR, null)
            )
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ failed to parse queue snapshot, skip restore", e)
            null
        }
    }

    /**
     * 清除保存的快照
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
