package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import com.iread.novel.core.model.ParsedBook
import java.io.InputStream

interface BookParser {
    fun parse(metadata: BookMetadata, openStream: () -> InputStream): ParsedBook
}
