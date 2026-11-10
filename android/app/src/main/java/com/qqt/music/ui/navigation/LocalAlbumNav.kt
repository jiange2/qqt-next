package com.qqt.music.ui.navigation

/**
 * 本地专辑卡片 → 本地专辑页的数据交接。
 *
 * 歌曲列表本来就在索引里（[com.qqt.music.ui.screens.localmusic.LocalMusicViewModel]），
 * 目标页只需专辑键就能从索引里取出整张专辑；这里只额外暂存专辑名，供顶栏标题使用
 * （顶栏在导航之外，拿不到某个页面的状态）。专辑键本身按 URL 安全编码后作为路由参数。
 */
object LocalAlbumNav {
    var albumName: String? = null
}