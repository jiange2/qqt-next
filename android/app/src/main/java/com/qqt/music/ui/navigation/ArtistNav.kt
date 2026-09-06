package com.qqt.music.ui.navigation

import com.qqt.music.data.api.model.Artist

/**
 * 搜索结果艺术家行 -> 艺术家歌曲页的数据交接。
 *
 * 歌曲列表由目标页通过 artist_name_songs 接口按艺术家名分页加载，
 * 此处仅暂存艺术家本身（用于顶栏标题与请求参数）。
 */
object ArtistNav {
    var artist: Artist? = null
}
