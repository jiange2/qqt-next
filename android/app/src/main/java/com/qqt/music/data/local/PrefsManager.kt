package com.qqt.music.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qqt.music.data.api.model.Song

object PrefsManager {
    private const val PREF_NAME = "qqt_music_prefs"
    private const val KEY_RECENT_IDS = "recent_song_ids"
    private const val KEY_DOWNLOADED = "downloaded_songs"
    private const val MAX_RECENT = 50

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- Recent songs ---
    fun getRecentIds(): List<String> {
        val json = prefs.getString(KEY_RECENT_IDS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun addRecentId(songId: String) {
        val ids = getRecentIds().toMutableList()
        ids.remove(songId)
        ids.add(0, songId)
        val trimmed = ids.take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT_IDS, gson.toJson(trimmed)).apply()
    }

    fun getRecentIdsString(): String = getRecentIds().joinToString(",")

    // --- Downloaded songs ---
    fun getDownloadedSongs(): List<Song> {
        val json = prefs.getString(KEY_DOWNLOADED, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Song>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveDownloadedSong(song: Song) {
        val songs = getDownloadedSongs().toMutableList()
        if (songs.none { it.id == song.id }) {
            songs.add(0, song)
            prefs.edit().putString(KEY_DOWNLOADED, gson.toJson(songs)).apply()
        }
    }

    fun removeDownloadedSong(songId: String) {
        val songs = getDownloadedSongs().toMutableList()
        songs.removeAll { it.id == songId }
        prefs.edit().putString(KEY_DOWNLOADED, gson.toJson(songs)).apply()
    }
}
