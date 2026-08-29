package com.qqt.music.ui.navigation

import com.qqt.music.data.api.model.Category

/**
 * 分类卡片 -> 分类歌曲页的数据交接。
 *
 * 歌曲列表由目标页通过 cat_songs 接口分页加载，
 * 此处仅暂存分类本身（用于顶栏标题与请求参数），
 * 目标页通过路由上的 {cid} 校验一致性。
 */
object CategoryNav {
    var category: Category? = null
}
