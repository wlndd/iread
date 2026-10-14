package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataParserTest {
    @Test fun parsesDashSeparatedAuthor() {
        assertEquals(BookMetadata("雾隐长安", "林渡"), BookMetadataParser.parse("雾隐长安 - 林渡.txt"))
    }

    @Test fun parsesUnderscoreSeparatedAuthor() {
        assertEquals(BookMetadata("剑来", "烽火戏诸侯"), BookMetadataParser.parse("剑来_烽火戏诸侯.TXT"))
    }

    @Test fun fallsBackToUnknownAuthor() {
        assertEquals(BookMetadata("长夜行", "未知作者"), BookMetadataParser.parse("长夜行.txt"))
    }
}
