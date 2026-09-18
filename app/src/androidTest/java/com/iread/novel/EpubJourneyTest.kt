package com.iread.novel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.data.files.ImportSource
import com.iread.novel.domain.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun epubReadsRestoresProgressAndDeletesWithoutChangingOriginal() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IReadApplication
        assertEquals("com.iread.novel.TestIReadApplication", app.javaClass.name)
        val original = File.createTempFile("epub-journey-", ".epub", app.cacheDir)
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>EPUB山间来信</dc:title><dc:creator>竹客</dc:creator></metadata><manifest><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""",
            "one.xhtml" to """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"><html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章 山风</h1><p>山风吹过竹林。</p></body></html>""",
            "two.xhtml" to "<html><body><h1>第二章 夜雨</h1><p>窗外细雨，灯下读书。</p></body></html>",
        )
        ZipOutputStream(original.outputStream()).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
        val bytes = original.readBytes()
        var scenario: ActivityScenario<MainActivity>? = null
        var bookId: String? = null
        try {
            val source = object : ImportSource {
                override val displayName = "测试.epub"
                override val sizeBytes = original.length()
                override fun open() = original.inputStream()
            }
            val result = runBlocking(Dispatchers.IO) { app.container.importTxtBook(source) }
            assertTrue("EPUB import failed: $result", result is ImportResult.Imported)
            val id = (result as ImportResult.Imported).bookId
            bookId = id
            val entity = runBlocking(Dispatchers.IO) { app.container.database.bookDao().getBook(id)!! }
            assertEquals("EPUB", entity.format)
            assertArrayEquals(bytes, File(entity.sourcePath).readBytes())
            assertEquals(ImportResult.Duplicate, runBlocking(Dispatchers.IO) { app.container.importTxtBook(source) })
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("EPUB山间来信")
            compose.onNodeWithText("竹客 · 2章未读").assertIsDisplayed()
            compose.onNodeWithText("EPUB山间来信").performClick()
            awaitText("第 1 / 2 章")
            compose.onNodeWithText("山风吹过竹林。").assertIsDisplayed()
            compose.onNodeWithContentDescription("下一章").performClick()
            awaitText("第 2 / 2 章")
            compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) {
                app.container.repository.observeProgress(id).first()?.chapterIndex == 1
            } }
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("EPUB山间来信")
            compose.onNodeWithText("EPUB山间来信").performClick()
            awaitText("第 2 / 2 章")
            compose.onNodeWithText("窗外细雨，灯下读书。").assertIsDisplayed()
            scenario.close()
            scenario = null
            runBlocking(Dispatchers.IO) { app.container.deleteBook(id) }
            assertFalse(File(entity.sourcePath).exists())
            assertNull(runBlocking(Dispatchers.IO) { app.container.repository.loadBook(id) })
            assertArrayEquals(bytes, original.readBytes())
        } finally {
            scenario?.close()
            bookId?.let { id -> runBlocking(Dispatchers.IO) {
                if (app.container.repository.loadBook(id) != null) app.container.deleteBook(id)
            } }
            original.delete()
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
