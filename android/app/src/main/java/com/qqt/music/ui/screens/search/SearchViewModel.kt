package com.qqt.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.SearchResults
import com.qqt.music.data.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 搜索页数据源：组合搜索（song_search 不带 search_type）一次取回三段结果——
 * 歌曲段按页返回（每页 10），专辑/艺术家段各 20 条截断不分页。
 * 提交式触发（回车/IME 搜索键），输入过程不发请求。
 * 歌曲段的全量分页由搜索结果页经 searchSongs 接口继续。
 */
class SearchViewModel : ViewModel() {
    /** 当前生效关键词（已 trim），与输入框草稿解耦 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<SearchResults?>(null)
    val results: StateFlow<SearchResults?> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchFailed = MutableStateFlow(false)
    val searchFailed: StateFlow<Boolean> = _searchFailed.asStateFlow()

    private var searchJob: Job? = null

    /** 提交搜索：新提交取消进行中的旧请求，防旧结果晚到覆盖新结果 */
    fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchFailed.value = false
            val res = MusicRepository.searchAll(q)
            _query.value = q
            _results.value = res
            _isSearching.value = false
            _searchFailed.value = res == null
        }
    }

    /** 清空输入，回到初始引导态 */
    fun reset() {
        searchJob?.cancel()
        _query.value = ""
        _results.value = null
        _isSearching.value = false
        _searchFailed.value = false
    }
}
