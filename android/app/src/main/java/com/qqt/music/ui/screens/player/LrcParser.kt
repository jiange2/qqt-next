package com.qqt.music.ui.screens.player

/** 歌词行：行起始时间（毫秒）与文本内容 */
internal data class LrcLine(
    val timeMs: Long,
    val text: String,
)

/** LRC 歌词解析：支持 [mm:ss]、[mm:ss.xx]、[mm:ss.xxx] 时间标签与一行多标签；元数据标签与空行忽略 */
internal object LrcParser {

    // 时间标签：分钟 1-3 位、秒 1-2 位、百分秒可选（. 或 : 分隔，1-3 位）
    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")

    /** 解析 LRC 文本为按时间升序的歌词行；无可解析行返回空列表 */
    fun parse(lrcText: String): List<LrcLine> {
        if (lrcText.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        for (raw in lrcText.lineSequence()) {
            val tags = TIME_TAG.findAll(raw).toList()
            if (tags.isEmpty()) continue
            // 行文本 = 最后一个时间标签之后的部分（元数据标签 [ti:xx] 等不含时间标签，已在上面跳过）
            val text = raw.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (tag in tags) {
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                val fractionMs = when (val fraction = tag.groupValues[3]) {
                    "" -> 0L
                    // 百分秒位数不定：1 位=十分秒、2 位=百分秒、3 位=毫秒，统一右对齐到毫秒
                    else -> fraction.take(3).padEnd(3, '0').toLong()
                }
                lines += LrcLine(minutes * 60_000 + seconds * 1000 + fractionMs, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
