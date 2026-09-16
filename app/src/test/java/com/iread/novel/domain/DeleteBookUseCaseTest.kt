package com.iread.novel.domain

import com.iread.novel.core.model.*
import com.iread.novel.data.files.*
import com.iread.novel.data.repository.BookRepository
import com.iread.novel.testutil.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeleteBookUseCaseTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(): PrivateBookFileStore = PrivateBookFileStore(temporary.root).also {
        it.finalize(it.stage(ByteArrayImportSource("书.txt", "原文".toByteArray())), "book")
    }
    private fun repository() = FakeBookRepository(initialContent = listOf(BookContent("book", "书", "作者", emptyList())))

    @Test fun removesPrivateCopyOnlyAfterDatabaseSuccess() = runTest {
        val files = store()
        val repository = repository()
        DeleteBookUseCase(repository, files)("book")
        assertNull(repository.loadBook("book"))
        assertFalse(File(temporary.root, "books/book.txt").exists())
        assertTrue(files.pendingDeletionIds().isEmpty())
    }

    @Test fun restoresCopyWhenDatabaseDeletionFails() = runTest {
        val files = store()
        val repository = object : BookRepository by repository() {
            override suspend fun deleteBook(bookId: String) { throw IOException("database unavailable") }
        }
        try { DeleteBookUseCase(repository, files)("book"); fail("Expected failure") } catch (_: IOException) { }
        assertEquals("原文", File(temporary.root, "books/book.txt").readText())
        assertNotNull(repository.loadBook("book"))
        assertTrue(files.pendingDeletionIds().isEmpty())
    }

    @Test fun moveFailureKeepsDatabaseRecord() = runTest {
        val files = store()
        File(temporary.root, "quarantine").writeText("blocked")
        val repository = repository()
        try { DeleteBookUseCase(repository, files)("book"); fail("Expected failure") } catch (_: IOException) { }
        assertNotNull(repository.loadBook("book"))
        assertEquals("原文", File(temporary.root, "books/book.txt").readText())
    }

    @Test fun startupRecoveryRestoresUncommittedDeletionAndPurgesCommittedDeletion() = runTest {
        val files = store()
        files.quarantine("book")
        val repository = repository()
        DeleteBookUseCase(repository, files).recover()
        assertEquals("原文", File(temporary.root, "books/book.txt").readText())
        files.quarantine("book")
        repository.deleteBook("book")
        DeleteBookUseCase(repository, files).recover()
        assertTrue(files.pendingDeletionIds().isEmpty())
        assertFalse(File(temporary.root, "books/book.txt").exists())
    }

    @Test fun rejectsIdsThatEscapePrivateStorage() = runTest {
        val files = store()
        try { files.quarantine("../original"); fail("Expected confinement failure") } catch (_: IllegalArgumentException) { }
        assertEquals("原文", File(temporary.root, "books/book.txt").readText())
    }
}
