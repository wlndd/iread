package com.iread.novel.ui.shelf

import com.iread.novel.core.model.BookSummary
import com.iread.novel.testutil.FakeBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {
    @Test fun exposesRepositoryBooksAndPersistsEdits() = runTest {
        val repository = FakeBookRepository(books = MutableStateFlow(listOf(BookSummary("1", "雾隐长安", "林渡", 21, 24))))
        val model = ShelfViewModel(repository, backgroundScope)
        runCurrent()
        assertEquals("雾隐长安", model.state.value.books.single().title)
        assertEquals(21, model.state.value.books.single().unreadChapters)
        model.updateMetadata("1", " 新书名 ", " 新作者 ")
        runCurrent()
        assertEquals("新书名", model.state.value.books.single().title)
        assertEquals("新作者", model.state.value.books.single().author)
    }

    @Test fun refusesBlankTitleWithoutLosingMetadata() = runTest {
        val repository = FakeBookRepository(books = MutableStateFlow(listOf(BookSummary("1", "原书名", "作者", 1, 1))))
        val model = ShelfViewModel(repository, backgroundScope)
        runCurrent()
        model.updateMetadata("1", "  ", "作者")
        runCurrent()
        assertEquals("原书名", model.state.value.books.single().title)
        assertNotNull(model.state.value.message)
    }
}
