package com.iread.novel.core.parser

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class UnsupportedTextEncodingException(cause: Throwable) :
    IllegalArgumentException("Text is neither valid UTF-8 nor GB18030", cause)

object TxtDecoder {
    private val gb18030 = Charset.forName("GB18030")

    fun decode(bytes: ByteArray): String {
        val decoded = try {
            decodeStrict(bytes, Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            try {
                decodeStrict(bytes, gb18030)
            } catch (exception: CharacterCodingException) {
                throw UnsupportedTextEncodingException(exception)
            }
        }

        return decoded
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String = charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}
