package com.iread.novel.data.files

import java.io.InputStream

interface ImportSource {
    val displayName: String
    val sizeBytes: Long?
    fun open(): InputStream
}
