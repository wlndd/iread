package com.iread.novel.data.files

import com.iread.novel.testutil.ByteArrayImportSource
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivateBookFileStoreTest {
    @Test
    fun propagatesCancellationFromSourceOpenAndRemovesTemporaryCopy() {
        val filesDir = Files.createTempDirectory("iread-files-cancelled").toFile()
        val store = PrivateBookFileStore(filesDir)
        val source = object : ImportSource {
            override val displayName = "cancelled.txt"
            override val sizeBytes: Long? = null
            override fun open(): java.io.InputStream = throw CancellationException("cancelled")
        }

        assertThrows(CancellationException::class.java) {
            store.stage(source)
        }

        assertTrue(java.io.File(filesDir, "importing").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun stagesHashesAndFinalizesOnlyInsidePrivateStorage() {
        val filesDir = Files.createTempDirectory("iread-files").toFile()
        val original = Files.createTempFile("selected", ".txt").toFile()
        val bytes = "第一章 雨\n正文".toByteArray()
        original.writeBytes(bytes)
        val source = object : ImportSource {
            override val displayName = original.name
            override val sizeBytes = original.length()
            override fun open() = original.inputStream()
        }
        val store = PrivateBookFileStore(filesDir)

        val staged = store.stage(source)
        val stored = store.finalize(staged, "book-1")

        assertEquals(sha256(bytes), staged.fingerprint)
        assertEquals(bytes.size.toLong(), staged.sizeBytes)
        assertEquals(java.io.File(filesDir, "books/book-1.txt").canonicalPath, stored.path)
        assertArrayEquals(bytes, java.io.File(stored.path).readBytes())
        assertArrayEquals(bytes, original.readBytes())
        assertTrue(original.exists())
        assertFalse(java.io.File(filesDir, "importing/${staged.token}").exists())
    }

    @Test
    fun rejectsBookIdThatCouldEscapePrivateBooksDirectory() {
        val filesDir = Files.createTempDirectory("iread-files-confined").toFile()
        val store = PrivateBookFileStore(filesDir)
        val staged = store.stage(ByteArrayImportSource("book.txt", "正文".toByteArray()))

        assertThrows(IllegalArgumentException::class.java) {
            store.finalize(staged, "../outside")
        }

        assertFalse(java.io.File(filesDir.parentFile, "outside.txt").exists())
        store.discard(staged)
    }

    @Test
    fun rejectsEmptyInputAndRemovesTemporaryCopy() {
        val filesDir = Files.createTempDirectory("iread-files-empty").toFile()
        val store = PrivateBookFileStore(filesDir)

        val failure = runCatching {
            store.stage(ByteArrayImportSource("empty.txt", byteArrayOf()))
        }.exceptionOrNull()

        assertTrue(failure is EmptyImportFileException)
        assertTrue(java.io.File(filesDir, "importing").listFiles().orEmpty().isEmpty())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
