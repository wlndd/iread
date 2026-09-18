package com.iread.novel.domain

import com.iread.novel.core.model.ImportedBook
import com.iread.novel.core.parser.BookMetadataParser
import com.iread.novel.core.parser.BookParser
import com.iread.novel.core.parser.EpubBookParser
import com.iread.novel.core.parser.UnsupportedTextEncodingException
import com.iread.novel.data.files.BookFileStore
import com.iread.novel.data.files.EmptyImportFileException
import com.iread.novel.data.files.ImportSource
import com.iread.novel.data.files.PrivateStorageException
import com.iread.novel.data.files.StagedBookFile
import com.iread.novel.data.files.StoredBookFile
import com.iread.novel.data.files.UnreadableImportSourceException
import com.iread.novel.data.repository.BookRepository
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException

fun interface TimeSource {
    fun nowMillis(): Long
}

sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult
    data object Duplicate : ImportResult
    data class Failed(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    EMPTY_FILE,
    UNSUPPORTED_FORMAT,
    UNREADABLE_FILE,
    UNKNOWN_ENCODING,
    NO_STORAGE,
    INVALID_EPUB,
}

class ImportTxtBookUseCase(
    private val repository: BookRepository,
    private val files: BookFileStore,
    private val parser: BookParser,
    private val timeSource: TimeSource,
) {
    suspend operator fun invoke(source: ImportSource): ImportResult {
        val isEpub = source.displayName.endsWith(".epub", ignoreCase = true)
        if (!isEpub && !source.displayName.endsWith(".txt", ignoreCase = true)) {
            return ImportResult.Failed(ImportFailure.UNSUPPORTED_FORMAT)
        }

        var stagedForCleanup: StagedBookFile? = null
        var storedForCleanup: StoredBookFile? = null
        return try {
            val staged = files.stage(source)
            stagedForCleanup = staged
            if (repository.hasFingerprint(staged.fingerprint)) {
                files.discard(staged)
                stagedForCleanup = null
                return ImportResult.Duplicate
            }

            val metadata = BookMetadataParser.parse(source.displayName)
            val parsed = if (isEpub) {
                try {
                    EpubBookParser().parse(metadata) { files.open(staged) }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: PrivateStorageException) {
                    throw exception
                } catch (exception: Exception) {
                    throw InvalidEpubImportException(exception)
                }
            } else parser.parse(metadata) { files.open(staged) }
            if (parsed.chapters.isEmpty() || parsed.chapters.all { it.body.isBlank() }) throw EmptyImportFileException()
            val bookId = UUID.randomUUID().toString()
            val stored = files.finalize(staged, bookId)
            stagedForCleanup = null
            storedForCleanup = stored
            val importedBook = ImportedBook(
                id = bookId,
                metadata = parsed.metadata,
                chapters = parsed.chapters,
                sourcePath = stored.path,
                fingerprint = staged.fingerprint,
                importedAt = timeSource.nowMillis(),
                sourceUri = source.sourceUri,
                format = if (isEpub) com.iread.novel.core.model.BookFormat.EPUB else com.iread.novel.core.model.BookFormat.TXT,
            )
            try {
                repository.insertImportedBook(importedBook)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw ImportPersistenceException(exception)
            }
            storedForCleanup = null
            ImportResult.Imported(bookId)
        } catch (exception: CancellationException) {
            stagedForCleanup?.let { runCatching { files.discard(it) } }
            storedForCleanup?.let { runCatching { files.remove(it) } }
            throw exception
        } catch (exception: Exception) {
            stagedForCleanup?.let { runCatching { files.discard(it) } }
            storedForCleanup?.let { runCatching { files.remove(it) } }
            ImportResult.Failed(exception.toImportFailure())
        }
    }

    private fun Exception.toImportFailure(): ImportFailure = when (this) {
        is EmptyImportFileException -> ImportFailure.EMPTY_FILE
        is InvalidEpubImportException -> ImportFailure.INVALID_EPUB
        is UnsupportedTextEncodingException -> ImportFailure.UNKNOWN_ENCODING
        is UnreadableImportSourceException -> ImportFailure.UNREADABLE_FILE
        is PrivateStorageException, is ImportPersistenceException -> ImportFailure.NO_STORAGE
        is IOException -> ImportFailure.UNREADABLE_FILE
        else -> ImportFailure.UNREADABLE_FILE
    }
}

private class ImportPersistenceException(cause: Throwable) : Exception(cause)
private class InvalidEpubImportException(cause: Throwable) : Exception(cause)
