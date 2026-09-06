package com.qqt.music.ui.navigation

import com.qqt.music.data.api.model.Book

/**
 * 书籍卡片 -> 阅读页的数据交接（书籍阅读域 ADR 0011）。
 *
 * 目录与正文由阅读页通过 book_chapters / book_chapter 接口加载，
 * 此处仅暂存书籍本身（用于顶栏标题），目标页通过路由上的 {bid} 校验一致性。
 */
object ReaderNav {
    var book: Book? = null
}
