package com.iread.novel.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAnchorTest {
    @Test fun titleAndFirstParagraphShareAnAnchorBeforeDistinctness() {
        val starts = listOf(0, 10, 20)
        val anchors = listOf(0, 1, 2, 3, 3).map { readerAnchorForItemIndex(it, starts) }.distinct().drop(1)

        assertEquals(0, readerAnchorForItemIndex(0, starts))
        assertEquals(0, readerAnchorForItemIndex(1, starts))
        assertEquals(listOf(10, 20), anchors)
    }

    @Test fun initialClampedAnchorIsSuppressedWithoutWaitingForRequestedIndex() {
        val starts = listOf(0, 10, 20)
        val anchors = listOf(999, 999, 2).map { readerAnchorForItemIndex(it, starts) }.distinct().drop(1)

        assertEquals(20, readerAnchorForItemIndex(999, starts))
        assertEquals(listOf(10), anchors)
    }
}
