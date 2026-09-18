package com.iread.novel.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
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
    @Test fun selectedTreeImportsNestedBooksAndSkipsOtherFormats() {
        val app = compose.activity.application as IReadApplication
        assertEquals("com.iread.novel.TestIReadApplication", app.javaClass.name)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uri = DocumentsContract.buildTreeDocumentUri("com.iread.novel.test.scan", "root")
        var launched = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT_TREE) return null
                launched = true
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("扫描文件夹").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("已导入 2 本 · 重复 0 本 · 失败 0 本").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(launched)
            compose.onNodeWithContentDescription("返回书架").performClick()
            compose.onNodeWithText("目录验收甲").assertIsDisplayed()
            compose.onNodeWithText("目录验收乙").assertIsDisplayed()
            val books = runBlocking(Dispatchers.IO) { app.container.repository.observeBooks().first().filter { it.title.startsWith("目录验收") } }
            assertEquals(2, books.size)
            assertTrue(books.all { runBlocking(Dispatchers.IO) { app.container.database.bookDao().getBook(it.id)!!.sourceUri!!.contains("com.iread.novel.test.scan") } })
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking(Dispatchers.IO) {
                app.container.repository.observeBooks().first().filter { it.title.startsWith("目录验收") }.forEach { app.container.deleteBook(it.id) }
            }
        }
    }
}

