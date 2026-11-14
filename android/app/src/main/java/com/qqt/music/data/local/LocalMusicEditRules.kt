package com.qqt.music.data.local

/**
 * 编辑模式 UI 侧纯规则（票据02，spec「编辑模式状态与选择态」「批量面板」，JVM 可测）：
 * 选择态的候选集 / 全选切换 / 已全选判据，以及批量面板的保存门槛。
 * 传入的是可见列表（ViewModel `songs`：已按排除清单过滤，权限被收回时整库灰显）——
 * 灰显行（[LocalSong.available] 为 false）不可勾选，这里统一跳过。
 * 勾选框渲染、点行 / 长按切换与退出路径在 Compose 层，不在这里。
 */

/** 可勾选行 = 可见列表中可用的行（灰显行跳过）；全选候选集与「已全选」判据共用这一口径 */
internal fun selectableSongIds(songs: List<LocalSong>): Set<String> =
    songs.filter { it.available }.mapTo(mutableSetOf()) { it.id }

/**
 * 全选切换：已全选（全部可用行都在勾选里）再点 = 清空；否则 = 勾上全部可用行。
 * 没有可用行时是 no-op（空库 / 整库灰显），顺带清掉不在可见列表里的残留勾选。
 */
internal fun toggleSelectAll(songs: List<LocalSong>, selected: Set<String>): Set<String> {
    return if (isAllSelected(songs, selected)) emptySet() else selectableSongIds(songs)
}

/**
 * 是否已全选（顶栏「全选 / 取消全选」文案判据）：有可用行且全部已勾选。
 * 没有可用行时不算已全选——顶栏保持「全选」，点击是 no-op；残留勾选不影响判定。
 */
internal fun isAllSelected(songs: List<LocalSong>, selected: Set<String>): Boolean {
    val ids = selectableSongIds(songs)
    return ids.isNotEmpty() && ids.all { it in selected }
}

/**
 * 批量面板保存门槛（spec「批量面板」）：专辑 / 专辑艺术家两字段 trim 后皆空时禁用保存——
 * 批量语义是「填了的字段才改」，两字段都留空没有任何可提交的内容。
 */
internal fun batchPanelCanSave(albumInput: String, albumArtistInput: String): Boolean =
    albumInput.isNotBlank() || albumArtistInput.isNotBlank()