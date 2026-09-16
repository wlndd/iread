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
)

data class BookRow(
    val id: String,
    val title: String,
    val author: String,
    val totalChapters: Int,
    val unreadChapters: Int,
)
