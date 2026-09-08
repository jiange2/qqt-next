package com.qqt.music.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读本地存储（书籍阅读域 ADR 0011），独立 SharedPreferences 文件：
 *
 * - 阅读进度：每书一条 {chapterId, ratio}（ratio = 章内已读位置 0..1，按段落换算）；
 *   写入时机 = 章切换 / 滚动停止约 2s（UI 侧节流）/ 退出阅读页；恢复时按 ratio 换算段落索引
 * - 阅读字号：全局一份偏好（五档 20–36，默认 24）
 * - 阅读背景：全局一份偏好（护眼色系主题，默认护眼绿）
 *
 * 进程内唯一事实来源，仅阅读域读写；变更即时落盘。
 */
object ReadingProgressStore {

    private const val PREF_NAME = "reading_prefs"
    private const val KEY_FONT_SIZE_SP = "reader_font_size_sp"
    private const val KEY_THEME_ID = "reader_theme_id"

    /** 字号五档（ADR 0011），声明顺序即设置面板展示顺序 */
    val FONT_SIZES_SP = listOf(20, 24, 28, 32, 36)

    /** 默认字号：仅未主动设置过字号的用户跟随此值；主动选过的档位（含 20）优先 */
    private const val DEFAULT_FONT_SIZE_SP = 24

    /** 阅读背景主题（护眼色系）：bg/onBg 为 ARGB Long（UI 侧转 Color），夜间为唯一深色套 */
    data class ReadingTheme(val id: String, val label: String, val bg: Long, val onBg: Long)

    val READER_THEMES = listOf(
        ReadingTheme("green", "护眼绿", 0xFFC7EDCC, 0xFF1F2D23),
        ReadingTheme("paper", "米黄", 0xFFF5EEDC, 0xFF3D3428),
        ReadingTheme("cyan", "淡青", 0xFFE3EDEA, 0xFF26332F),
        ReadingTheme("white", "素白", 0xFFFFFFFF, 0xFF1A1A1A),
        ReadingTheme("night", "夜间", 0xFF121212, 0xFFA8A8A8),
    )

    private lateinit var prefs: SharedPreferences

    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _theme = MutableStateFlow(READER_THEMES.first())
    val theme: StateFlow<ReadingTheme> = _theme.asStateFlow()

    /** MainActivity 启动时装载（独立 prefs 文件，与其他 Store 无初始化顺序依赖） */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP)
        _fontSizeSp.value = if (saved in FONT_SIZES_SP) saved else DEFAULT_FONT_SIZE_SP
        val savedTheme = prefs.getString(KEY_THEME_ID, null)
        READER_THEMES.firstOrNull { it.id == savedTheme }?.let { _theme.value = it }
    }

    fun setFontSizeSp(size: Int) {
        if (size !in FONT_SIZES_SP) return
        _fontSizeSp.value = size
        prefs.edit().putInt(KEY_FONT_SIZE_SP, size).apply()
    }

    fun setTheme(id: String) {
        val target = READER_THEMES.firstOrNull { it.id == id } ?: return
        _theme.value = target
        prefs.edit().putString(KEY_THEME_ID, id).apply()
    }

    /** 恢复目标：无进度（或记录损坏）返回 null，阅读页从第一章开始 */
    fun getProgress(bookId: String): Pair<String, Float>? {
        if (bookId.isBlank()) return null
        val raw = prefs.getString(key(bookId), null) ?: return null
        val parts = raw.split("|")
        val chapterId = parts.getOrNull(0) ?: return null
        val ratio = parts.getOrNull(1)?.toFloatOrNull() ?: return null
        if (chapterId.isBlank() || ratio !in 0f..1f) return null
        return chapterId to ratio
    }

    fun saveProgress(bookId: String, chapterId: String, ratio: Float) {
        if (bookId.isBlank() || chapterId.isBlank()) return
        prefs.edit().putString(key(bookId), "$chapterId|${ratio.coerceIn(0f, 1f)}").apply()
    }

    private fun key(bookId: String) = "progress_$bookId"
}
