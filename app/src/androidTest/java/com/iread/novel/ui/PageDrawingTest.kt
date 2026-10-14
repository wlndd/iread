package com.iread.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iread.novel.ui.reader.MeasuredChapter
import com.iread.novel.ui.reader.PageCanvas
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageDrawingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unusedPageSpaceContainsNoNextPageText() {
        lateinit var chapter: MeasuredChapter
        compose.setContent {
            val density = LocalDensity.current
            chapter = with(density) {
                MeasuredChapter("山风吹过树林，旅人翻开书信。\n".repeat(100),
                    200.dp.roundToPx(), 250.dp.roundToPx(), 20.dp.toPx(), 1.8f)
            }
            PageCanvas(chapter, 0, Color.Black,
                Modifier.size(200.dp, 250.dp).background(Color.White).testTag("page"))
        }
        val bitmap = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        val bottom = chapter.pages.first().bottom - chapter.pages.first().top
        assertTrue("Fixture must leave room below the last complete line", bottom < bitmap.height - 10)
        var strayPixels = 0
        for (y in bottom until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) strayPixels++
            }
        }
        assertTrue("Next-page text leaked into bottom space: " + strayPixels, strayPixels == 0)
    }
}
