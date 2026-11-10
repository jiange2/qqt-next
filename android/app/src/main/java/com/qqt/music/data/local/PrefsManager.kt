package com.qqt.music.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qqt.music.data.api.model.Song
import java.util.UUID

object PrefsManager {
    private const val PREF_NAME = "qqt_music_prefs"
    private const val KEY_DOWNLOADED = "downloaded_songs"
    private const val KEY_FAVOURITE_IDS = "favourite_song_ids"
    private const val KEY_LOCAL_PLAYLISTS = "local_playlists"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_RATED_SONGS = "rated_songs"

    // 「最近播放」在本地音乐改造中整体下线（ADR 0019）：旧版遗留的 ID 列表由 init 一次性清除
    private const val KEY_LEGACY_RECENT_IDS = "recent_song_ids"
    private const val KEY_LEGACY_RECENT_PURGED = "legacy_recent_ids_purged"

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        purgeLegacyRecentIds()
    }

    /** 清除已下线功能的残留键：升级后首次启动写标记，之后不再重复写盘 */
    private fun purgeLegacyRecentIds() {
        if (prefs.getBoolean(KEY_LEGACY_RECENT_PURGED, false)) return
        prefs.edit()
            .remove(KEY_LEGACY_RECENT_IDS)
            .putBoolean(KEY_LEGACY_RECENT_PURGED, true)
            .apply()
    }

    // --- Favourite songs ---
    /** 收藏 ID 列表，最新收藏在最前；不设上限、不自动驱逐（收藏是用户显式拥有，ADR 0005） */
    fun getFavouriteIds(): List<String> {
        val json = prefs.getString(KEY_FAVOURITE_IDS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** 整表写入收藏列表（事实来源是 FavoriteStore，这里只负责持久化快照） */
    fun setFavouriteIds(ids: List<String>) {
        prefs.edit().putString(KEY_FAVOURITE_IDS, gson.toJson(ids)).apply()
    }

    // --- Local playlists ---
    /** 本地歌单快照（事实来源是 LocalPlaylistStore，这里只负责持久化） */
    fun getLocalPlaylists(): List<LocalPlaylist> {
        val json = prefs.getString(KEY_LOCAL_PLAYLISTS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<LocalPlaylist>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun setLocalPlaylists(playlists: List<LocalPlaylist>) {
        prefs.edit().putString(KEY_LOCAL_PLAYLISTS, gson.toJson(playlists)).apply()
    }

    // --- Downloaded songs ---
    fun getDownloadedSongs(): List<Song> {
        val json = prefs.getString(KEY_DOWNLOADED, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Song>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** 整表写入已下载列表（事实来源是 DownloadManager，这里只负责持久化快照） */
    fun setDownloadedSongs(songs: List<Song>) {
        prefs.edit().putString(KEY_DOWNLOADED, gson.toJson(songs)).apply()
    }

    // --- Device ID ---
    /** 设备 ID：首次需要时生成 UUID 并落盘（评分归属身份，ADR 0007）；须在 init 之后调用 */
    fun getOrCreateDeviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    // --- Rated songs ---
    /** 本机已评记录：歌曲 ID → 星级（每人每首仅一次、不可改分，ADR 0007） */
    fun getRatedSongs(): Map<String, Int> {
        val json = prefs.getString(KEY_RATED_SONGS, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    /** 整表写入已评记录（事实来源是 RatingStore，这里只负责持久化快照） */
    fun setRatedSongs(rated: Map<String, Int>) {
        prefs.edit().putString(KEY_RATED_SONGS, gson.toJson(rated)).apply()
    }
}
