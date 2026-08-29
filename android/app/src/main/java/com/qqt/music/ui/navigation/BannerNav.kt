package com.qqt.music.ui.navigation

import com.qqt.music.data.api.model.Banner

/**
 * 轮播图 -> 横幅歌曲页的数据交接。
 *
 * 横幅及其歌曲列表已随首页接口内嵌返回，无需二次请求；
 * 点击横幅时在此暂存，目标页通过路由上的 {bid} 校验一致性。
 */
object BannerNav {
    var banner: Banner? = null
}
