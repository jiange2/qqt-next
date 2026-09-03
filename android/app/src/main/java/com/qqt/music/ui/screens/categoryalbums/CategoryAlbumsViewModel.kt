package com.qqt.music.ui.screens.categoryalbums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.Album
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.navigation.CategoryNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 分类专辑页：cat_albums 接口分页加载该分类下的专辑（backend-next ADR 0009）。
 *  分类下仅 1 张专辑时（首页加载完成即判定），置位 autoOpen 供导航层替换当前栈条目直跳专辑歌曲页 */
class CategoryAlbumsViewModel : ViewModel() {
    private val catId = CategoryNav.category?.id.orEmpty()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 单专辑直跳：仅首页加载完成后判定一次，非空时导航层应替换当前栈条目进入专辑歌曲页 */
    private val _autoOpen = MutableStateFlow<Album?>(null)
    val autoOpen: StateFlow<Album?> = _autoOpen.asStateFlow()
    private var page = 1
    private var hasMore = true

    init { loadMore() }

    fun loadMore() {
        if (catId.isBlank() || _isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val isFirstPage = page == 1
            val (result, total) = MusicRepository.getCategoryAlbums(catId, page)
            // total 解析失败（-1）时退回空页探测，但首页恰返回 1 条也必是单专辑（后端页大小为 10）
            val singleAlbum = result.singleOrNull()
                .takeIf { isFirstPage && (total == 1 || (total < 0 && result.size == 1)) }
            if (result.isEmpty() && total < 0) hasMore = false
            else {
                _albums.value = _albums.value + result
                page++
                if (total >= 0) hasMore = _albums.value.size < total
            }
            singleAlbum?.let { _autoOpen.value = it }
            _isLoading.value = false
        }
    }
}
