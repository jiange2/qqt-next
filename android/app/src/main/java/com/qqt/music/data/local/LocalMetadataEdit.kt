package com.qqt.music.data.local

/**
 * 元数据编辑（spec「元数据编辑」，ADR 0020）：面板里六个字段的原始输入，以及把它变成覆盖层的规则。
 *
 * 这里只有文本与取值规则，不碰界面也不碰文件：保存只写覆盖层，音频文件的标签一个字节都不改，
 * 其它播放器看到的仍是文件原本的标签。
 */

/** 编辑面板的草稿：六个字段的原始输入（首尾空白在保存时统一裁剪，见 [overrideFrom]） */
data class LocalMetadataDraft(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNo: String = "",
    val year: String = "",
)

/** 面板初值取合并后的展示值：用户看到什么就改什么（覆盖值与标签值在面板上没有区别） */
fun LocalSong.metadataDraft(): LocalMetadataDraft = LocalMetadataDraft(
    title = displayTitle(),
    artist = displayArtist(),
    album = displayAlbum(),
    albumArtist = displayAlbumArtist(),
    trackNo = displayTrackNo()?.toString().orEmpty(),
    year = displayYear()?.toString().orEmpty(),
)

/**
 * 数值字段（音轨号 / 年份）的输入解析结果：
 * [Ok] 带解析值（`null` = 留空，等于回到文件标签值），[Invalid] 带提示文案。
 */
sealed interface LocalNumberInput {
    data class Ok(val value: Int?) : LocalNumberInput
    data class Invalid(val message: String) : LocalNumberInput
}

/**
 * 校验通过时的解析值（留空为 null）；非法输入返回 null——
 * 调用方应先看 [LocalNumberInput.Invalid.message] 拦下非法输入，再取这个值。
 */
val LocalNumberInput.valueOrNull: Int?
    get() = (this as? LocalNumberInput.Ok)?.value

/**
 * 音轨号只接受正整数（spec「元数据编辑」）。全角数字、正负号、小数点一律不认；
 * 超出 Int 范围的数字解析为 null，同样按非法处理。
 */
fun parseTrackNoInput(raw: String): LocalNumberInput {
    val text = raw.trim()
    if (text.isEmpty()) return LocalNumberInput.Ok(null)
    val value = text.takeIf { candidate -> candidate.all { it in '0'..'9' } }?.toIntOrNull()
    return if (value != null && value > 0) {
        LocalNumberInput.Ok(value)
    } else {
        LocalNumberInput.Invalid("音轨号只接受正整数")
    }
}

/** 年份只接受四位数字（spec「元数据编辑」）；`0000` 不是年份，按非法处理 */
fun parseYearInput(raw: String): LocalNumberInput {
    val text = raw.trim()
    if (text.isEmpty()) return LocalNumberInput.Ok(null)
    val value = text.takeIf { it.length == 4 && it.all { char -> char in '0'..'9' } }?.toIntOrNull()
    return if (value != null && value > 0) {
        LocalNumberInput.Ok(value)
    } else {
        LocalNumberInput.Invalid("年份只接受四位数字")
    }
}

/**
 * 草稿里音轨号的实际输入：与文件标签值一字不差的输入按「没改动」处理，不参与校验。
 * 面板初值就是展示值，标签里若有不合规的老值（例如只有两位数的年份、0 音轨号），
 * 不该在用户什么都没改的时候把整张面板锁死。
 */
fun LocalSong.trackNoInput(raw: String): LocalNumberInput = taggedInput(raw, tags.trackNo, ::parseTrackNoInput)

/** [trackNoInput] 的年份版：同一套「没改动就不校验」口径 */
fun LocalSong.yearInput(raw: String): LocalNumberInput = taggedInput(raw, tags.year, ::parseYearInput)

private fun taggedInput(raw: String, tagValue: Int?, parse: (String) -> LocalNumberInput): LocalNumberInput {
    val text = raw.trim()
    return if (text == tagValue?.toString().orEmpty()) LocalNumberInput.Ok(tagValue) else parse(text)
}

/**
 * 草稿 → 覆盖层（ADR 0020）：
 *
 * - 留空的字段不写覆盖——清空即回到文件标签值（标题还会再回退到文件名）
 * - 与文件标签值相同的字段也不写覆盖——没有真正改动，不制造把标签「冻结」住的假覆盖项
 * - 其余字段存裁剪后的输入值
 *
 * [trackNo] / [year] 由调用方用 [trackNoInput] / [yearInput] 取值后传入，留空为 null。
 */
fun LocalSong.overrideFrom(draft: LocalMetadataDraft, trackNo: Int?, year: Int?): LocalSongOverride =
    LocalSongOverride(
        // 标题的标签基准与 displayTitle() 的兜底同口径：标签缺失时是去掉扩展名的文件名
        title = draft.title.trim().overrideIfCustom(tags.title.ifBlank { displayName.substringBeforeLast('.') }),
        artist = draft.artist.trim().overrideIfCustom(tags.artist),
        album = draft.album.trim().overrideIfCustom(tags.album),
        albumArtist = draft.albumArtist.trim().overrideIfCustom(tags.albumArtist),
        trackNo = trackNo?.takeIf { it != tags.trackNo },
        year = year?.takeIf { it != tags.year },
    )

/**
 * 批量面板（spec「批量提交语义」）：专辑 / 专辑艺术家两个原始输入 → 这首歌的新覆盖，
 * 「填了的字段才改」——与单首面板「留空 = 恢复文件原值」相反：
 *
 * - trim 后留空 → 保留原覆盖的该字段（没填的字段不动）
 * - 非空且与文件标签值相同 → 该字段覆盖清除（不制造把标签「冻结」住的假覆盖，沿用 [overrideIfCustom] 口径）
 * - 其余 → 存裁剪后的输入值
 *
 * 标题 / 艺术家 / 音轨号 / 年份四字段原样保留。结果可能是空壳（[LocalSongOverride.isEmpty]），
 * 调用方按单首「空壳不落盘」口径删键，见 [batchAlbumCommit]。
 */
fun LocalSong.batchAlbumOverride(albumInput: String, albumArtistInput: String): LocalSongOverride =
    LocalSongOverride(
        title = overlay?.title,
        artist = overlay?.artist,
        album = albumInput.batchAlbumField(tags.album, overlay?.album),
        albumArtist = albumArtistInput.batchAlbumField(tags.albumArtist, overlay?.albumArtist),
        trackNo = overlay?.trackNo,
        year = overlay?.year,
    )

/** 留空或与文件标签值一致都不算「用户改过」 */
private fun String.overrideIfCustom(tagValue: String): String? = takeIf { it.isNotBlank() && it != tagValue }

/** 批量字段口径：留空保留原覆盖；填了走单首同款 [overrideIfCustom]（同标签清除、其余存裁剪值） */
private fun String.batchAlbumField(tagValue: String, existing: String?): String? {
    val text = trim()
    return if (text.isEmpty()) existing else text.overrideIfCustom(tagValue)
}

/** 批量提交后的整表形态：新覆盖表 + 新列表（[batchAlbumCommit] 的产物，ViewModel 拿去锁内落位） */
data class BatchAlbumCommit(
    val overlay: Map<String, LocalSongOverride>,
    val songs: List<LocalSong>,
)

/**
 * 批量提交的整表变换（spec「批量提交语义」，JVM 可测接缝）：按快照列表与当前覆盖表，
 * 为选中行生成新覆盖表与新列表——
 *
 * - 只替换选中行，其余行原样；行从快照取，可用状态保留快照本值（UI 传入的展示副本可能灰显，不参与生成）
 * - 原覆盖以覆盖表现值为准（它是按行 ID 落盘与合并的权威来源）
 * - 新覆盖成空壳 → 删键（沿用单首「空壳不落盘」口径）；未选中行的键不动
 * - 列表按展示标题重排（批量只改专辑字段，行序实际不变）
 *
 * 不在快照里的选中行直接跳过（与在途删除竞态的已知取舍）。锁、一次原子落盘与提示由
 * ViewModel 入口编排（[com.qqt.music.ui.screens.localmusic.LocalMusicViewModel.saveBatchMetadata]）。
 */
fun batchAlbumCommit(
    songs: List<LocalSong>,
    overlay: Map<String, LocalSongOverride>,
    selectedIds: Set<String>,
    albumInput: String,
    albumArtistInput: String,
): BatchAlbumCommit {
    var updated = overlay
    val rows = songs.map { row ->
        if (row.id !in selectedIds) return@map row
        val override = row.withOverride(overlay[row.id]).batchAlbumOverride(albumInput, albumArtistInput)
        updated = if (override.isEmpty) updated - row.id else updated + (row.id to override)
        row.withOverride(override.takeUnless { it.isEmpty })
    }
    return BatchAlbumCommit(updated, rows.sortedByDisplayTitle())
}

/**
 * 专辑页批量提交后的去向推算（票据04，JVM 可测接缝）：返回选中行提交后落到的新专辑键集合。
 * 在提交前按当前展示值同步推算——albums 派生异步重算，提交后读它会拿到旧值
 * （与 [com.qqt.music.ui.screens.localmusic.LocalDeleteSuccess] 同一理由）。
 * 提交后展示值口径与 [batchAlbumOverride] 一致：trim 后非空的输入即新展示值
 * （与标签同值清覆盖不改变展示值），留空保留原展示值。
 *
 * - 空集 = 留在原页：未全选（未选中行——含不可勾选的灰显行——保留旧键，原专辑仍在），
 *   或全选但键不变（输入与当前展示值相同 / 文件夹兜底专辑只改专辑艺术家）
 * - 单元素 = 全选且整张专辑搬进唯一新专辑，调用方跟随新专辑
 * - 多元素 = 全选但拆成多张新专辑（文件夹兜底专辑只填专辑名、成员专辑艺术家不同），
 *   无唯一去向，调用方返回本地音乐页
 */
internal fun batchAlbumNewKeys(
    albumSongs: List<LocalSong>,
    selected: Set<String>,
    albumInput: String,
    albumArtistInput: String,
): Set<String> {
    if (albumSongs.isEmpty() || !albumSongs.all { it.id in selected }) return emptySet()
    val album = albumInput.trim()
    val artist = albumArtistInput.trim()
    val newKeys = albumSongs.mapTo(mutableSetOf()) { song ->
        song.albumKeyOf(
            album.ifBlank { song.displayAlbum() },
            artist.ifBlank { song.displayAlbumArtist() },
        )
    }
    return if (newKeys == setOf(albumSongs.first().albumKey())) emptySet() else newKeys
}
