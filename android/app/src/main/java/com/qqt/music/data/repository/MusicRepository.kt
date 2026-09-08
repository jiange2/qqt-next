package com.qqt.music.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qqt.music.data.api.ApiClient
import com.qqt.music.data.api.model.*
import com.qqt.music.data.local.PrefsManager

/** 评分提交结果：成功 / 后端判定已评过（rate_already，每人每首仅一次）/ 网络或响应异常 */
enum class RatingOutcome { SUCCESS, ALREADY_RATED, FAILED }

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
            // 旧契约单行结果也包在 ONLINE_MP3 数组里（api.php array_push，backend-next 逐字复刻），取首元素解析
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            val row = if (mp3.isJsonArray) mp3.asJsonArray.firstOrNull() ?: return null else mp3
            gson.fromJson(row, AppUpdateInfo::class.java)
        } catch (e: Exception) { null }
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

    /** 分类书籍列表（书籍阅读域 ADR 0011）：形态对齐 getCategoryAlbums */
    suspend fun getCategoryBooks(catId: String, page: Int): Pair<List<Book>, Int> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "cat_books", "cat_id" to catId, "page" to page))
            val resp = service.callApi(data)
            val total = resp.get("total_records")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: -1
            parseArray<Book>(resp.get("ONLINE_MP3")) to total
        } catch (e: Exception) { emptyList<Book>() to -1 }
    }

    /** 全书章节目录：一次下发全部章（服务端按 id ASC）；返回 null 表示请求失败（区别于空书） */
    suspend fun getBookChapters(bookId: String): List<BookChapter>? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "book_chapters", "book_id" to bookId))
            val resp = service.callApi(data)
            parseArray<BookChapter>(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { null }
    }

    /** 单章正文：book_chapter 返回外包数组的单对象；不可见/失败返回 null（不可见时服务端返回空对象，id 为空串） */
    suspend fun getChapterContent(chapterId: String): ChapterContent? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "book_chapter", "chapter_id" to chapterId))
            val resp = service.callApi(data)
            val first = resp.getAsJsonArray("ONLINE_MP3")?.firstOrNull() ?: return null
            gson.fromJson(first, ChapterContent::class.java).takeIf { it.id.isNotBlank() }
        } catch (e: Exception) { null }
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

    suspend fun getAlbumSongs(albumId: String, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "album_songs", "album_id" to albumId, "user_id" to userId))
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

    /** get_recent_songs 每页条数（后端硬编码） */
    const val RECENT_PAGE_SIZE = 10

    /** 按 ID 串查歌曲详情（最近播放/收藏的换详情通道）；后端按歌曲 ID 降序分页，返回顺序与传入
     *  顺序无关，需调用方自行重排；返回 null 表示请求失败（区别于成功但无数据） */
    suspend fun getRecentSongs(songIds: String, page: Int, userId: Int = 0): List<Song>? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "get_recent_songs", "songs_ids" to songIds, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { null }
    }

    /** 单曲详情（song_info）：歌词字段（mp3_lrc_txt / mp3_lrc_url）只在详情接口下发，列表接口不携带；
     *  返回 null 表示请求失败（区别于成功但无数据）。后端此接口带「详情请求计一次播放」副作用（ADR 0014） */
    suspend fun getSongInfo(songId: String, userId: Int = 0): Song? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "song_info", "song_id" to songId, "user_id" to userId))
            val resp = service.callApi(data)
            val first = resp.getAsJsonArray("ONLINE_MP3")?.firstOrNull() ?: return null
            gson.fromJson(first, Song::class.java)
        } catch (e: Exception) { null }
    }

    /** 纯歌曲搜索（搜索页「查看更多歌曲」的全量分页通道）：按歌名模糊匹配，每页 10 条 */
    suspend fun searchSongs(query: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "song_search", "search_text" to query, "search_type" to "songs", "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            parseArray(resp.get("ONLINE_MP3"))
        } catch (e: Exception) { emptyList() }
    }

    /** 组合搜索（搜索页首屏）：不传 search_type 走后端组合分支，一次返回歌曲（分页）/
     *  专辑/艺术家（后两段各 20 条截断）三段；返回 null 表示请求失败 */
    suspend fun searchAll(query: String, page: Int = 1, userId: Int = 0): SearchResults? {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "song_search", "search_text" to query, "page" to page, "user_id" to userId))
            val resp = service.callApi(data)
            val mp3 = resp.get("ONLINE_MP3") ?: return null
            if (!mp3.isJsonObject) return null
            gson.fromJson(mp3, SearchResults::class.java)
        } catch (e: Exception) { null }
    }

    /** 艺术家歌曲列表（artist_name_songs 按名字精确匹配逗号分隔歌手字段），每页 10 条，id 倒序；
     *  重名艺术家会进同一结果页（旧契约如此） */
    suspend fun getArtistSongs(artistName: String, page: Int, userId: Int = 0): List<Song> {
        return try {
            val data = ApiClient.buildData(mapOf("method_name" to "artist_name_songs", "artist_name" to artistName, "page" to page, "user_id" to userId))
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

    /** 提交歌曲评分（song_rating）：归属设备 ID（ADR 0007），仅此请求携带；rate 为 1–5 */
    suspend fun submitRating(songId: String, rate: Int): RatingOutcome {
        return try {
            val data = ApiClient.buildData(
                mapOf(
                    "method_name" to "song_rating",
                    "post_id" to songId,
                    "user_id" to PrefsManager.getOrCreateDeviceId(),
                    "rate" to rate,
                ),
            )
            val resp = service.callApi(data)
            val success = resp.getAsJsonArray("ONLINE_MP3")
                ?.firstOrNull()?.asJsonObject?.get("success")?.asString
            when (success) {
                "1" -> RatingOutcome.SUCCESS
                "0" -> RatingOutcome.ALREADY_RATED
                else -> RatingOutcome.FAILED
            }
        } catch (e: Exception) { RatingOutcome.FAILED }
    }
}
