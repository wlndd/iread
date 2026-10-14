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
    private fun readArchive(openStream: () -> InputStream): Map<String, ByteArray> {
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
                    if (out.size().toLong() + read > 8 * 1024 * 1024 || total > 64 * 1024 * 1024) invalid()
                    out.write(buffer, 0, read)
                }
                entries[name] = out.toByteArray()
            }
        } }
        if (entries["mimetype"]?.toString(Charsets.US_ASCII)?.trim() != "application/epub+zip") invalid()
        if (entries.containsKey("META-INF/encryption.xml")) invalid()
        return entries
    }

    fun readCover(openStream: () -> InputStream): ByteArray? {
        val entries = readArchive(openStream)
        val container = parseXml(entries["META-INF/container.xml"] ?: invalid())
        val opfPath = resolve("", container.elements("rootfile").firstOrNull()?.getAttribute("full-path") ?: invalid())
        val opf = parseXml(entries[opfPath] ?: invalid())
        val coverId = opf.elements("meta").firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
        val cover = opf.elements("item").firstOrNull { "cover-image" in it.getAttribute("properties").split(' ') }
            ?: opf.elements("item").firstOrNull { coverId != null && it.getAttribute("id") == coverId }
            ?: return null
        if (cover.getAttribute("media-type") !in setOf("image/jpeg", "image/png", "image/webp")) return null
        return entries[resolve(opfPath, cover.getAttribute("href"))]
    }

    override fun parse(metadata: BookMetadata, openStream: () -> InputStream): ParsedBook {
        val entries = readArchive(openStream)
        fun xml(path: String): Element = parseXml(entries[path] ?: invalid())
        val container = xml("META-INF/container.xml")
        val opfPath = resolve("", container.elements("rootfile").firstOrNull()?.getAttribute("full-path") ?: invalid())
        val opf = xml(opfPath)
        val bookMetadata = opf.elements("metadata").firstOrNull() ?: invalid()
        val title = bookMetadata.elements("title").firstOrNull()?.textContent?.trim().orEmpty()
        val author = bookMetadata.elements("creator").map { it.textContent.trim() }.filter { it.isNotBlank() }.joinToString("、")
        val manifest = opf.elements("manifest").firstOrNull()?.elements("item")?.associateBy { it.getAttribute("id") } ?: invalid()
        val spine = opf.elements("spine").firstOrNull()?.elements("itemref") ?: invalid()
        val labels = linkedMapOf<Pair<String, String>, String>()
        val navItem = manifest.values.firstOrNull { "nav" in it.getAttribute("properties").split(' ') }
        if (navItem != null) {
            val navPath = resolve(opfPath, navItem.getAttribute("href"))
            val navRoot = xml(navPath)
            val toc = navRoot.elements("nav").firstOrNull { "toc" in it.getAttributeNS("http://www.idpf.org/2007/ops", "type").split(' ') }
                ?: navRoot.elements("nav").firstOrNull()
            toc?.elements("a")?.forEach { link ->
                val href = link.getAttribute("href")
                if (href.isNotBlank()) labels[resolve(navPath, href) to URI(href).fragment.orEmpty()] = link.textContent.trim()
            }
        }
        if (labels.isEmpty()) {
            val ncxId = opf.elements("spine").firstOrNull()?.getAttribute("toc")
            val ncxItem = manifest[ncxId] ?: manifest.values.firstOrNull { it.getAttribute("media-type") == "application/x-dtbncx+xml" }
            ncxItem?.let { item ->
                val ncxPath = resolve(opfPath, item.getAttribute("href"))
                xml(ncxPath).elements("navPoint").forEach { point ->
                    val href = point.elements("content").firstOrNull()?.getAttribute("src").orEmpty()
                    val label = point.elements("navLabel").firstOrNull()?.textContent?.trim().orEmpty()
                    if (href.isNotBlank()) labels[resolve(ncxPath, href) to URI(href).fragment.orEmpty()] = label
                }
            }
        }
        val chapters = mutableListOf<Chapter>()
        for (ref in spine) {
            if (ref.getAttribute("linear") == "no") continue
            val item = manifest[ref.getAttribute("idref")] ?: invalid()
            if (item.getAttribute("media-type") !in setOf("application/xhtml+xml", "text/html")) invalid()
            val path = resolve(opfPath, item.getAttribute("href"))
            val root = xml(path)
            val body = root.elements("body").firstOrNull() ?: invalid()
            val extracted = body.readableText()
            val text = extracted.first
            if (text.isBlank()) continue
            val heading = body.elements("h1").firstOrNull() ?: body.elements("h2").firstOrNull()
            val chapterTitle = labels[path to ""]?.takeIf { it.isNotBlank() }
                ?: heading?.textContent?.trim()?.takeIf { it.isNotBlank() }
                ?: root.elements("title").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }
                ?: "第 ${chapters.size + 1} 章"
            val sections = labels.filterKeys { it.first == path && it.second.isNotBlank() }.mapNotNull { (key, title) ->
                extracted.second[key.second]?.let { it to title }
            }.sortedBy { it.first }.distinctBy { it.first }
            if (sections.isEmpty()) chapters += Chapter(chapters.size, chapterTitle, text)
            else {
                val starts = if (sections.first().first > 0) listOf(0 to chapterTitle) + sections else sections
                starts.forEachIndexed { i, (start, title) ->
                    val part = text.substring(start, starts.getOrNull(i + 1)?.first ?: text.length).trim()
                    if (part.isNotBlank()) chapters += Chapter(chapters.size, title.ifBlank { chapterTitle }, part)
                }
            }
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
        val entities = mapOf("nbsp" to 160, "ldquo" to 8220, "rdquo" to 8221, "lsquo" to 8216, "rsquo" to 8217,
            "mdash" to 8212, "ndash" to 8211, "hellip" to 8230, "copy" to 169, "reg" to 174, "trade" to 8482,
            "bull" to 8226, "middot" to 183, "laquo" to 171, "raquo" to 187, "ensp" to 8194, "emsp" to 8195)
        text = text.replace(Regex("&([A-Za-z]+);")) { match -> entities[match.groupValues[1]]?.let { "&#$it;" } ?: match.value }
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

    private fun Element.readableText(): Pair<String, Map<String, Int>> {
        val output = StringBuilder()
        val anchors = linkedMapOf<String, Int>()
        fun visit(node: Node, depth: Int) {
            if (depth > 128) invalid()
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                output.append(node.nodeValue.replace(Regex("\\s+"), " "))
                return
            }
            val tag = (node.localName ?: node.nodeName).lowercase()
            if (tag in setOf("script", "style", "head", "svg", "noscript")) return
            if (node is Element && node.getAttribute("id").isNotBlank()) anchors[node.getAttribute("id")] = output.length
            val block = tag in setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "br", "hr", "tr")
            if (block) output.append('\n')
            val children = node.childNodes
            for (i in 0 until children.length) visit(children.item(i), depth + 1)
            if (block) output.append('\n')
        }
        visit(this, 0)
        val raw = output.toString()
        val cleaned = StringBuilder()
        val mapped = linkedMapOf<String, Int>()
        val sorted = anchors.entries.sortedBy { it.value }
        var nextAnchor = 0
        var rawStart = 0
        // Translate all anchors in one pass, rather than repeatedly copying a long chapter prefix.
        raw.split('\n').forEach { line ->
            val trimmed = line.trim().replace('\u00a0', ' ')
            val leading = line.length - line.trimStart().length
            while (nextAnchor < sorted.size && sorted[nextAnchor].value <= rawStart + line.length) {
                val mark = sorted[nextAnchor++]
                val local = (mark.value - rawStart - leading).coerceIn(0, trimmed.length)
                mapped[mark.key] = cleaned.length + if (local == 0) 0 else local + if (cleaned.isEmpty()) 0 else 1
            }
            if (trimmed.isNotBlank()) {
                if (cleaned.isNotEmpty()) cleaned.append('\n')
                cleaned.append(trimmed)
            }
            rawStart += line.length + 1
        }
        return cleaned.toString() to mapped
    }

    private fun invalid(): Nothing = throw InvalidEpubException()
}
