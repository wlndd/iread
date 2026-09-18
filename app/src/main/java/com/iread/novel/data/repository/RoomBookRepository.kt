package com.iread.novel.data.repository

import com.iread.novel.core.model.BookContent
import com.iread.novel.core.model.Bookmark
import com.iread.novel.core.model.ReaderMode
import com.iread.novel.data.db.BookmarkEntity
import com.iread.novel.core.model.BookFormat
import com.iread.novel.core.model.BookSummary
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ImportedBook
import com.iread.novel.core.model.ReadingProgress
import com.iread.novel.data.db.BookDao
import com.iread.novel.data.db.BookEntity
import com.iread.novel.data.db.ChapterEntity
import com.iread.novel.data.db.ReadingProgressEntity
import com.iread.novel.data.db.toBookSummary
import com.iread.novel.domain.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBookRepository(
    private val dao: BookDao,
    private val timeSource: TimeSource = TimeSource(System::currentTimeMillis),
) : BookRepository {
    override fun observeBooks(): Flow<List<BookSummary>> =
        dao.observeBookRows().map { rows -> rows.map { it.toBookSummary() } }

    override suspend fun hasFingerprint(fingerprint: String): Boolean =
        dao.hasFingerprint(fingerprint)

    override suspend fun insertImportedBook(book: ImportedBook) {
        dao.insertBookWithChapters(
            book = BookEntity(
                id = book.id,
                title = book.metadata.title,
                author = book.metadata.author,
                format = book.format.name,
                sourcePath = book.sourcePath,
                fingerprint = book.fingerprint,
                totalChapters = book.chapters.size,
                importedAt = book.importedAt,
                lastReadAt = null,
                sourceUri = book.sourceUri,
            ),
            chapters = book.chapters.map { chapter ->
                ChapterEntity(book.id, chapter.index, chapter.title, chapter.body)
            },
        )
    }

    override suspend fun loadBook(bookId: String): BookContent? {
        val aggregate = dao.getBookWithChapters(bookId) ?: return null
        return BookContent(
            id = aggregate.book.id,
            title = aggregate.book.title,
            author = aggregate.book.author,
            chapters = aggregate.chapters.map { chapter ->
                Chapter(chapter.chapterIndex, chapter.title, chapter.body)
            },
        )
    }

    override suspend fun loadBookIndex(bookId: String): BookContent? {
        val book = dao.getBook(bookId) ?: return null
        return BookContent(book.id, book.title, book.author,
            dao.getChapterIndex(bookId).map { Chapter(it.chapterIndex, it.title, "") })
    }

    override suspend fun loadChapter(bookId: String, index: Int): Chapter? =
        dao.getChapter(bookId, index)?.let { Chapter(it.chapterIndex, it.title, it.body) }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        dao.observeBookmarks(bookId).map { rows -> rows.map {
            Bookmark(it.bookId, it.chapterIndex, it.characterOffset, it.snippet, it.createdAt)
        } }

    override suspend fun upsertBookmark(bookmark: Bookmark) {
        dao.upsertBookmark(BookmarkEntity(bookmark.bookId, bookmark.chapterIndex,
            bookmark.characterOffset, bookmark.snippet, bookmark.createdAt))
    }

    override suspend fun removeBookmark(bookId: String, chapterIndex: Int, characterOffset: Int) {
        dao.removeBookmark(bookId, chapterIndex, characterOffset)
    }

    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        dao.observeProgress(bookId).map { progress ->
            progress?.let {
                ReadingProgress(
                    bookId = it.bookId,
                    chapterIndex = it.chapterIndex,
                    characterOffset = it.characterOffset,
                    lastCompletedChapterIndex = it.lastCompletedChapterIndex,
                    mode = it.mode?.let { name -> ReaderMode.entries.firstOrNull { mode -> mode.name == name } },
                )
            }
        }

    override suspend fun saveProgress(progress: ReadingProgress) {
        dao.upsertProgress(
            ReadingProgressEntity(
                bookId = progress.bookId,
                chapterIndex = progress.chapterIndex,
                characterOffset = progress.characterOffset,
                lastCompletedChapterIndex = progress.lastCompletedChapterIndex,
                updatedAt = timeSource.nowMillis(),
                mode = progress.mode?.name,
            ),
        )
    }

    override suspend fun updateMetadata(bookId: String, title: String, author: String) {
        dao.updateMetadata(bookId, title, author)
    }

    override suspend fun deleteBook(bookId: String) {
        dao.deleteBook(bookId)
    }
}
