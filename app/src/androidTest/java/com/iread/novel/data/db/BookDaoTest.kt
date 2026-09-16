package com.iread.novel.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var database: IReadDatabase
    private lateinit var dao: BookDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IReadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.bookDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun deletingBookCascadesChaptersAndProgress() = runTest {
        dao.insertBook(BookEntity("book-1", "雾隐长安", "林渡", "TXT", "books/book-1.txt", "abc", 1, 1L, null))
        dao.insertChapters(listOf(ChapterEntity("book-1", 0, "第一章", "正文")))
        dao.upsertProgress(ReadingProgressEntity("book-1", 0, 2, -1, 2L))

        dao.deleteBook("book-1")

        assertTrue(dao.getChapters("book-1").isEmpty())
        assertNull(dao.getProgress("book-1"))
    }

    @Test
    fun observedRowReportsRemainingUnreadChapters() = runTest {
        dao.insertBook(BookEntity("book-2", "长夜行", "未知作者", "TXT", "books/book-2.txt", "def", 3, 1L, null))
        dao.insertChapters((0..2).map { ChapterEntity("book-2", it, "第${it + 1}章", "正文") })
        dao.upsertProgress(ReadingProgressEntity("book-2", 1, 0, 0, 2L))

        val row = dao.observeBookRows().first().single()

        assertEquals(2, row.unreadChapters)
    }
}
