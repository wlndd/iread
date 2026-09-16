package com.iread.novel.data.db

import com.iread.novel.core.model.BookSummary

fun BookRow.toBookSummary(): BookSummary = BookSummary(
    id = id,
    title = title,
    author = author,
    unreadChapters = unreadChapters,
    totalChapters = totalChapters,
)
