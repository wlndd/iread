package com.iread.novel.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.IReadApplication
import com.iread.novel.MainActivity
import com.iread.novel.core.model.*
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

@RunWith(AndroidJUnit4::class)
class ReaderInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun pageGesturesReflowThemesBookmarksDirectoryAndScrollSurviveReopen() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IReadApplication
        assertEquals("com.iread.novel.TestIReadApplication", app.javaClass.name)
        runBlocking { app.container.preferences.update(ReaderPreferences()) }
        val text = "第一章 山路\n" + "山风吹过竹林，小路沿着溪水延伸。旅人停下脚步，翻开手中的书信。\n".repeat(120) + "第二章 夜雨\n窗外的细雨敲打着屋檐。"
        val result = runBlocking(Dispatchers.IO) {
            app.container.importTxtBook(object : ImportSource {
                override val displayName = "翻页验收 - 林渡.txt"
                override val sizeBytes = text.toByteArray().size.toLong()
                override fun open() = text.byteInputStream()
            })
        } as ImportResult.Imported
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("翻页验收")
            compose.onNodeWithText("翻页验收").performClick()
            awaitPage()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-footer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader-footer").assertIsDisplayed()
            compose.onNodeWithTag("reader-controls").assertDoesNotExist()
            screenshot(app, "reader-footer")
            compose.onNodeWithTag("reader-page").performTouchInput { click(center) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-controls").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader-footer").assertDoesNotExist()
            compose.onNodeWithTag("reader-page").performTouchInput { click(Offset(width * .85f, height * .4f)) }
            compose.waitUntil(10_000) { progress(app, result.bookId)?.characterOffset?.let { it > 0 } == true }
            val secondOffset = progress(app, result.bookId)!!.characterOffset
            compose.onNodeWithTag("reader-page").performTouchInput { swipeLeft() }
            compose.waitUntil(10_000) { (progress(app, result.bookId)?.characterOffset ?: 0) > secondOffset }
            compose.onNodeWithContentDescription("添加书签").performClick()
            compose.waitUntil(10_000) { marks(app, result.bookId).size == 1 }
            val mark = marks(app, result.bookId).single()
            compose.onNodeWithText("字号与主题").performClick()
            compose.onNodeWithText("字号 ＋").performClick()
            compose.onNodeWithText("雾霭蓝").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-BLUE").fetchSemanticsNodes().isNotEmpty() }
            awaitPage()
            screenshot(app, "reader-blue")
            assertEquals(mark.characterOffset, progress(app, result.bookId)!!.characterOffset)
            compose.onNodeWithText("字号与主题").performClick()
            compose.onNodeWithText("深夜黑").performClick()
            compose.onNodeWithText("完成").performClick()
            awaitPage()
            screenshot(app, "reader-night")
            compose.onNodeWithText("字号与主题").performClick()
            compose.onNodeWithText("暖纸黄").performClick()
            compose.onNodeWithText("完成").performClick()
            awaitPage()
            screenshot(app, "reader-paper")
            compose.onNodeWithText("目录").performClick()
            compose.onNodeWithText("第二章 夜雨").performClick()
            awaitPage()
            compose.onNodeWithTag("reader-page").performTouchInput { click(center) }
            awaitText("第 2 / 2 章")
            compose.onNodeWithTag("reader-page").performTouchInput { click(Offset(width * .1f, height * .4f)) }
            awaitText("第 1 / 2 章")
            compose.waitUntil(10_000) { (progress(app, result.bookId)?.characterOffset ?: 0) > mark.characterOffset }
            compose.onNodeWithTag("reader-page").performTouchInput { click(center) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-controls").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("书签").performClick()
            compose.onNodeWithText("第一章 山路\n" + mark.snippet).performClick()
            compose.waitUntil(10_000) { progress(app, result.bookId)?.characterOffset == mark.characterOffset }
            compose.onNodeWithText("切换上下滚动").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-scroll").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader-scroll").performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("返回书架").performClick()
            awaitText("我的书架")
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("翻页验收")
            compose.onNodeWithText("翻页验收").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-scroll").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(ReaderMode.SCROLL, progress(app, result.bookId)!!.mode)
            assertEquals(1, marks(app, result.bookId).size)
            compose.onNodeWithTag("reader-scroll").performTouchInput { click(center) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-controls").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("返回书架").performClick()
            awaitText("我的书架")
        } finally {
            scenario?.close()
            runBlocking(Dispatchers.IO) {
                app.container.deleteBook(result.bookId)
                app.container.preferences.update(ReaderPreferences())
            }
        }
    }
    private fun progress(app: IReadApplication, id: String) = runBlocking(Dispatchers.IO) { app.container.repository.observeProgress(id).first() }
    private fun marks(app: IReadApplication, id: String) = runBlocking(Dispatchers.IO) { app.container.repository.observeBookmarks(id).first() }
    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitPage() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader-page").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun screenshot(app: IReadApplication, name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
