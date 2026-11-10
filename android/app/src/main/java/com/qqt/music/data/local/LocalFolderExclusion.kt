package com.qqt.music.data.local

import java.text.Collator
import java.util.Locale

/**
 * 文件夹黑名单（spec「文件夹黑名单」，票据 03）：排除清单是一组文件夹路径，
 * 匹配语义为「该文件夹及其全部子文件夹」。这一层是纯 Kotlin：入参只有归一化歌曲行
 * 与排除路径集合，是管线里可 JVM 单测的过滤环节（spec「Testing Decisions」）。
 *
 * 路径形态（行归一化约定，[MediaStoreLibrarySource]）：
 * - Android 10+：[LocalSong.path] 是 `RELATIVE_PATH`（目录本身、尾带 `/`，如 `Music/Foo/`）
 * - Android 9 及以下：[LocalSong.path] 是 `DATA`（文件全路径，如 `/storage/emulated/0/Music/Foo/a.mp3`）
 *
 * 匹配与展示都不区分这两种形态：先把「行所在文件夹」归一出来，再做目录段边界上的前缀匹配。
 */

/** 库中的一个文件夹（排除面板的候选项）：[path] 参与匹配与落盘，[name] 是展示用目录名 */
data class LocalFolder(
    val path: String,
    val name: String,
)

/**
 * 行所在文件夹的路径（匹配与候选派生都以它为准）：`RELATIVE_PATH` 形态尾带 `/`、本身即目录，
 * 去掉尾斜杠即可；`DATA` 形态是文件全路径，取目录段。文件直接在存储根时为空串
 * （这类行不属于任何可选文件夹，始终可见）。
 */
fun folderPathOf(song: LocalSong): String =
    if (song.path.endsWith("/")) song.path.trimEnd('/') else song.path.substringBeforeLast('/', "")

/** 展示名：所在目录名（Android 10+ 与 `BUCKET_DISPLAY_NAME` 同源，低版本即目录段尾名） */
fun folderNameOf(song: LocalSong): String = folderPathOf(song).substringAfterLast('/', "")

/**
 * 排除面板的候选清单（spec：自动列出当前库中所有含音乐的文件夹，无需手输路径）：
 * 从当前库全部行的路径派生——含已被排除的文件夹，否则取消排除无从谈起。
 * 按目录名中文序排列，同名再按路径定序，保证清单稳定。
 */
fun List<LocalSong>.distinctFolders(): List<LocalFolder> {
    val collator = Collator.getInstance(Locale.CHINA)
    return asSequence()
        .map { LocalFolder(path = folderPathOf(it), name = folderNameOf(it)) }
        .filter { it.path.isNotBlank() && it.name.isNotBlank() }
        .distinctBy { it.path }
        .sortedWith(compareWith(collator))
        .toList()
}

private fun compareWith(collator: Collator): Comparator<LocalFolder> =
    compareBy(collator) { it.name }.thenBy { it.path }

/** 匹配前归一：裁掉首尾空白与尾斜杠（落盘值与行路径都过一遍，口径一致） */
private fun normalizeFolderPath(path: String): String = path.trim().trimEnd('/')

/**
 * 一个文件夹是否被排除：排除清单里任一路径在目录段边界上是它的前缀即被排除——
 * 排除 `Music` 时 `Music/Live`、`Music/Live/2024` 都算，`MusicX` 不算（前缀必须整段命中）。
 * 空目录（存储根散装文件）不属于任何可选文件夹，永不命中。
 */
fun isFolderExcluded(folderPath: String, excludedFolders: Collection<String>): Boolean {
    val folder = normalizeFolderPath(folderPath)
    if (folder.isEmpty()) return false
    return excludedFolders.any { candidate ->
        val excluded = normalizeFolderPath(candidate)
        excluded.isNotEmpty() && (folder == excluded || folder.startsWith("$excluded/"))
    }
}

/**
 * 排除过滤（spec：被排除的行不进歌曲列表、不进专辑分组、不进播放队列候选）：
 * 清单为空时原样返回，其余按 [isFolderExcluded] 滤掉整棵被排除的目录树。
 */
fun List<LocalSong>.excludingFolders(excludedFolders: Collection<String>): List<LocalSong> {
    if (excludedFolders.isEmpty()) return this
    return filterNot { isFolderExcluded(folderPathOf(it), excludedFolders) }
}
