package com.iread.novel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.data.files.ImportSource
import com.iread.novel.domain.ImportResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseOneJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var application: IReadApplication? = null
    private var source: File? = null

    // Catches broken import wiring, lost private copies, shelf mapping, chapter saves,
    // and reader restoration from Room after a fresh reader ViewModel is created.
    @Test fun importedTxtCanBeReadAndChapterTwoSurvivesRecreationAndReopening() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IReadApplication
        assertEquals("The isolated test application must replace the production composition root",
            "com.iread.novel.TestIReadApplication", app.javaClass.name)
        application = app
        val original = File.createTempFile("phase-one-source-", ".txt", app.cacheDir)
        source = original
        val body = "第一章 入城\n长安细雨，林间来客。\n第二章 夜航\n灯火映水，舟行向远。"
        original.writeText(body, Charsets.UTF_8)
        val result = runBlocking(Dispatchers.IO) {
            app.container.importTxtBook(object : ImportSource {
                override val displayName = "雾隐长安 - 林渡.txt"
                override val sizeBytes = original.length()
                override fun open() = original.inputStream()
            })
        }
        assertTrue("Production import must succeed: $result", result is ImportResult.Imported)
        val id = (result as ImportResult.Imported).bookId
        val privateCopy = File(app.cacheDir, "phase-one-test/books/$id.txt")
        assertEquals(body, privateCopy.readText(Charsets.UTF_8))
        assertEquals(body, original.readText(Charsets.UTF_8))

        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("雾隐长安")
        compose.onNodeWithText("林渡 · 2章未读").assertIsDisplayed()
        compose.onNodeWithText("雾隐长安").performClick()
        awaitText("第 1 / 2 章")
        compose.onNodeWithTag("reader-page").performTouchInput { click(center) }
        var statusBarBottom = 0
        scenario!!.onActivity { activity ->
            statusBarBottom = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        assertTrue("The reader title must stay below the system status bar",
            compose.onNodeWithText("雾隐长安").fetchSemanticsNode().boundsInWindow.top >= statusBarBottom)
        assertTrue("The reader back button must stay below the system status bar",
            compose.onNodeWithContentDescription("返回书架").fetchSemanticsNode().boundsInWindow.top >= statusBarBottom)
        compose.onNodeWithContentDescription("下一章").performClick()
        compose.onNodeWithTag("reader-page").performTouchInput { click(center) }
        awaitText("第 2 / 2 章")
        compose.onNodeWithText("灯火映水，舟行向远。", substring = true).assertIsDisplayed()
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                app.container.repository.observeProgress(id).first()?.chapterIndex == 1
            }
        }
        scenario!!.recreate()
        awaitText("第 2 / 2 章")
        compose.onNodeWithText("灯火映水，舟行向远。", substring = true).assertIsDisplayed()

        // Recreate retains ViewModels; closing and relaunching proves Room restoration too.
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("雾隐长安")
        compose.onNodeWithText("林渡 · 1章未读").assertIsDisplayed()
        compose.onNodeWithText("雾隐长安").performClick()
        awaitText("第 2 / 2 章")
        compose.onNodeWithText("灯火映水，舟行向远。", substring = true).assertIsDisplayed()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @After fun cleanOnlyJourneyResources() {
        scenario?.close()
        application?.let { app ->
            runBlocking(Dispatchers.IO) { app.container.database.clearAllTables() }
            val root = File(app.cacheDir, "phase-one-test").canonicalFile
            check(root.parentFile == app.cacheDir.canonicalFile && root.name == "phase-one-test")
            check(!root.exists() || root.deleteRecursively())
        }
        source?.let { check(!it.exists() || it.delete()) }
    }
}
