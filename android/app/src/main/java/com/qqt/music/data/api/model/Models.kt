package com.qqt.music.data.api.model

import com.google.gson.annotations.SerializedName

data class Artist(
    @SerializedName("id") val id: String = "",
    @SerializedName("artist_name") val name: String = "",
    @SerializedName("artist_image") val image: String = "",
    @SerializedName("artist_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
)

data class Album(
    @SerializedName("aid") val id: String = "",
    @SerializedName("album_name") val name: String = "",
    @SerializedName("album_image") val image: String = "",
    @SerializedName("album_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
)

data class Playlist(
    @SerializedName("pid") val id: String = "",
    @SerializedName("playlist_name") val name: String = "",
    @SerializedName("playlist_image") val image: String = "",
    @SerializedName("playlist_image_thumb") val imageThumb: String = "",
    @SerializedName("total_records") val totalRecords: String = "0",
    @SerializedName("songs_list") val songsList: List<Song> = emptyList(),
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
    @SerializedName("latest_artist") val latestArtists: List<Artist> = emptyList(),
    @SerializedName("trending_songs") val trendingSongs: List<Song> = emptyList(),
    @SerializedName("recent_songs") val recentSongs: List<Song> = emptyList(),
)
