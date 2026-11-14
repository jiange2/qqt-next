package com.qqt.music.data.local

import java.text.Collator
import java.util.Locale

/**
 * 编辑候选（spec「编辑候选」）：单首 / 批量编辑面板里专辑、艺术家、专辑艺术家字段的下拉候选。
 * 全部从本地库当前展示值派生（覆盖层优先，ADR 0020），不新增查询、不碰在线实体。
 *
 * [value] 点选后填入本框的值；[supporting] 候选行次文本——专辑候选放专辑艺术家
 * （卡片展示口径），点选时连带回填专辑艺术家框（spec「专辑候选联动」），其余字段为 null。
 */
data class MetadataSuggestion(
    val value: String,
    val supporting: String? = null,
)

/**
 * 专辑候选：复用 [groupIntoAlbums] 的归并与中文排序，每行「专辑名 + 专辑艺术家（卡片展示值）」。
 *
 * 只收有真实专辑标签的组：文件夹兜底专辑（专辑名为空、按文件夹名展示）不进候选——
 * 文件夹组要求专辑标签为空，写入专辑名只会造出一张同名平行的专辑键组，
 * 点选它无法真正归并进原文件夹组（spec Further Notes）。判据用首成员展示专辑名非空，
 * 不耦合专辑键字符串格式。按展示对去重：专辑艺术家为空回退出的艺术家与显式同值专辑艺术家
 * 展示相同，只留一行。
 */
fun List<LocalSong>.albumSuggestions(): List<MetadataSuggestion> =
    groupIntoAlbums()
        .filter { it.songs.first().displayAlbum().isNotBlank() }
        .map { MetadataSuggestion(value = it.name, supporting = it.artist) }
        .distinct()

/** 艺术家候选：歌曲艺术家展示值非空去重，中文序（与专辑视图同一份排序规则） */
fun List<LocalSong>.artistSuggestions(): List<MetadataSuggestion> {
    val collator = Collator.getInstance(Locale.CHINA)
    return map { it.displayArtist() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(collator)
        .map { MetadataSuggestion(it) }
}

/**
 * 专辑艺术家候选：专辑艺术家与艺术家展示值的并集非空去重，中文序。
 * 专辑艺术家列 API 30+ 才有值、标签常缺失，卡片展示本就回退艺术家（[groupIntoAlbums]），
 * 候选池同口径——否则低版本设备上这个框永远没有候选。
 */
fun List<LocalSong>.albumArtistSuggestions(): List<MetadataSuggestion> {
    val collator = Collator.getInstance(Locale.CHINA)
    return (map { it.displayAlbumArtist() } + map { it.displayArtist() })
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(collator)
        .map { MetadataSuggestion(it) }
}

/** 打字过滤：query trim 后为空 → 全量；否则主文本或次文本包含（忽略大小写）即命中 */
fun List<MetadataSuggestion>.filterSuggestions(query: String): List<MetadataSuggestion> {
    val text = query.trim()
    if (text.isEmpty()) return this
    return filter { suggestion ->
        suggestion.value.contains(text, ignoreCase = true) ||
            suggestion.supporting?.contains(text, ignoreCase = true) == true
    }
}