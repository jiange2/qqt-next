package com.qqt.music.data.api.model

import com.google.gson.annotations.SerializedName

data class Song(
    @SerializedName("id") val id: String = "",
    @SerializedName("mp3_title") val title: String = "",
    @SerializedName("mp3_url") val url: String = "",
    @SerializedName("mp3_thumbnail_b") val thumbnailBig: String = "",
    @SerializedName("mp3_thumbnail_s") val thumbnailSmall: String = "",
    @SerializedName("mp3_artist") val artist: String = "",
    @SerializedName("mp3_description") val description: String = "",
    @SerializedName("total_rate") val totalRate: String = "0",
    @SerializedName("rate_avg") val rateAvg: String = "0",
    @SerializedName("total_views") val totalViews: String = "0",
    @SerializedName("total_download") val totalDownload: String = "0",
    @SerializedName("is_favourite") val isFavourite: Boolean = false,
    @SerializedName("category_name") val categoryName: String = "",
    // 歌词字段可空：后端对无歌词的歌输出 JSON null（旧库空列），Gson 会绕过 Kotlin 默认值直接注入 null，
    // 非空声明反而导致访问时 NPE（消费方用 orEmpty() 归一）
    @SerializedName("mp3_lrc_txt") val lrcText: String? = null,
    @SerializedName("mp3_lrc_url") val lrcUrl: String? = null,
    @SerializedName("mp3_duration") val duration: String = "0",
    @SerializedName("total_songs") val totalSongs: String = "0",
    @SerializedName("total_records") val totalRecords: String = "0",
    @SerializedName("mp3_type") val mp3Type: String = "local",
    @SerializedName("cat_id") val catId: String = "",
) {
    fun totalCount(): Int =
        (totalSongs.toIntOrNull() ?: 0).coerceAtLeast(totalRecords.toIntOrNull() ?: 0)
}
