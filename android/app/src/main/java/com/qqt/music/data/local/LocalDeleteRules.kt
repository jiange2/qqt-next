package com.qqt.music.data.local

/**
 * 删除结果 → 整库列表整形（纯函数，JVM 可测）：成功仅按歌曲身份移除目标行，
 * 失败 / 用户取消原样返回（一行不动，spec「列表联动与快照」）。
 * 传入的是整库（含被排除文件夹的行与灰显行）——展示过滤只发生在派生层，
 * 这里不做过滤、也不改动其它任何行。
 */
internal fun songsAfterDelete(songs: List<LocalSong>, songId: String, result: LocalDeleteResult): List<LocalSong> =
    when (result) {
        LocalDeleteResult.Success -> songs.filterNot { it.id == songId }
        is LocalDeleteResult.Failure -> songs
        LocalDeleteResult.Cancelled -> songs
    }

/**
 * 是否需要 App 内删除确认框（纯函数，JVM 可测）：仅 ≤28 直接删链路（[DeleteRoute.DirectWrite]）——
 * 该链路没有系统框，App 确认框兜底；29 系统授权框 / 30+ 系统删除框即唯一确认，
 * 不得再叠加 App 框（spec「确认交互」）。
 */
internal fun needsAppDeleteConfirm(sdkInt: Int): Boolean =
    deleteRoute(sdkInt) == DeleteRoute.DirectWrite

/**
 * 专辑删空自动返回的判据（纯函数，JVM 可测）：删后该专辑剩余的可见行数——
 * 只数可见行（被排除文件夹的行不计入），与专辑视图同口径（[excludingFolders] 后按 [albumKey] 清点）。
 * 剩余 0 即专辑被删空，导航层据此把专辑页自动返回本地音乐页（决策 A2）。
 */
internal fun remainingVisibleInAlbum(
    songs: List<LocalSong>,
    albumKey: String,
    excludedFolders: Collection<String>,
): Int = songs.excludingFolders(excludedFolders).count { it.albumKey() == albumKey }