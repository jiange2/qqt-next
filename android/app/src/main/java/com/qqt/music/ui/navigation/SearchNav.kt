package com.qqt.music.ui.navigation

/**
 * 搜索页 -> 纯歌曲搜索结果页的数据交接。
 *
 * 结果页经 searchSongs 接口按关键词分页加载，
 * 此处仅暂存关键词（用于请求参数与顶栏标题）。
 */
object SearchNav {
    var query: String? = null
}
