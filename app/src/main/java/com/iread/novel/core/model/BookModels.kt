package com.iread.novel.core.model

enum class BookFormat { TXT, EPUB }

data class BookMetadata(val title: String, val author: String)

data class Chapter(val index: Int, val title: String, val body: String)

data class ParsedBook(val metadata: BookMetadata, val chapters: List<Chapter>)

data class ImportedBook(
    val id: String,
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val sourcePath: String,
    val fingerprint: String,
    val importedAt: Long,
)

data class BookContent(
    val id: String,
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
)

data class BookSummary(
    val id: String,
    val title: String,
    val author: String,
    val unreadChapters: Int,
    val totalChapters: Int,
)

data class ReadingProgress(
    val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val lastCompletedChapterIndex: Int,
)
