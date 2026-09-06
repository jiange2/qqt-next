package com.qqt.music.ui.navigation

/**
 * 「我的歌单」→ 歌单详情的跳转暂存（本地歌单，ADR 0006）。
 * 此处仅暂存歌单 id 与名称（用于详情页取数与顶栏标题），
 * 路由上的 {pid} 仅标识返回栈条目。
 */
object MyListNav {
    var playlistId: String? = null
    var playlistName: String? = null
}
