package com.iread.novel.data.repository

import com.iread.novel.core.model.BookContent
import com.iread.novel.core.model.BookSummary
import com.iread.novel.core.model.ImportedBook
import com.iread.novel.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<BookSummary>>
    suspend fun hasFingerprint(fingerprint: String): Boolean
    suspend fun insertImportedBook(book: ImportedBook)
    suspend fun loadBook(bookId: String): BookContent?
    fun observeProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun updateMetadata(bookId: String, title: String, author: String)
    suspend fun deleteBook(bookId: String)
}
