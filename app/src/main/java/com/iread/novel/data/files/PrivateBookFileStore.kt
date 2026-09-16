package com.iread.novel.data.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException

class StagedBookFile internal constructor(
    val token: String,
    val fingerprint: String,
    val sizeBytes: Long,
)

class StoredBookFile internal constructor(val path: String)

interface BookFileStore {
    fun stage(source: ImportSource): StagedBookFile
    fun open(staged: StagedBookFile): InputStream
    fun finalize(staged: StagedBookFile, bookId: String): StoredBookFile
    fun discard(staged: StagedBookFile)
    fun remove(stored: StoredBookFile)
}

class EmptyImportFileException : IllegalArgumentException("The selected file is empty")
class UnreadableImportSourceException(cause: Throwable) : IOException("Cannot read selected file", cause)
class PrivateStorageException(cause: Throwable) : IOException("Cannot access private book storage", cause)

interface QuarantinedBookFiles {
    fun quarantine(bookId: String)
    fun restore(bookId: String)
    fun purge(bookId: String)
    fun pendingDeletionIds(): List<String>
}

class PrivateBookFileStore(filesDir: File) : BookFileStore, QuarantinedBookFiles {
    private val privateRoot = filesDir.canonicalFile
    private val importingDir = childDirectory("importing")
    private val booksDir = childDirectory("books")
    private val quarantineDir = childDirectory("quarantine")

    override fun quarantine(bookId: String) {
        val source = bookFile(booksDir, bookId)
        ensureDirectory(quarantineDir)
        // Same-volume rename preserves a recoverable copy until Room commits.
        Files.move(source.toPath(), bookFile(quarantineDir, bookId).toPath())
    }

    override fun restore(bookId: String) {
        ensureDirectory(booksDir)
        Files.move(bookFile(quarantineDir, bookId).toPath(), bookFile(booksDir, bookId).toPath())
    }

    override fun purge(bookId: String) {
        Files.deleteIfExists(bookFile(quarantineDir, bookId).toPath())
    }

    override fun pendingDeletionIds(): List<String> {
        if (!quarantineDir.exists()) return emptyList()
        return Files.list(quarantineDir.toPath()).use { paths ->
            paths.map { it.fileName.toString() }.filter { safeStoredName.matches(it) }
                .map { it.removeSuffix(".txt") }.toArray().map { it as String }
        }
    }

    private fun bookFile(directory: File, bookId: String): File {
        require(safeBookId.matches(bookId)) { "Invalid book id" }
        return confinedChild(directory, "$bookId.txt")
    }

    override fun stage(source: ImportSource): StagedBookFile {
        ensureDirectory(importingDir)
        val token = "${UUID.randomUUID()}.part"
        val temporary = confinedChild(importingDir, token)
        val digest = MessageDigest.getInstance("SHA-256")
        var byteCount = 0L

        try {
            val input = try {
                source.open()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw UnreadableImportSourceException(exception)
            }
            input.use { selected ->
                val output = try {
                    Files.newOutputStream(temporary.toPath(), StandardOpenOption.CREATE_NEW)
                } catch (exception: IOException) {
                    throw PrivateStorageException(exception)
                }
                try {
                    output.use { privateCopy ->
                        byteCount = copyAndHash(selected, privateCopy, digest)
                    }
                } catch (exception: UnreadableImportSourceException) {
                    throw exception
                } catch (exception: PrivateStorageException) {
                    throw exception
                } catch (exception: IOException) {
                    throw PrivateStorageException(exception)
                }
            }
            if (byteCount == 0L) throw EmptyImportFileException()
            return StagedBookFile(token, digest.digest().toHex(), byteCount)
        } catch (exception: Exception) {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw exception
        }
    }

    override fun open(staged: StagedBookFile): InputStream = try {
        Files.newInputStream(stagedFile(staged).toPath())
    } catch (exception: IOException) {
        throw PrivateStorageException(exception)
    }

    override fun finalize(staged: StagedBookFile, bookId: String): StoredBookFile {
        require(safeBookId.matches(bookId)) { "Invalid book id" }
        ensureDirectory(booksDir)
        val temporary = stagedFile(staged)
        val destination = confinedChild(booksDir, "$bookId.txt")
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            try {
                Files.move(temporary.toPath(), destination.toPath())
            } catch (exception: IOException) {
                throw PrivateStorageException(exception)
            }
        } catch (exception: IOException) {
            throw PrivateStorageException(exception)
        }
        return StoredBookFile(destination.path)
    }

    override fun discard(staged: StagedBookFile) {
        try {
            Files.deleteIfExists(stagedFile(staged).toPath())
        } catch (exception: IOException) {
            throw PrivateStorageException(exception)
        }
    }

    override fun remove(stored: StoredBookFile) {
        val candidate = File(stored.path).canonicalFile
        require(candidate.parentFile == booksDir) { "Stored book is outside private storage" }
        require(safeStoredName.matches(candidate.name)) { "Invalid stored book name" }
        try {
            Files.deleteIfExists(candidate.toPath())
        } catch (exception: IOException) {
            throw PrivateStorageException(exception)
        }
    }

    private fun copyAndHash(input: InputStream, output: OutputStream, digest: MessageDigest): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = try {
                input.read(buffer)
            } catch (exception: IOException) {
                throw UnreadableImportSourceException(exception)
            }
            if (read < 0) return total
            if (read == 0) continue
            try {
                output.write(buffer, 0, read)
            } catch (exception: IOException) {
                throw PrivateStorageException(exception)
            }
            digest.update(buffer, 0, read)
            total += read
        }
    }

    private fun stagedFile(staged: StagedBookFile): File {
        require(stagedToken.matches(staged.token)) { "Invalid staged file token" }
        return confinedChild(importingDir, staged.token)
    }

    private fun childDirectory(name: String): File = confinedChild(privateRoot, name)

    private fun confinedChild(parent: File, name: String): File {
        val child = File(parent, name).canonicalFile
        require(child.parentFile == parent) { "Path escapes private storage" }
        return child
    }

    private fun ensureDirectory(directory: File) {
        try {
            Files.createDirectories(directory.toPath())
        } catch (exception: IOException) {
            throw PrivateStorageException(exception)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val stagedToken = Regex("[0-9a-fA-F-]{36}\\.part")
        val safeBookId = Regex("[A-Za-z0-9_-]+")
        val safeStoredName = Regex("[A-Za-z0-9_-]+\\.txt")
    }
}
