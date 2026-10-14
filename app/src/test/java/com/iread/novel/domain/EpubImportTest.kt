package com.iread.novel.domain

import com.iread.novel.core.parser.TxtBookParser
import com.iread.novel.data.files.PrivateBookFileStore
import com.iread.novel.testutil.ByteArrayImportSource
import com.iread.novel.testutil.FakeBookRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubImportTest {
    @get:Rule val temp = TemporaryFolder()

    private fun epub(change: (MutableMap<String, String>) -> Unit = {}): ByteArray {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            "OPS/book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>山间来信</dc:title><dc:creator>竹客</dc:creator></metadata><manifest><item id="b" href="Text/second.xhtml" media-type="application/xhtml+xml"/><item id="a" href="Text/%E7%AC%AC%E4%B8%80.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""",
            "OPS/Text/second.xhtml" to "<html><body><h1>第二章</h1><p>月光映水。</p></body></html>",
            "OPS/Text/第一.xhtml" to """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"><html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章</title><style>hidden</style></head><body><h1>第一章</h1><p>山风<em>吹过</em>竹林。</p><p>旅人<br/>翻开书信。&amp;</p><script>hidden</script></body></html>""",
        )
        change(entries)
        return ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip -> entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            } }
        }.toByteArray()
    }

    @Test fun importsMetadataSpineOrderParagraphsAndPreservesPrivateCopy() = runTest {
        val repo = FakeBookRepository()
        val bytes = epub()
        val importer = ImportTxtBookUseCase(repo, PrivateBookFileStore(temp.root), TxtBookParser(), TimeSource { 1L })
        val result = importer(ByteArrayImportSource("fallback.EPUB", bytes))
        assertTrue("EPUB should import: $result", result is ImportResult.Imported)
        val book = repo.saved.single()
        assertEquals("山间来信", book.metadata.title)
        assertEquals("竹客", book.metadata.author)
        assertEquals(listOf("第一章", "第二章"), book.chapters.map { it.title })
        assertTrue(book.chapters[0].body.contains("山风吹过竹林。\n旅人\n翻开书信。&"))
        assertFalse(book.chapters[0].body.contains("hidden"))
        assertArrayEquals(bytes, java.io.File(book.sourcePath).readBytes())
        assertEquals(ImportResult.Duplicate, importer(ByteArrayImportSource("again.epub", bytes)))
    }

    @Test fun rejectsMissingSpineContentAndCleansPrivateStaging() = runTest {
        assertRejected(epub { it.remove("OPS/Text/second.xhtml") })
    }
    @Test fun rejectsEntityDeclarations() = runTest {
        assertRejected(epub { it["OPS/Text/second.xhtml"] = """<!DOCTYPE html [<!ENTITY x SYSTEM "file:///etc/passwd">]><html><body>&x;</body></html>""" })
    }
    @Test fun rejectsEncryptedBooks() = runTest {
        assertRejected(epub { it["META-INF/encryption.xml"] = "<encryption/>" })
    }
    @Test fun rejectsEscapingPaths() = runTest {
        assertRejected(epub { it["META-INF/container.xml"] = """<container><rootfiles><rootfile full-path="../book.opf"/></rootfiles></container>""" })
    }
    @Test fun rejectsOversizedExpandedChapter() = runTest {
        assertRejected(epub { it["OPS/Text/second.xhtml"] = "x".repeat(8 * 1024 * 1024 + 1) })
    }
    private suspend fun assertRejected(bytes: ByteArray) {
        val repo = FakeBookRepository()
        val result = ImportTxtBookUseCase(repo, PrivateBookFileStore(temp.root), TxtBookParser(), TimeSource { 1L })(ByteArrayImportSource("broken.epub", bytes))
        assertEquals(ImportResult.Failed(ImportFailure.INVALID_EPUB), result)
        assertTrue(repo.saved.isEmpty())
        assertTrue(java.io.File(temp.root, "importing").listFiles().orEmpty().isEmpty())
        assertTrue(java.io.File(temp.root, "books").listFiles().orEmpty().isEmpty())
    }
}
