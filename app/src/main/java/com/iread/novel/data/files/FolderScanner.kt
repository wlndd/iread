package com.iread.novel.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class FolderScanResult(val uris: List<Uri>, val warnings: List<String>)
data class ScanDocument(val id: String, val name: String, val directory: Boolean, val mimeType: String? = null)
data class ScannedDocuments(val documents: List<ScanDocument>, val warnings: List<String>)

/** Independent traversal policy keeps scanning within the explicitly granted tree. */
class FolderTraversal(private val children: suspend (String) -> List<ScanDocument>) {
    suspend fun scan(root: String): ScannedDocuments {
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(root to 0)
        val visited = mutableSetOf<String>()
        val files = mutableListOf<ScanDocument>()
        val warnings = mutableListOf<String>()
        var count = 0
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, depth) = queue.removeFirst()
            if (!visited.add(id)) continue
            if (depth > 32) { warnings += "部分子文件夹层级过深，已跳过"; continue }
            val found = try { children(id) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) {
                if (id == root) throw error
                warnings += "部分文件夹无法读取，请检查权限"; continue
            }
            for (doc in found) {
                currentCoroutineContext().ensureActive()
                if (++count > 10000 || files.size >= 2000) {
                    warnings += "本次扫描已达到上限，请按子文件夹分批导入"
                    return ScannedDocuments(files, warnings.distinct())
                }
                if (doc.directory) queue.add(doc.id to depth + 1)
                else if (isSupportedBookDocument(doc.name, doc.mimeType) && visited.add(doc.id)) files += doc
            }
        }
        return ScannedDocuments(files, warnings.distinct())
    }
}

class FolderScanner(private val resolver: ContentResolver) {
    suspend fun scan(treeUri: Uri): FolderScanResult {
        require(treeUri.scheme == "content" && DocumentsContract.isTreeUri(treeUri))
        val root = DocumentsContract.getTreeDocumentId(treeUri)
        val result = FolderTraversal { id ->
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val result = mutableListOf<ScanDocument>()
            (resolver.query(childrenUri, columns, null, null, null) ?: error("Cannot query selected directory")).use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (result.size >= 10001) break
                    val mimeType = cursor.getString(2)
                    result += ScanDocument(cursor.getString(0), cursor.getString(1).orEmpty(),
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR, mimeType)
                }
            }
            result
        }.scan(root)
        return FolderScanResult(result.documents.map { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id) }, result.warnings)
    }
}

internal fun isSupportedBookDocument(name: String, mimeType: String?): Boolean =
    name.endsWith(".txt", true) || name.endsWith(".epub", true) || bookExtensionForMime(mimeType) != null

internal fun normalizedBookDisplayName(name: String, mimeType: String?): String =
    if (name.endsWith(".txt", true) || name.endsWith(".epub", true)) name
    else bookExtensionForMime(mimeType)?.let(name::plus) ?: name

private fun bookExtensionForMime(mimeType: String?): String? = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
    "text/plain" -> ".txt"
    "application/epub+zip", "application/x-epub+zip" -> ".epub"
    else -> null
}
