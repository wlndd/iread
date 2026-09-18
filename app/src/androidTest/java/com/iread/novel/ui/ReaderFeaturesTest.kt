package com.iread.novel.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iread.novel.ui.reader.MeasuredChapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFeaturesTest {
    @Test fun measuredPagesCoverLongChineseChapterWithoutLossOrOverlap() {
        val body = "　　山间的风吹过树林，远处有人走来。😀\n".repeat(2000)
        val chapter = MeasuredChapter(body, 320, 480, 22f, 1.7f)
        assertTrue(chapter.pages.size > 10)
        assertEquals(body, chapter.pages.joinToString("") { body.substring(it.start, it.end) })
        chapter.pages.forEach { assertTrue(it.bottom - it.top <= 480) }
        val anchor = body.length / 2
        val larger = MeasuredChapter(body, 320, 480, 30f, 1.9f)
        val page = larger.pages[larger.pageForOffset(anchor)]
        assertTrue(anchor >= page.start && anchor < page.end)
        assertEquals(larger.pages.lastIndex, larger.pageForOffset(Int.MAX_VALUE))
    }
}
