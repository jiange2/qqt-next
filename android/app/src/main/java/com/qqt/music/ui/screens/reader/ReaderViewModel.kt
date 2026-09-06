package com.qqt.music.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.BookChapter
import com.qqt.music.data.local.ReadingProgressStore
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.navigation.ReaderNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 阅读页状态机：Loading → Reading；目录/正文加载失败与空书落 Error（空书不可重试） */
sealed interface ReaderState {
    data object Loading : ReaderState
    data class Reading(
        val chapterId: String,
        val chapterTitle: String,
        val paragraphs: List<String>,
        val chapterIndex: Int,
        /** 恢复位置（0..1）：仅目录加载后的首次直落携带，UI 消费后置零 */
        val restoreRatio: Float,
    ) : ReaderState
    data class Error(val message: String, val retryable: Boolean) : ReaderState
}

/**
 * 阅读页（书籍阅读域 ADR 0011）：两段式加载——进页拉全书目录 → 按 ReadingProgressStore
 * 进度直落该章（无进度从第一章）→ 取单章正文。章节顺序即 id 序（章节无 sort 字段）。
 */
class ReaderViewModel : ViewModel() {
    private val book = ReaderNav.book
    private val bookId = book?.id.orEmpty()

    val bookName: String get() = book?.name.orEmpty()

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _chapters = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters: StateFlow<List<BookChapter>> = _chapters.asStateFlow()

    private var chapterIndex = 0

    init { openInitial() }

    /** 首次打开 / 失败重试共用 */
    fun openInitial() {
        if (bookId.isBlank()) {
            _state.value = ReaderState.Error("内容已失效", retryable = false)
            return
        }
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            val list = MusicRepository.getBookChapters(bookId)
            if (list == null) {
                _state.value = ReaderState.Error("目录加载失败，请重试", retryable = true)
                return@launch
            }
            if (list.isEmpty()) {
                _state.value = ReaderState.Error("本书暂无章节", retryable = false)
                return@launch
            }
            _chapters.value = list
            // 两段式：按本地进度直落该章（进度章节已不存在则从第一章）
            val saved = ReadingProgressStore.getProgress(bookId)
            val index = saved?.let { (chapterId, _) -> list.indexOfFirst { it.id == chapterId } }
                ?.takeIf { it >= 0 }
            chapterIndex = index ?: 0
            loadChapter(chapterIndex, index?.let { saved.second } ?: 0f)
        }
    }

    /** 打开指定章（目录点选 / 上一章 / 下一章共用）；重复点当前章仅由 UI 关闭浮层，不重载 */
    fun openChapter(index: Int) {
        val list = _chapters.value
        if (index < 0 || index >= list.size || index == chapterIndex) return
        chapterIndex = index
        loadChapter(index, 0f)
    }

    fun openPrev() = openChapter(chapterIndex - 1)

    fun openNext() = openChapter(chapterIndex + 1)

    private fun loadChapter(index: Int, restoreRatio: Float) {
        val chapter = _chapters.value.getOrNull(index) ?: return
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            val content = MusicRepository.getChapterContent(chapter.id)
            if (content == null) {
                _state.value = ReaderState.Error("章节加载失败，请重试", retryable = true)
                return@launch
            }
            _state.value = ReaderState.Reading(
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                paragraphs = content.content.split("\n"),
                chapterIndex = index,
                restoreRatio = restoreRatio,
            )
            // 章切换即记进度（无恢复位置 = 落章首）
            if (restoreRatio == 0f) {
                ReadingProgressStore.saveProgress(bookId, chapter.id, 0f)
            }
        }
    }

    /** 滚动节流（UI 侧约 2s 一次）与退出阅读页时写入 */
    fun saveProgress(ratio: Float) {
        val s = _state.value as? ReaderState.Reading ?: return
        ReadingProgressStore.saveProgress(bookId, s.chapterId, ratio)
    }

    /** UI 消费恢复位置后置零，避免重组期间重复 scrollToItem */
    fun consumeRestore() {
        val s = _state.value as? ReaderState.Reading ?: return
        if (s.restoreRatio > 0f) _state.value = s.copy(restoreRatio = 0f)
    }
}
