package com.iread.novel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert
    suspend fun insertBook(book: BookEntity)

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun insertBookWithChapters(
        book: BookEntity,
        chapters: List<ChapterEntity>,
    ) {
        insertBook(book)
        insertChapters(chapters)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE fingerprint = :fingerprint)")
    suspend fun hasFingerprint(fingerprint: String): Boolean

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query("SELECT chapterIndex, title FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapterIndex(bookId: String): List<ChapterIndexRow>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :index")
    suspend fun getChapter(bookId: String, index: Int): ChapterEntity?

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex, characterOffset")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND characterOffset = :characterOffset")
    suspend fun removeBookmark(bookId: String, chapterIndex: Int, characterOffset: Int)

    @Transaction
    suspend fun getBookWithChapters(bookId: String): BookWithChapters? {
        val book = getBook(bookId) ?: return null
        return BookWithChapters(book, getChapters(bookId))
    }

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeProgress(bookId: String): Flow<ReadingProgressEntity?>

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :bookId")
    suspend fun updateMetadata(bookId: String, title: String, author: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query(
        """
        SELECT
            b.id,
            b.title,
            b.author,
            b.sourcePath,
            b.totalChapters,
            MAX(
                b.totalChapters - MAX(
                    COALESCE(p.lastCompletedChapterIndex, -1) + 1,
                    0
                ),
                0
            ) AS unreadChapters
        FROM books AS b
        LEFT JOIN reading_progress AS p ON p.bookId = b.id
        ORDER BY COALESCE(b.lastReadAt, b.importedAt) DESC
        """,
    )
    fun observeBookRows(): Flow<List<BookRow>>
}
