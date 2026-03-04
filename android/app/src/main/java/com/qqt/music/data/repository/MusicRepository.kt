package com.qqt.music.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qqt.music.data.api.ApiClient
import com.qqt.music.data.api.model.*

object MusicRepository {
    private val service = ApiClient.apiService
    private val gson = Gson()

    private inline fun <reified T> parseArray(json: com.google.gson.JsonElement?): List<T> {
        if (json == null || !json.isJsonArray) return emptyList()
        return try {
            val type = TypeToken.getParameterized(List::class.java, T::class.java).type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getHome(userId: Int = 0): HomeData? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "home_new", "user_id" to userId))
            val resp = service.callApi(data)
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            if (mp3.isJsonObject) gson.fromJson(mp3, HomeData::class.java) else null
        } catch (e: Exception) { null }
    }

    suspend fun getArtists(page: Int): List<Artist> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "artist_list", "page" to page))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getAlbums(page: Int): List<Album> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "album_list", "page" to page))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPlaylists(page: Int): List<Playlist> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "playlist", "page" to page))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCategories(page: Int): List<Category> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "cat_list", "page" to page))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLatestSongs(page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "latest", "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getAllSongs(page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "all_songs", "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCategorySongs(catId: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "cat_songs", "cat_id" to catId, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getAlbumSongs(albumId: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "album_songs", "album_id" to albumId, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPlaylistSongs(playlistId: String, page: Int, userId: Int = 0): List<Playlist> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "playlist_songs", "playlist_id" to playlistId, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getFavorites(userId: Int, page: Int): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "get_favourite_post", "user_id" to userId, "type" to "song", "page" to page))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRecentSongs(songIds: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "get_recent_songs", "songs_ids" to songIds, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchSongs(query: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "song_search", "search_text" to query, "search_type" to "songs", "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    suspend fun toggleFavourite(songId: String, userId: Int): Boolean {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "favourite_post", "post_id" to songId, "user_id" to userId, "type" to "song"))
            val resp = service.callApi(data)
            val arr = resp.getAsJsonArray("ONLINE_MP3")
            arr?.get(0)?.asJsonObject?.get("success")?.asString == "1"
        } catch (e: Exception) { false }
    }
}
