package com.qqt.music.ui.navigation

import com.qqt.music.data.api.model.Album

/**
 * 专辑卡片 -> 专辑歌曲页的数据交接。
 *
 * 歌曲列表由目标页通过 album_songs 接口分页加载，
 * 此处仅暂存专辑本身（用于顶栏标题与请求参数），
 * 目标页通过路由上的 {aid} 校验一致性。
 */
object AlbumNav {
    var album: Album? = null
}
