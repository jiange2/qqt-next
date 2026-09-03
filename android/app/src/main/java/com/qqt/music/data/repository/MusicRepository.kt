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
            // 横幅/专辑/歌手用 home 接口（home_new 不返回横幅挂接的歌曲详情，不可用）
            val data = ApiClient.buildData(mapOf("method_name" to "home", "user_id" to userId))
            val resp = service.callApi(data)
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            val home = if (mp3.isJsonObject) gson.fromJson(mp3, HomeData::class.java) else null
            if (home == null) return null

            // home 的 trending_songs 在服务端有重复缺陷（同一首歌多条周播放记录霸榜），
            // 改从 home_new 取近一个月无重复的热门榜（与老版 app 表现一致）；失败时兜底去重
            val trending = getHomeNewTrending(userId)
                ?: home.trendingSongs.distinctBy { it.id }
            home.copy(trendingSongs = trending)
        } catch (e: Exception) { null }
    }

    /** 取 home_new 接口的热门歌曲；请求失败或列表为空时返回 null，由调用方兜底 */
    private suspend fun getHomeNewTrending(userId: Int): List<Song>? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "home_new", "user_id" to userId))
            val resp = service.callApi(data)
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            if (!mp3.isJsonObject) return null
            gson.fromJson(mp3, HomeData::class.java).trendingSongs.takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    /** 取 app_details 接口的 App 信息与更新配置；请求失败返回 null */
    suspend fun getAppDetails(): AppUpdateInfo? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "app_details"))
            val resp = service.callApi(data)
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            if (!mp3.isJsonObject) return null
            gson.fromJson(mp3, AppUpdateInfo::class.java)
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

    /** 分类专辑列表（backend-next ADR 0009）：分类下放专辑而非歌曲，行结构与 album_list 一致；
     *  返回 (专辑列表, total_records)，total 解析失败时为 -1 */
    suspend fun getCategoryAlbums(catId: String, page: Int): Pair<List<Album>, Int> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "cat_albums", "cat_id" to catId, "page" to page))
            val resp = service.callApi(data)
            val total = resp.get("total_records")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: -1
            parseArray<Album>(resp.get("ONLINE_MP3")) to total
        } catch (e: Exception) { emptyList<Album>() to -1 }
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
