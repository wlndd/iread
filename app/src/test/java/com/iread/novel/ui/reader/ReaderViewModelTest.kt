package com.iread.novel.ui.reader

import com.iread.novel.core.model.BookContent
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ReadingProgress
import com.iread.novel.testutil.FakeBookRepository
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @Test fun restoresSavedChapterAndOffset() = runTest {
        val repository = FakeBookRepository(
            progress = ReadingProgress("book-1", 2, 140, 1),
            initialContent = listOf(book()),
        )
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)

        runCurrent()

        assertEquals(2, viewModel.state.value.chapterIndex)
        assertEquals(140, viewModel.state.value.characterOffset)
    }

    @Test fun movingForwardMarksPreviousChapterCompleted() = runTest {
        val repository = FakeBookRepository(
            progress = ReadingProgress("book-1", 0, 30, -1),
            initialContent = listOf(book()),
        )
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)
        runCurrent()

        viewModel.openChapter(1)
        runCurrent()

        assertEquals(0, repository.savedProgress.last().lastCompletedChapterIndex)
    }

    @Test fun clampsSavedChapterAndOffsetToAvailableContent() = runTest {
        val content = book()
        val repository = FakeBookRepository(
            progress = ReadingProgress("book-1", 99, 999, 99),
            initialContent = listOf(content),
        )
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)

        runCurrent()

        assertEquals(2, viewModel.state.value.chapterIndex)
        assertEquals(content.chapters[2].body.length, viewModel.state.value.characterOffset)
    }

    @Test fun debouncesCharacterOffsetSavesForFiveHundredMilliseconds() = runTest {
        val repository = FakeBookRepository(initialContent = listOf(book()))
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)
        runCurrent()

        viewModel.updateCharacterOffset(2)
        runCurrent()
        assertEquals(2, viewModel.state.value.characterOffset)
        assertTrue(repository.savedProgress.isEmpty())

        advanceTimeBy(499)
        runCurrent()
        assertTrue(repository.savedProgress.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.savedProgress.single().characterOffset)
    }

    @Test fun flushPersistsPendingOffsetImmediatelyWithoutDuplicateSave() = runTest {
        val repository = FakeBookRepository(initialContent = listOf(book()))
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)
        runCurrent()

        viewModel.updateCharacterOffset(3)
        advanceTimeBy(100)
        viewModel.flushProgress()
        runCurrent()

        assertEquals(3, repository.savedProgress.single().characterOffset)

        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, repository.savedProgress.size)
    }

    private fun book() = BookContent(
        id = "book-1",
        title = "雾隐长安",
        author = "林渡",
        chapters = listOf(
            Chapter(0, "第一章", "第一章正文。"),
            Chapter(1, "第二章", "第二章正文。"),
            Chapter(2, "第三章", "第三章正文足够长，用于恢复阅读位置。".repeat(10)),
        ),
    )
}
