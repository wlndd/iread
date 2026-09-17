package com.iread.novel.testutil

import com.iread.novel.core.model.BookContent
import com.iread.novel.core.model.BookSummary
import com.iread.novel.core.model.ImportedBook
import com.iread.novel.core.model.ReadingProgress
import com.iread.novel.data.files.BookFileStore
import com.iread.novel.data.files.ImportSource
import com.iread.novel.data.files.StagedBookFile
import com.iread.novel.data.files.StoredBookFile
import com.iread.novel.data.repository.BookRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ByteArrayImportSource(
    override val displayName: String,
    private val bytes: ByteArray,
    override val sizeBytes: Long? = bytes.size.toLong(),
) : ImportSource {
    var openCount: Int = 0
        private set

    override fun open(): InputStream {
        openCount += 1
        return ByteArrayInputStream(bytes)
    }
}

class FakePrivateBookFileStore(
    private val fingerprint: String = "fingerprint",
) : BookFileStore {
    private val stagedBytes = mutableMapOf<String, ByteArray>()
    val finalized = mutableListOf<StoredBookFile>()
    val removedFinalized = mutableListOf<StoredBookFile>()
    var discardedTemporaryCopies: Int = 0
        private set

    override fun stage(source: ImportSource): StagedBookFile {
        val bytes = source.open().use(InputStream::readBytes)
        val staged = StagedBookFile("stage-${stagedBytes.size}", fingerprint, bytes.size.toLong())
        stagedBytes[staged.token] = bytes
        return staged
    }

    override fun open(staged: StagedBookFile): InputStream =
        ByteArrayInputStream(checkNotNull(stagedBytes[staged.token]))

    override fun finalize(staged: StagedBookFile, bookId: String): StoredBookFile {
        checkNotNull(stagedBytes.remove(staged.token))
        return StoredBookFile("/private/books/$bookId.txt").also(finalized::add)
    }

    override fun discard(staged: StagedBookFile) {
        stagedBytes.remove(staged.token)
        discardedTemporaryCopies += 1
    }

    override fun remove(stored: StoredBookFile) {
        finalized.remove(stored)
        removedFinalized += stored
    }
}

class FakeBookRepository(
    existingFingerprints: MutableSet<String> = mutableSetOf(),
    val books: MutableStateFlow<List<BookSummary>> = MutableStateFlow(emptyList()),
    progress: ReadingProgress? = null,
    initialContent: List<BookContent> = emptyList(),
    private val insertFailure: Throwable? = null,
    private val saveProgressInterceptor: (suspend (ReadingProgress) -> Unit)? = null,
) : BookRepository {
    private val fingerprints = existingFingerprints
    private val fingerprintByBookId = mutableMapOf<String, String>()
    private val content = initialContent.associateByTo(mutableMapOf()) { it.id }
    private val progressByBook = MutableStateFlow(
        progress?.let { mapOf(it.bookId to it) } ?: emptyMap(),
    )

    val saved = mutableListOf<ImportedBook>()
    val savedProgress = mutableListOf<ReadingProgress>()
    val metadataUpdates = mutableListOf<Triple<String, String, String>>()
    val deletedBookIds = mutableListOf<String>()

    override fun observeBooks(): Flow<List<BookSummary>> = books

    override suspend fun hasFingerprint(fingerprint: String): Boolean = fingerprint in fingerprints

    override suspend fun insertImportedBook(book: ImportedBook) {
        insertFailure?.let { throw it }
        check(fingerprints.add(book.fingerprint)) { "Duplicate fingerprint" }
        fingerprintByBookId[book.id] = book.fingerprint
        saved += book
        content[book.id] = BookContent(
            id = book.id,
            title = book.metadata.title,
            author = book.metadata.author,
            chapters = book.chapters,
        )
        books.value += BookSummary(
            id = book.id,
            title = book.metadata.title,
            author = book.metadata.author,
            unreadChapters = book.chapters.size,
            totalChapters = book.chapters.size,
        )
    }

    override suspend fun loadBook(bookId: String): BookContent? = content[bookId]

    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        progressByBook.map { it[bookId] }

    override suspend fun saveProgress(progress: ReadingProgress) {
        saveProgressInterceptor?.invoke(progress)
        savedProgress += progress
        progressByBook.value = progressByBook.value + (progress.bookId to progress)
    }

    override suspend fun updateMetadata(bookId: String, title: String, author: String) {
        metadataUpdates += Triple(bookId, title, author)
        content[bookId]?.let { content[bookId] = it.copy(title = title, author = author) }
        books.value = books.value.map { summary ->
            if (summary.id == bookId) summary.copy(title = title, author = author) else summary
        }
    }

    override suspend fun deleteBook(bookId: String) {
        deletedBookIds += bookId
        fingerprintByBookId.remove(bookId)?.let(fingerprints::remove)
        content.remove(bookId)
        books.value = books.value.filterNot { it.id == bookId }
        progressByBook.value = progressByBook.value - bookId
    }
}
