package com.iread.novel.data.files

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class StorageScanProgress(val checkedFiles: Long = 0, val foundBooks: Int = 0)
data class StorageScanResult(val files: List<File>, val warnings: List<String>)

/** Enumerate shared storage without loading book contents or imposing a result cap. */
class StorageBookTraversal {
    suspend fun scan(
        roots: List<File>,
        excludedDirectories: List<File> = emptyList(),
        onProgress: (StorageScanProgress) -> Unit = {},
    ): StorageScanResult {
        val visited = mutableSetOf<String>()
        val books = linkedMapOf<String, File>()
        val warnings = linkedSetOf<String>()
        val excludedPaths = excludedDirectories.map { it.canonicalPath }
        var checked = 0L
        fun excluded(path: String) = excludedPaths.any { path == it || path.startsWith(it + File.separator) }
        for (root in roots) {
            val normalizedRoot = try { root.canonicalFile } catch (_: Exception) {
                warnings += "部分存储位置无法读取，扫描结果可能不完整"; continue
            }
            val stack = ArrayDeque<Iterator<File>>()
            stack.addLast(listOf(normalizedRoot).iterator())
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val iterator = stack.last()
                if (!iterator.hasNext()) { stack.removeLast(); continue }
                val file = iterator.next()
                val path = try { file.canonicalPath } catch (_: Exception) {
                    warnings += "部分文件无法读取，已跳过"; continue
                }
                if (excluded(path)) continue
                // Symlinks must not escape a volume or make a traversal cycle.
                if (path != file.absolutePath) continue
                if (file.isDirectory) {
                    if (!visited.add(path)) continue
                    val children = try { file.listFiles() } catch (_: SecurityException) { null }
                    if (children == null) warnings += "部分目录无法读取，扫描结果可能不完整"
                    else stack.addLast(children.iterator())
                } else {
                    checked++
                    if (file.isFile && (file.name.endsWith(".txt", true) || file.name.endsWith(".epub", true))) {
                        if (file.canRead()) books.putIfAbsent(path, file)
                        else warnings += "部分小说文件无法读取，已跳过"
                    }
                    if (checked % 128L == 0L) onProgress(StorageScanProgress(checked, books.size))
                }
            }
        }
        onProgress(StorageScanProgress(checked, books.size))
        return StorageScanResult(books.values.toList(), warnings.toList())
    }
}
