package com.qqt.music.ui.screens.booklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.Book
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.navigation.CategoryNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 书单页：cat_books 接口分页加载该书籍分类下的书（书籍阅读域 ADR 0011），分页范式对齐 CategoryAlbumsViewModel */
class BookListViewModel : ViewModel() {
    private val catId = CategoryNav.category?.id.orEmpty()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var page = 1
    private var hasMore = true

    init { loadMore() }

    fun loadMore() {
        if (catId.isBlank() || _isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val (result, total) = MusicRepository.getCategoryBooks(catId, page)
            if (result.isEmpty() && total < 0) hasMore = false
            else {
                _books.value = _books.value + result
                page++
                if (total >= 0) hasMore = _books.value.size < total
            }
            _isLoading.value = false
        }
    }
}
