package com.iread.novel.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ClipData
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.MainActivity
import com.iread.novel.IReadApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FolderImportFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun scanningExplainsStorageAccessAndCancellingDoesNotOpenFolderPicker() {
        org.junit.Assume.assumeFalse(com.iread.novel.data.files.hasStorageScanAccess(compose.activity))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var pickerCount = 0
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT_TREE) return null
                pickerCount++
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("一键扫描").performClick()
            compose.onNodeWithText("授权扫描小说文件").assertIsDisplayed()
            compose.onNodeWithText("前往授权").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("授权扫描小说文件").assertDoesNotExist()
            assertEquals(0, pickerCount)
            compose.onNodeWithText("导入书籍").assertIsDisplayed()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
    @Test fun multipleSelectedBooksImportEvenWhenAnotherFileIsUnsupported() {
        val app = compose.activity.application as IReadApplication
        assertEquals("com.iread.novel.TestIReadApplication", app.javaClass.name)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uri = DocumentsContract.buildDocumentUri("com.iread.novel.test.scan", "a")
        var launched = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                launched = true
                val selected = ClipData.newRawUri("selected books", uri).apply {
                    addItem(ClipData.Item(DocumentsContract.buildDocumentUri("com.iread.novel.test.scan", "skip")))
                    addItem(ClipData.Item(DocumentsContract.buildDocumentUri("com.iread.novel.test.scan", "b")))
                    addItem(ClipData.Item(DocumentsContract.buildDocumentUri("com.iread.novel.test.scan", "c")))
                }
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().apply {
                    clipData = selected
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("导入书籍").performClick()
            compose.waitUntil(15_000) { runBlocking(Dispatchers.IO) { app.container.repository.observeBooks().first().count { it.title.startsWith("目录验收") } == 3 } && compose.onAllNodesWithText("正在导入", substring = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("ignored.pdf：目前支持 TXT 和 EPUB 文件").assertIsDisplayed()
            assertTrue(launched)
            compose.onNodeWithContentDescription("返回书架").performClick()
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("ignored.pdf：目前支持 TXT 和 EPUB 文件").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回书架").performClick()
            compose.onNodeWithText("目录验收甲").assertIsDisplayed()
            compose.onNodeWithText("目录验收乙").assertIsDisplayed()
            val books = runBlocking(Dispatchers.IO) { app.container.repository.observeBooks().first().filter { it.title.startsWith("目录验收") } }
            assertEquals(3, books.size)
            assertTrue(books.any { it.title == "目录验收丙" })
            assertTrue(books.all { runBlocking(Dispatchers.IO) { app.container.database.bookDao().getBook(it.id)!!.sourceUri!!.contains("com.iread.novel.test.scan") } })
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking(Dispatchers.IO) {
                app.container.repository.observeBooks().first().filter { it.title.startsWith("目录验收") }.forEach { app.container.deleteBook(it.id) }
            }
        }
    }
}
