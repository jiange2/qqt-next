package com.qqt.music.player

/**
 * 播放队列来源描述符（ADR 0015）：点播入口打标用，键空间钉死为六键（CONTEXT.md「来源记忆」词条）。
 * 常驻来源（收藏 / 下载列表 / 本地音乐 / 默认）用常量；实体来源（专辑 / 本地歌单）按 ID 构造。
 */
object QueueSource {
    /** 首页/热门/最新/搜索/艺术家/Banner 等全部临时列表共享的默认来源，兼作无记忆来源的初值 */
    const val DEFAULT = "default"

    /** 收藏页 */
    const val FAVOURITES = "favourites"

    /** 下载列表页 */
    const val DOWNLOADS = "downloads"

    /** 本地音乐：本地音乐页与本地专辑页共用同一份来源记忆（ADR 0015 键空间扩展） */
    const val LOCAL = "local"

    /** 专辑歌曲页来源 */
    fun album(albumId: String) = "album:$albumId"

    /** 歌单详情页来源 */
    fun localPlaylist(playlistId: String) = "local_playlist:$playlistId"
}
