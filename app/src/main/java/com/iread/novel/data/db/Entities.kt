package com.iread.novel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val format: String,
    val sourcePath: String,
    val fingerprint: String,
    val totalChapters: Int,
    val importedAt: Long,
    val lastReadAt: Long?,
    val sourceUri: String? = null,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChapterEntity(
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val body: String,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val lastCompletedChapterIndex: Int,
    val updatedAt: Long,
    val mode: String? = null,
)

data class BookRow(
    val id: String,
    val title: String,
    val author: String,
    val totalChapters: Int,
    val unreadChapters: Int,
    val sourcePath: String? = null,
)

data class BookWithChapters(
    val book: BookEntity,
    val chapters: List<ChapterEntity>,
)

@Entity(
    tableName = "bookmarks",
    primaryKeys = ["bookId", "chapterIndex", "characterOffset"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BookmarkEntity(
    val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val snippet: String,
    val createdAt: Long,
)

data class ChapterIndexRow(val chapterIndex: Int, val title: String)
