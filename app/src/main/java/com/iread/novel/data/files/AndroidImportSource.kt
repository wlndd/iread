package com.iread.novel.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

class AndroidImportSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : ImportSource {
    private val metadata = queryMetadata()

    override val displayName: String = normalizedBookDisplayName(
        metadata.first ?: uri.lastPathSegment ?: "未命名.txt",
        runCatching { contentResolver.getType(uri) }.getOrNull(),
    )
    override val sizeBytes: Long? = metadata.second
    override val sourceUri: String = uri.toString()

    override fun open(): InputStream = contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("The selected document cannot be opened")

    private fun queryMetadata(): Pair<String?, Long?> = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
            val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            name to size
        } ?: (null to null)
    }.getOrDefault(null to null)
}
