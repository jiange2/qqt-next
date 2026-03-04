package com.qqt.music.ui.navigation

sealed class Screen(val route: String, val title: String) {
    // Bottom nav tabs
    object Home : Screen("home", "首页")
    object Recent : Screen("recent", "最近播放")
    object Download : Screen("download", "我的下载")
    object Category : Screen("category", "音乐分类")
    object Latest : Screen("latest", "最新歌曲")

    // Drawer screens
    object Artist : Screen("artist", "音乐歌手")
    object Album : Screen("album", "音乐专辑")
    object Playlist : Screen("playlist", "播放列表")
    object MyList : Screen("mylist", "我的列表")
    object Favorites : Screen("favorites", "歌曲收藏")
    object Settings : Screen("settings", "设置中心")

    companion object {
        val bottomNavRoutes = setOf("home", "recent", "download", "category", "latest")
        val drawerTopRoutes = setOf("artist", "album", "playlist", "mylist", "favorites")

        fun titleOf(route: String?): String = when (route) {
            "home" -> "首页"; "recent" -> "最近播放"; "download" -> "我的下载"
            "category" -> "音乐分类"; "latest" -> "最新歌曲"; "artist" -> "音乐歌手"
            "album" -> "音乐专辑"; "playlist" -> "播放列表"; "mylist" -> "我的列表"
            "favorites" -> "歌曲收藏"; "settings" -> "设置中心"
            else -> "倾轻听"
        }
    }
}
