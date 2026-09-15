package com.iread.novel.core.parser

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtDecoderTest {
    @Test fun decodesUtf8() {
        assertEquals("第一章 雨夜", TxtDecoder.decode("第一章 雨夜".toByteArray(Charsets.UTF_8)))
    }

    @Test fun decodesGb18030() {
        val bytes = "第一章 雨夜".toByteArray(Charset.forName("GB18030"))
        assertEquals("第一章 雨夜", TxtDecoder.decode(bytes))
    }
}
