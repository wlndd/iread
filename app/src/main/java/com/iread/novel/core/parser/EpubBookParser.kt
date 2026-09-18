package com.iread.novel.core.parser

import com.iread.novel.core.model.BookMetadata
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ParsedBook
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

class InvalidEpubException : IllegalArgumentException("Invalid or unsupported EPUB")

/** Text-only EPUB reader. Archive entries are never extracted onto the filesystem. */
class EpubBookParser : BookParser {
    override fun parse(metadata: BookMetadata, openStream: () -> InputStream): ParsedBook {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        openStream().use { input -> ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > 4096) invalid()
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith('/') || '\\' in name || name.split('/').any { it == ".." } || entries.containsKey(name)) invalid()
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    if (out.size().toLong() + read > 8 * 1024 * 1024 || total > 32 * 1024 * 1024) invalid()
                    out.write(buffer, 0, read)
                }
                entries[name] = out.toByteArray()
            }
        } }
        if (entries["mimetype"]?.toString(Charsets.US_ASCII)?.trim() != "application/epub+zip") invalid()
        if (entries.containsKey("META-INF/encryption.xml")) invalid()
        fun xml(path: String): Element = parseXml(entries[path] ?: invalid())
        val container = xml("META-INF/container.xml")
        val opfPath = resolve("", container.elements("rootfile").firstOrNull()?.getAttribute("full-path") ?: invalid())
        val opf = xml(opfPath)
        val bookMetadata = opf.elements("metadata").firstOrNull() ?: invalid()
        val title = bookMetadata.elements("title").firstOrNull()?.textContent?.trim().orEmpty()
        val author = bookMetadata.elements("creator").map { it.textContent.trim() }.filter { it.isNotBlank() }.joinToString("、")
        val manifest = opf.elements("manifest").firstOrNull()?.elements("item")?.associateBy { it.getAttribute("id") } ?: invalid()
        val spine = opf.elements("spine").firstOrNull()?.elements("itemref") ?: invalid()
        val chapters = mutableListOf<Chapter>()
        for (ref in spine) {
            if (ref.getAttribute("linear") == "no") continue
            val item = manifest[ref.getAttribute("idref")] ?: invalid()
            if (item.getAttribute("media-type") !in setOf("application/xhtml+xml", "text/html")) invalid()
            val root = xml(resolve(opfPath, item.getAttribute("href")))
            val body = root.elements("body").firstOrNull() ?: invalid()
            val text = body.readableText()
            if (text.isBlank()) continue
            val heading = body.elements("h1").firstOrNull() ?: body.elements("h2").firstOrNull()
            val chapterTitle = heading?.textContent?.trim()?.takeIf { it.isNotBlank() }
                ?: root.elements("title").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }
                ?: "第 ${chapters.size + 1} 章"
            chapters += Chapter(chapters.size, chapterTitle, text)
        }
        if (chapters.isEmpty()) invalid()
        return ParsedBook(BookMetadata(title.ifBlank { metadata.title }, author.ifBlank { metadata.author }), chapters)
    }

    private fun resolve(base: String, reference: String): String {
        if (reference.isBlank() || '\\' in reference) invalid()
        val uri = URI(null, null, base, null).resolve(reference).normalize()
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) invalid()
        val path = uri.path ?: invalid()
        if (path.startsWith('/') || '\\' in path || path.split('/').any { it == ".." } || path.isBlank()) invalid()
        return path
    }

    private fun parseXml(bytes: ByteArray): Element {
        // EPUB XML is UTF-8 or UTF-16. Decode before screening declarations, so
        // UTF-16 cannot bypass the entity check. No DTD is ever given to a parser.
        val utf16 = bytes.size >= 2 && ((bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) ||
            (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()))
        val charset = when {
            utf16 -> Charsets.UTF_16
            bytes.size >= 2 && bytes[0] == 0.toByte() && bytes[1] == 60.toByte() -> Charsets.UTF_16BE
            bytes.size >= 2 && bytes[0] == 60.toByte() && bytes[1] == 0.toByte() -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }
        var text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        if (Regex("<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(text)) invalid()
        text = text.replace(Regex("<!DOCTYPE\\s+[^\\[>]*>", RegexOption.IGNORE_CASE), "")
        if (Regex("<!DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(text)) invalid()
        text = text.replace("&nbsp;", "&#160;")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder.parse(InputSource(StringReader(text))).documentElement ?: invalid()
    }

    private fun Element.elements(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.readableText(): String {
        val output = StringBuilder()
        fun visit(node: Node, depth: Int) {
            if (depth > 128) invalid()
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                output.append(node.nodeValue.replace(Regex("\\s+"), " "))
                return
            }
            val tag = (node.localName ?: node.nodeName).lowercase()
            if (tag in setOf("script", "style", "head", "svg", "noscript")) return
            val block = tag in setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "br", "hr", "tr")
            if (block) output.append('\n')
            val children = node.childNodes
            for (i in 0 until children.length) visit(children.item(i), depth + 1)
            if (block) output.append('\n')
        }
        visit(this, 0)
        return output.toString().lines().map { it.trim().replace('\u00a0', ' ') }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun invalid(): Nothing = throw InvalidEpubException()
}
