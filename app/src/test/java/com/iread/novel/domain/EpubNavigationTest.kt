package com.iread.novel.domain

import com.iread.novel.core.model.BookMetadata
import com.iread.novel.core.parser.EpubBookParser
import com.iread.novel.core.parser.InvalidEpubException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubNavigationTest {
    private fun archive(nav: Boolean = true, edit: (MutableMap<String, String>) -> Unit = {}): ByteArray {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container><rootfile full-path="OPS/book.opf"/></container>""",
            "OPS/book.opf" to """<package><metadata><title>Book</title><meta name="cover" content="cover"/></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" properties="nav"/><item id="ncx" href="toc.ncx"/><item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/></manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>""",
            "OPS/chapter.xhtml" to """<html><body><p id="one">First &ldquo;story&rdquo;&mdash;A&nbsp;B</p><p id="two">Second &copy; story</p></body></html>""",
            "OPS/nav.xhtml" to """<html xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml#two">Second title</a></li><li><a href="chapter.xhtml#one">First title</a></li></ol></nav></body></html>""",
            "OPS/toc.ncx" to """<ncx><navMap><navPoint><navLabel><text>NCX first</text></navLabel><content src="chapter.xhtml#one"/></navPoint><navPoint><navLabel><text>NCX second</text></navLabel><content src="chapter.xhtml#two"/></navPoint></navMap></ncx>""",
            "OPS/cover.png" to "cover bytes",
        )
        if (!nav) entries["OPS/book.opf"] = entries.getValue("OPS/book.opf").replace("properties=\"nav\"", "")
        edit(entries)
        return ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        } }.toByteArray()
    }

    @Test fun navigationFragmentsFollowDocumentOrderAndPreserveAllText() {
        val chapters = EpubBookParser().parse(BookMetadata("Fallback", "Unknown")) { archive().inputStream() }.chapters
        assertEquals(listOf("First title", "Second title"), chapters.map { it.title })
        assertEquals("First “story”—A B", chapters[0].body)
        assertEquals("Second © story", chapters[1].body)
    }

    @Test fun epub2NcxSuppliesFragmentTitles() {
        val chapters = EpubBookParser().parse(BookMetadata("Fallback", "Unknown")) { archive(false).inputStream() }.chapters
        assertEquals(listOf("NCX first", "NCX second"), chapters.map { it.title })
    }

    @Test fun embeddedCoverSupportsEpub3AndEpub2Metadata() {
        assertArrayEquals("cover bytes".toByteArray(), EpubBookParser().readCover { archive().inputStream() })
        val legacy = archive { it["OPS/book.opf"] = it.getValue("OPS/book.opf").replace("properties=\"cover-image\"", "") }
        assertArrayEquals("cover bytes".toByteArray(), EpubBookParser().readCover { legacy.inputStream() })
    }

    @Test(expected = InvalidEpubException::class) fun rejectsExternalNavigationTargets() {
        val bytes = archive { it["OPS/nav.xhtml"] = it.getValue("OPS/nav.xhtml").replace("chapter.xhtml#one", "https://example.org/book") }
        EpubBookParser().parse(BookMetadata("Fallback", "Unknown")) { bytes.inputStream() }
    }

    @Test(expected = InvalidEpubException::class) fun rejectsEncodedCoverEscape() {
        val bytes = archive { it["OPS/book.opf"] = it.getValue("OPS/book.opf").replace("cover.png", "%2e%2e/%2e%2e/secret.png") }
        EpubBookParser().readCover { bytes.inputStream() }
    }

    @Test(expected = InvalidEpubException::class) fun rejectsInternalEntityDeclarationsEvenInUtf16() {
        val bytes = archive { it["OPS/chapter.xhtml"] = """<!DOCTYPE html [<!ENTITY x SYSTEM "file:///secret">]><html><body>&x;</body></html>""" }
        EpubBookParser().parse(BookMetadata("Fallback", "Unknown")) { bytes.inputStream() }
    }
}
