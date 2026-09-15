package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ParsedBook
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtBookParserTest {
    private val metadata = BookMetadata("雾隐长安", "林渡")

    @Test fun splitsCommonChineseChapterHeadings() {
        val source = "序言\n风起。\n第十二章 城门夜雨\n雨落长街。\n第13回 故人\n灯火未眠。"
        val parsed = TxtBookParser().parse(metadata) { source.byteInputStream() }
        assertEquals(
            ParsedBook(
                metadata,
                listOf(
                    Chapter(0, "序章", "序言\n风起。"),
                    Chapter(1, "第十二章 城门夜雨", "雨落长街。"),
                    Chapter(2, "第13回 故人", "灯火未眠。"),
                ),
            ),
            parsed,
        )
    }

    @Test fun treatsUnsectionedTextAsOneChapter() {
        val parsed = TxtBookParser().parse(metadata) { "只有一段正文。".byteInputStream() }
        assertEquals(
            ParsedBook(metadata, listOf(Chapter(0, "正文", "只有一段正文。"))),
            parsed,
        )
    }

    @Test fun discardsBlankSectionsAroundConsecutiveHeadings() {
        val source = "第一章 空章\n第二章 实章\n正文\n第三章 空章"
        val parsed = TxtBookParser().parse(metadata) { source.byteInputStream() }
        assertEquals(
            ParsedBook(metadata, listOf(Chapter(0, "第二章 实章", "正文"))),
            parsed,
        )
    }

    @Test fun anchorsHeadingsAndHonorsFortyCharacterSuffixLimit() {
        val suffix40 = "甲".repeat(40)
        val suffix41 = "乙".repeat(41)
        val lookalike = "前缀第3章 假标题"
        val source = "引子\n第1章$suffix40\n正文一\n$lookalike\n第2章$suffix41\n正文二"

        val parsed = TxtBookParser().parse(metadata) { source.byteInputStream() }

        assertEquals(
            ParsedBook(
                metadata,
                listOf(
                    Chapter(0, "序章", "引子"),
                    Chapter(
                        1,
                        "第1章$suffix40",
                        "正文一\n$lookalike\n第2章$suffix41\n正文二",
                    ),
                ),
            ),
            parsed,
        )
    }

    @Test fun closesTheOpenedStream() {
        val stream = CloseTrackingInputStream("正文".toByteArray())

        TxtBookParser().parse(metadata) { stream }

        assertTrue(stream.closed)
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
