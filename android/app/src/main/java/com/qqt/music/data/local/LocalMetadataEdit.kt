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

/** 留空或与文件标签值一致都不算「用户改过」 */
private fun String.overrideIfCustom(tagValue: String): String? = takeIf { it.isNotBlank() && it != tagValue }
