package com.iread.novel.data.files

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageBookTraversalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun scansAllVolumesAndNestedHiddenFoldersWithoutReadingContents() = runTest {
        val phone = temporary.newFolder("phone")
        val card = temporary.newFolder("card")
        val first = File(phone, "Download/小说.TXT").apply { parentFile?.mkdirs(); writeText("正文") }
        val second = File(card, ".books/故事.EPUB").apply { parentFile?.mkdirs(); writeText("not parsed during scan") }
        File(phone, "图片.jpg").writeText("ignored")
        val privateDirectory = File(phone, "Android/data/other.app").apply { mkdirs() }
        File(privateDirectory, "私有.txt").writeText("excluded")
        val result = StorageBookTraversal().scan(listOf(phone, card, phone), listOf(File(phone, "Android/data")))
        assertEquals(setOf(first.canonicalPath, second.canonicalPath), result.files.map { it.path }.toSet())
        assertEquals("正文", first.readText())
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun doesNotStopAfterTenThousandFilesOrThirtyTwoDirectoryLevels() = runTest {
        val root = temporary.newFolder("large")
        repeat(10_010) { File(root, "$it.bin").createNewFile() }
        var deep = root
        repeat(40) { deep = File(deep, "d") }
        deep.mkdirs()
        val book = File(deep, "深层.txt").apply { writeText("正文") }
        var last = StorageScanProgress()
        val result = StorageBookTraversal().scan(listOf(root), onProgress = { last = it })
        assertEquals(listOf(book.canonicalPath), result.files.map { it.path })
        assertEquals(10_011L, last.checkedFiles)
        assertEquals(1, last.foundBooks)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun progressCallbackCanCancelTraversal() = runTest {
        val root = temporary.newFolder("cancel")
        repeat(256) { File(root, "$it.txt").createNewFile() }
        try {
            StorageBookTraversal().scan(listOf(root), onProgress = { throw CancellationException("cancel") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
