package com.iread.novel.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class IReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_progress ADD COLUMN mode TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN sourceUri TEXT")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmarks (
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                characterOffset INTEGER NOT NULL,
                snippet TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(bookId, chapterIndex, characterOffset),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
