package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ParsedBook
import java.io.InputStream

class TxtBookParser : BookParser {
    override fun parse(metadata: BookMetadata, openStream: () -> InputStream): ParsedBook {
        val text = openStream().use { stream -> TxtDecoder.decode(stream.readBytes()) }
        val chapters = mutableListOf<Chapter>()
        val bodyLines = mutableListOf<String>()
        var currentTitle: String? = null

        fun appendChapter(title: String) {
            val body = bodyLines.joinToString("\n").trim()
            if (body.isNotBlank()) {
                chapters += Chapter(chapters.size, title, body)
            }
            bodyLines.clear()
        }

        text.split('\n').forEach { line ->
            if (chapterHeading.matches(line)) {
                appendChapter(currentTitle ?: "序章")
                currentTitle = line.trim()
            } else {
                bodyLines += line
            }
        }

        appendChapter(currentTitle ?: "正文")
        return ParsedBook(metadata, chapters)
    }

    private companion object {
        private val chapterHeading = Regex(
            pattern = "^\\s*第[0-9零一二三四五六七八九十百千万两]+[章回节卷部篇][^\\n]{0,40}$",
        )
    }
}
