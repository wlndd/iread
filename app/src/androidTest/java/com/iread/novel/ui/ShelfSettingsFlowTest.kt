package com.iread.novel.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.IReadApplication
import com.iread.novel.MainActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfSettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun iconOnlySettingsButtonOpensImportOptionsAndBackReturnsToShelf() {
        compose.onNodeWithText("设置").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed().performClick()
        compose.onNodeWithText("导入书籍").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("扫描文件夹").assertDoesNotExist()
        compose.onNodeWithText("一键扫描").assertIsDisplayed()
        compose.onNodeWithText("书籍文件夹").assertDoesNotExist()
        compose.onNodeWithText("阅读偏好").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("暖纸黄").performScrollTo()
        compose.onNodeWithText("默认主题").assertIsDisplayed()
        compose.onNodeWithText("暖纸黄").assertIsDisplayed()
        compose.onNodeWithText("阅读时可调整字号、行距与背景。").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("我的书架").assertIsDisplayed()
    }

    @Test fun pickerImportEditAndConfirmedDeletePreserveOriginalFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = compose.activity.contentResolver
        val title = "山间来信${UUID.randomUUID().toString().take(6)}"
        val original = "第一章 山风\n这是原始文件 ${UUID.randomUUID()}"
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }))
        resolver.openOutputStream(uri)!!.use { it.write(original.toByteArray()) }
        var pickedIntent: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                pickedIntent = intent
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
            }
        }
        instrumentation.addMonitor(monitor)
        val container = (compose.activity.application as IReadApplication).container
        var importedId: String? = null
        try {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("导入书籍").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("已导入 1 本 · 重复 0 本 · 失败 0 本").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, pickedIntent?.action)
            assertTrue(pickedIntent!!.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            assertEquals("*/*", pickedIntent!!.type)
            assertTrue(pickedIntent!!.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("*/*"))
            compose.onNodeWithContentDescription("返回书架").performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            val book = runBlocking(Dispatchers.IO) { container.repository.observeBooks().first().single { it.title == title } }
            importedId = book.id
            val privateCopy = File(runBlocking(Dispatchers.IO) {
                checkNotNull(container.database.bookDao().getBook(book.id)).sourcePath
            })
            assertTrue(privateCopy.exists())
            compose.onNodeWithText("未知作者 · 1章未读").assertIsDisplayed()
            compose.onNodeWithContentDescription("更多：$title").performClick()
            compose.onNodeWithText("编辑书籍信息").performClick()
            compose.onNodeWithText("书名").performTextReplacement("山间新信")
            compose.onNodeWithText("作者").performTextReplacement("林渡")
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("山间新信").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("林渡", runBlocking(Dispatchers.IO) { container.repository.loadBook(book.id)!!.author })
            compose.onNodeWithContentDescription("更多：山间新信").performClick()
            compose.onNodeWithText("删除本书").performClick()
            compose.onNodeWithText("确定从书架删除《山间新信》吗？原始文件不会被删除。").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            assertTrue(privateCopy.exists())
            compose.onNodeWithContentDescription("更多：山间新信").performClick()
            compose.onNodeWithText("删除本书").performClick()
            compose.onNodeWithText("删除", substring = false).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("山间新信").fetchSemanticsNodes().isEmpty() }
            assertNull(runBlocking(Dispatchers.IO) { container.repository.loadBook(book.id) })
            compose.waitUntil(5_000) { !privateCopy.exists() && container.files.pendingDeletionIds().isEmpty() }
            assertEquals(original, resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        } finally {
            instrumentation.removeMonitor(monitor)
            importedId?.let { id -> runBlocking(Dispatchers.IO) {
                if (container.repository.loadBook(id) != null) container.deleteBook(id)
            } }
            resolver.delete(uri, null, null)
        }
    }
}
