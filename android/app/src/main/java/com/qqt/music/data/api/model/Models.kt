package com.qqt.music.data.api.model

import com.google.gson.annotations.SerializedName

data class Album(
    @SerializedName("aid") val id: String = "",
    @SerializedName("album_name") val name: String = "",
    @SerializedName("album_image") val image: String = "",
    @SerializedName("album_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
)

data class Category(
    @SerializedName("cid") val id: String = "",
    @SerializedName("category_name") val name: String = "",
    // 分类类型（书籍阅读域 ADR 0011）：1=音乐，2=书籍；App 据此分流点击去向
    @SerializedName("category_type") val categoryType: String = "1",
    @SerializedName("category_image") val image: String = "",
    @SerializedName("category_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
) {
    val isBook: Boolean get() = categoryType == "2"
}

/** 书籍（书籍阅读域 ADR 0011）：cat_books 行，形态对齐 Album */
data class Book(
    @SerializedName("book_id") val id: String = "",
    @SerializedName("book_name") val name: String = "",
    @SerializedName("book_author") val author: String = "",
    @SerializedName("book_cover") val cover: String = "",
    @SerializedName("book_cover_thumb") val coverThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
)

/** book_chapters 行：全书目录一次下发（服务端按 id ASC） */
data class BookChapter(
    @SerializedName("chapter_id") val id: String = "",
    @SerializedName("chapter_title") val title: String = "",
)

/** book_chapter 行：单章正文（外包数组） */
data class ChapterContent(
    @SerializedName("chapter_id") val id: String = "",
    @SerializedName("chapter_title") val title: String = "",
    @SerializedName("content") val content: String = "",
)

/** 艺术家实体：搜索结果之一，点击进艺术家歌曲页；与歌曲行副标题的歌手文本不同（那是非实体的字符串） */
data class Artist(
    @SerializedName("id") val id: String = "",
    @SerializedName("artist_name") val name: String = "",
    @SerializedName("artist_image") val image: String = "",
    @SerializedName("artist_image_thumb") val imageThumb: String = "",
)

/** song_search 组合搜索返回的三段结果：歌曲段按页返回（每页 10），专辑/艺术家段为 20 条截断不分页 */
data class SearchResults(
    @SerializedName("search_songs") val songs: List<Song> = emptyList(),
    @SerializedName("search_album") val albums: List<Album> = emptyList(),
    @SerializedName("search_artist") val artists: List<Artist> = emptyList(),
)

data class Banner(
    @SerializedName("bid") val id: String = "",
    @SerializedName("banner_title") val title: String = "",
    @SerializedName("banner_sort_info") val info: String = "",
    @SerializedName("banner_link") val link: String = "",
    @SerializedName("banner_image") val image: String = "",
    @SerializedName("banner_image_thumb") val imageThumb: String = "",
    @SerializedName("total_songs") val totalSongs: Int = 0,
    @SerializedName("songs_list") val songs: List<Song> = emptyList(),
)

data class HomeData(
    @SerializedName("home_banner") val banners: List<Banner> = emptyList(),
    @SerializedName("latest_album") val latestAlbums: List<Album> = emptyList(),
    @SerializedName("trending_songs") val trendingSongs: List<Song> = emptyList(),
    @SerializedName("recent_songs") val recentSongs: List<Song> = emptyList(),
)

/** app_details 返回的 App 信息与更新配置 */
data class AppUpdateInfo(
    @SerializedName("app_name") val appName: String = "",
    @SerializedName("app_version") val appVersion: String = "",
    @SerializedName("app_update_status") val updateStatus: String = "false",
    @SerializedName("app_new_version") val newVersion: Double = 0.0,
    @SerializedName("app_update_desc") val updateDesc: String = "",
    @SerializedName("app_redirect_url") val redirectUrl: String = "",
    @SerializedName("cancel_update_status") val cancelUpdateStatus: String = "false",
) {
    /** 后台是否已开启更新提醒 */
    val isUpdateEnabled: Boolean get() = updateStatus == "true"

    /** 弹窗是否允许用户取消（false = 强制更新） */
    val isCancelable: Boolean get() = cancelUpdateStatus == "true"
}
