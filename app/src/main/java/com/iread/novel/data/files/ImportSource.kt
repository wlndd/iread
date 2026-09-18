package com.iread.novel.data.files

import java.io.InputStream

interface ImportSource {
    val displayName: String
    val sizeBytes: Long?
    val sourceUri: String? get() = null
    fun open(): InputStream
}
