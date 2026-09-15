package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtBookParserTest {
    private val metadata = BookMetadata("雾隐长安", "林渡")

    @Test fun splitsCommonChineseChapterHeadings() {
        val source = "序言\n风起。\n第十二章 城门夜雨\n雨落长街。\n第13回 故人\n灯火未眠。"
        val parsed = TxtBookParser().parse(metadata) { source.byteInputStream() }
        assertEquals(listOf("序章", "第十二章 城门夜雨", "第13回 故人"), parsed.chapters.map { it.title })
    }

    @Test fun treatsUnsectionedTextAsOneChapter() {
        val parsed = TxtBookParser().parse(metadata) { "只有一段正文。".byteInputStream() }
        assertEquals(1, parsed.chapters.size)
        assertEquals("正文", parsed.chapters.single().title)
    }
}
