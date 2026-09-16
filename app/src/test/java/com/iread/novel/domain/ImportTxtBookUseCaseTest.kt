package com.iread.novel.domain

import com.iread.novel.core.parser.TxtBookParser
import com.iread.novel.data.files.ImportSource
import com.iread.novel.data.files.PrivateBookFileStore
import com.iread.novel.testutil.ByteArrayImportSource
import com.iread.novel.testutil.FakeBookRepository
import com.iread.novel.testutil.FakePrivateBookFileStore
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTxtBookUseCaseTest {
    @Test
    fun propagatesCancellationFromSelectedSourceOpen() = runTest {
        val filesDir = Files.createTempDirectory("iread-use-case-cancelled-open").toFile()
        val source = object : ImportSource {
            override val displayName = "cancelled.txt"
            override val sizeBytes: Long? = null
            override fun open(): java.io.InputStream = throw CancellationException("cancelled")
        }
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            PrivateBookFileStore(filesDir),
            TxtBookParser(),
            TimeSource { 10L },
        )
        var cancellationPropagated = false

        try {
            useCase(source)
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
        assertTrue(java.io.File(filesDir, "importing").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun importsCopyAndParsedChapters() = runTest {
        val repository = FakeBookRepository()
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource { 10L })
        val source = ByteArrayImportSource(
            "雾隐长安 - 林渡.txt",
            "第一章 雨\n正文".toByteArray(),
        )

        val result = useCase(source)

        assertTrue(result is ImportResult.Imported)
        assertEquals("雾隐长安", repository.saved.single().metadata.title)
        assertEquals("林渡", repository.saved.single().metadata.author)
        assertEquals(1, repository.saved.single().chapters.size)
        assertEquals(10L, repository.saved.single().importedAt)
        assertEquals(1, files.finalized.size)
        assertEquals(1, source.openCount)
    }

    @Test
    fun rejectsARepeatedFingerprintWithoutSecondRecord() = runTest {
        val repository = FakeBookRepository(existingFingerprints = mutableSetOf("same"))
        val files = FakePrivateBookFileStore(fingerprint = "same")
        val useCase = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource { 10L })

        val result = useCase(ByteArrayImportSource("重复.txt", "正文".toByteArray()))

        assertEquals(ImportResult.Duplicate, result)
        assertTrue(repository.saved.isEmpty())
        assertEquals(1, files.discardedTemporaryCopies)
        assertTrue(files.finalized.isEmpty())
    }

    @Test
    fun rejectsUnsupportedFormatWithoutOpeningSource() = runTest {
        val source = ByteArrayImportSource("不是文本.epub", "正文".toByteArray())
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            files,
            TxtBookParser(),
            TimeSource { 10L },
        )

        val result = useCase(source)

        assertEquals(ImportResult.Failed(ImportFailure.UNSUPPORTED_FORMAT), result)
        assertEquals(0, source.openCount)
        assertTrue(files.finalized.isEmpty())
    }

    @Test
    fun removesFinalizedCopyWhenDatabaseInsertionFails() = runTest {
        val repository = FakeBookRepository(insertFailure = IllegalStateException("database full"))
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource { 10L })

        val result = useCase(ByteArrayImportSource("失败.txt", "正文".toByteArray()))

        assertEquals(ImportResult.Failed(ImportFailure.NO_STORAGE), result)
        assertTrue(repository.saved.isEmpty())
        assertEquals(1, files.removedFinalized.size)
        assertTrue(files.finalized.isEmpty())
    }

    @Test
    fun mapsEmptyFileWithoutLeavingTemporaryCopy() = runTest {
        val filesDir = Files.createTempDirectory("iread-use-case-empty").toFile()
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            PrivateBookFileStore(filesDir),
            TxtBookParser(),
            TimeSource { 10L },
        )

        val result = useCase(ByteArrayImportSource("empty.txt", byteArrayOf()))

        assertEquals(ImportResult.Failed(ImportFailure.EMPTY_FILE), result)
        assertTrue(java.io.File(filesDir, "importing").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun mapsUnknownEncodingAndDiscardsStagedCopy() = runTest {
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            files,
            TxtBookParser(),
            TimeSource { 10L },
        )

        val result = useCase(ByteArrayImportSource("unknown.txt", byteArrayOf(0x81.toByte())))

        assertEquals(ImportResult.Failed(ImportFailure.UNKNOWN_ENCODING), result)
        assertEquals(1, files.discardedTemporaryCopies)
        assertTrue(files.finalized.isEmpty())
    }

    @Test
    fun mapsSourceOpenFailureWithoutExposingException() = runTest {
        val filesDir = Files.createTempDirectory("iread-use-case-unreadable").toFile()
        val source = object : ImportSource {
            override val displayName = "unreadable.txt"
            override val sizeBytes: Long? = null
            override fun open(): java.io.InputStream = throw IOException("secret provider detail")
        }
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            PrivateBookFileStore(filesDir),
            TxtBookParser(),
            TimeSource { 10L },
        )

        val result = useCase(source)

        assertEquals(ImportResult.Failed(ImportFailure.UNREADABLE_FILE), result)
    }

    @Test
    fun propagatesCoroutineCancellation() = runTest {
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(insertFailure = CancellationException("cancelled")),
            FakePrivateBookFileStore(),
            TxtBookParser(),
            TimeSource { 10L },
        )
        var cancellationPropagated = false

        try {
            useCase(ByteArrayImportSource("cancelled.txt", "正文".toByteArray()))
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
    }

    @Test
    fun removesFinalizedCopyWhenPostFinalizeWorkFails() = runTest {
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(
            FakeBookRepository(),
            files,
            TxtBookParser(),
            TimeSource { throw IllegalStateException("clock failed") },
        )

        val result = useCase(ByteArrayImportSource("clock.txt", "正文".toByteArray()))

        assertTrue(result is ImportResult.Failed)
        assertEquals(1, files.removedFinalized.size)
        assertTrue(files.finalized.isEmpty())
    }
}
