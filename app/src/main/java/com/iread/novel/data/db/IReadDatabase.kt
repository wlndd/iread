package com.iread.novel.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
