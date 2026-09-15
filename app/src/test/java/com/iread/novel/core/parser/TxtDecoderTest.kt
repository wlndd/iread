package com.iread.novel.core.parser

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TxtDecoderTest {
    @Test fun decodesUtf8() {
        assertEquals("第一章 雨夜", TxtDecoder.decode("第一章 雨夜".toByteArray(Charsets.UTF_8)))
    }

    @Test fun decodesGb18030() {
        val bytes = "第一章 雨夜".toByteArray(Charset.forName("GB18030"))
        assertEquals("第一章 雨夜", TxtDecoder.decode(bytes))
    }

    @Test fun rejectsBytesInvalidUnderBothSupportedCharsets() {
        assertThrows(UnsupportedTextEncodingException::class.java) {
            TxtDecoder.decode(byteArrayOf(0x81.toByte()))
        }
    }

    @Test fun removesInitialUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "正文".toByteArray(Charsets.UTF_8)
        assertEquals("正文", TxtDecoder.decode(bytes))
    }

    @Test fun normalizesMixedLineEndings() {
        val bytes = "甲\r\n乙\r丙\n丁".toByteArray(Charsets.UTF_8)
        assertEquals("甲\n乙\n丙\n丁", TxtDecoder.decode(bytes))
    }
}
