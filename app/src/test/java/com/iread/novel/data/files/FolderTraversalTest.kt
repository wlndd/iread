package com.iread.novel.data.files

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FolderTraversalTest {
    @Test fun scansOnlyBooksRecursivelyAndDoesNotLoopOrDuplicate() = runTest {
        val folders = mapOf(
            "root" to listOf(ScanDocument("a", "One.TXT", false), ScanDocument("sub", "sub", true), ScanDocument("pdf", "skip.pdf", false)),
            "sub" to listOf(ScanDocument("a", "One.TXT", false), ScanDocument("b", "Two.epub", false), ScanDocument("root", "cycle", true)),
        )
        val scanned = FolderTraversal { folders.getValue(it) }.scan("root")
        assertEquals(listOf("a", "b"), scanned.documents.map { it.id })
        assertTrue(scanned.warnings.isEmpty())
    }
    @Test fun reportsUnreadableChildButStillReturnsOtherFiles() = runTest {
        val result = FolderTraversal { id ->
            if (id != "root") throw SecurityException()
            listOf(ScanDocument("good", "book.txt", false), ScanDocument("bad", "private", true))
        }.scan("root")
        assertEquals("good", result.documents.single().id)
        assertFalse(result.warnings.isEmpty())
    }
    @Test fun scansDownloadedEpubWhenProviderOmitsFileExtension() = runTest {
        val result = FolderTraversal {
            listOf(ScanDocument("download", "刚下载的小说", false, "application/epub+zip"))
        }.scan("root")
        assertEquals(listOf("download"), result.documents.map { it.id })
        assertEquals("刚下载的小说.epub", normalizedBookDisplayName("刚下载的小说", "application/epub+zip"))
    }
    @Test fun limitsLargeFoldersWithAnExplicitWarning() = runTest {
        val result = FolderTraversal { (0..2500).map { ScanDocument("$it", "$it.txt", false) } }.scan("root")
        assertEquals(2000, result.documents.size)
        assertFalse(result.warnings.isEmpty())
    }
}
