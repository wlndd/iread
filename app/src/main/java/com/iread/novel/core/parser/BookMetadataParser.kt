package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata

object BookMetadataParser {
    private const val UNKNOWN_AUTHOR = "未知作者"
    private val extension = Regex("(?i)\\.(txt|epub)$")
    private val separator = Regex("\\s+-\\s+|—|－|_")

    fun parse(fileName: String): BookMetadata {
        val displayName = fileName.replace(extension, "").trim()
        val separatorMatch = separator.find(displayName)
            ?: return BookMetadata(displayName, UNKNOWN_AUTHOR)

        val title = displayName.substring(0, separatorMatch.range.first).trim()
        val author = displayName.substring(separatorMatch.range.last + 1).trim()
        return BookMetadata(title, author.ifBlank { UNKNOWN_AUTHOR })
    }
}
