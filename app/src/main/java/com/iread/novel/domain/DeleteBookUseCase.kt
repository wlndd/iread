package com.iread.novel.domain

import com.iread.novel.data.files.QuarantinedBookFiles
import com.iread.novel.data.repository.BookRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DeleteBookUseCase(private val repository: BookRepository, private val files: QuarantinedBookFiles) {
    private val mutex = Mutex()

    suspend operator fun invoke(bookId: String) = mutex.withLock {
        // Navigation cancellation must not interrupt the file/database transaction.
        // Process death is recovered by reconciling quarantine with Room on launch.
        withContext(NonCancellable) {
            recoverPending()
            files.quarantine(bookId)
            try { repository.deleteBook(bookId) } catch (failure: Exception) {
                try { files.restore(bookId) } catch (restoreFailure: Exception) { failure.addSuppressed(restoreFailure) }
                throw failure
            }
            files.purge(bookId)
        }
    }

    suspend fun recover() = mutex.withLock { withContext(NonCancellable) { recoverPending() } }

    private suspend fun recoverPending() {
        for (id in files.pendingDeletionIds()) {
            if (repository.loadBook(id) != null) files.restore(id) else files.purge(id)
        }
    }
}
