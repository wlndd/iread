package com.iread.novel

import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.data.files.ImportSource
import com.iread.novel.domain.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

/** Optional user-supplied fixture stays outside the repository and test APK. */
class LargeEpubImportTest {
    @Test fun importsLargeCollectionAndSkipsItsDuplicate() = runBlocking(Dispatchers.IO) {
        val path = InstrumentationRegistry.getArguments().getString("largeEpub")
        assumeNotNull(path)
        val original = File(checkNotNull(path))
        assertTrue(original.isFile)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IReadApplication
        val source = object : ImportSource {
            override val displayName = "合集.epub"
            override val sizeBytes = original.length()
            override fun open() = original.inputStream()
        }
        val result = app.container.importTxtBook(source)
        assertTrue("Large EPUB import failed: $result", result is ImportResult.Imported)
        val id = (result as ImportResult.Imported).bookId
        try {
            assertNotNull(app.container.repository.loadBook(id))
            assertEquals(ImportResult.Duplicate, app.container.importTxtBook(source))
        } finally { app.container.deleteBook(id) }
    }
}
