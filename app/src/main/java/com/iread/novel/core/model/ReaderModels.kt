package com.iread.novel.core.model

enum class ReaderMode { PAGED, SCROLL }
enum class ReaderTheme { PAPER, BLUE, NIGHT }

data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val mode: ReaderMode = ReaderMode.PAGED,
    val fontSize: Int = 19,
    val lineSpacing: Float = 1.7f,
)

data class Bookmark(
    val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val snippet: String,
    val createdAt: Long,
)
