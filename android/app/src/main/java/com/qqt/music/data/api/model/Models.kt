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
    @SerializedName("category_image") val image: String = "",
    @SerializedName("category_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
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
