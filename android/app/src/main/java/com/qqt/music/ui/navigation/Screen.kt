package com.qqt.music.ui.navigation

sealed class Screen(val route: String, val title: String) {
    // Bottom nav tabs
    object Home : Screen("home", "首页")
    object Recent : Screen("recent", "最近播放")
    object Download : Screen("download", "我的下载")
    object Category : Screen("category", "音乐分类")
    object Latest : Screen("latest", "最新歌曲")

    // Drawer screens
    object Album : Screen("album", "音乐专辑")
    object MyList : Screen("mylist", "我的歌单")
    object Favorites : Screen("favorites", "歌曲收藏")
    object Settings : Screen("settings", "设置中心")
    
    // Full-screen player
    object Player : Screen("player", "播放器")

    // Banner songs detail page (title comes from the banner itself)
    object BannerSongs : Screen("banner_songs/{bid}", "歌曲")

    // Album songs detail page (title comes from the album itself)
    object AlbumSongs : Screen("album_songs/{aid}", "专辑")

    // Category albums detail page (title comes from the category itself, backend-next ADR 0009)
    object CategoryAlbums : Screen("category_albums/{cid}", "分类")

    // My playlist detail page (title comes from the playlist itself, local playlists ADR 0006)
    object MyPlaylistDetail : Screen("mylist_detail/{pid}", "歌单")

    companion object {
        val bottomNavRoutes = setOf("home", "recent", "download", "category", "latest")
        val drawerTopRoutes = setOf("album", "mylist", "favorites")

        fun titleOf(route: String?): String = when {
            route == null -> "倾轻听"
            route.startsWith("banner_songs") -> BannerNav.banner?.title ?: "歌曲"
            route.startsWith("album_songs") -> AlbumNav.album?.name ?: "专辑"
            route.startsWith("category_albums") -> CategoryNav.category?.name ?: "分类"
            route.startsWith("mylist_detail") -> MyListNav.playlistName ?: "歌单"
            else -> when (route) {
                "home" -> "首页"; "recent" -> "最近播放"; "download" -> "我的下载"
                "category" -> "音乐分类"; "latest" -> "最新歌曲"
                "album" -> "音乐专辑"; "mylist" -> "我的歌单"
                "favorites" -> "歌曲收藏"; "settings" -> "设置中心"; "player" -> "播放器"
                else -> "倾轻听"
            }
        }
    }
}
