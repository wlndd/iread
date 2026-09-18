package com.iread.novel.ui.reader

import com.iread.novel.core.model.*
import com.iread.novel.data.preferences.ReaderPreferencesStore
import com.iread.novel.testutil.FakeBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderPersistenceTest {
    @Test fun loadsOnlyNeighbourBodiesAndRestoresModeBookmarksAndAnchor() = runTest {
        val repo = FakeBookRepository(initialContent = listOf(BookContent("book", "标题", "作者", (0..20).map { Chapter(it, "第$it 章", "正文".repeat(300)) })),
            progress = ReadingProgress("book", 10, 140, 9, ReaderMode.SCROLL))
        val preferences = object : ReaderPreferencesStore {
            override val preferences = MutableStateFlow(ReaderPreferences())
            override suspend fun update(value: ReaderPreferences) { preferences.value = value }
        }
        val model = ReaderViewModel("book", repo, backgroundScope, preferences)
        runCurrent()
        assertEquals(listOf(9, 10, 11), repo.loadedChapterIndices)
        assertEquals(3, model.state.value.chapters.count { it.body.isNotBlank() })
        assertEquals(ReaderMode.SCROLL, model.state.value.preferences.mode)
        model.toggleBookmark()
        runCurrent()
        assertEquals(140, model.state.value.bookmarks.single().characterOffset)
        model.updatePreferences(model.state.value.preferences.copy(fontSize = 24, theme = ReaderTheme.NIGHT))
        runCurrent()
        assertEquals(140, model.state.value.characterOffset)
        model.flushProgressAndWait()
        val reopened = ReaderViewModel("book", repo, backgroundScope, preferences)
        runCurrent()
        assertEquals(140, reopened.state.value.characterOffset)
        assertEquals(ReaderTheme.NIGHT, reopened.state.value.preferences.theme)
        assertEquals(ReaderMode.SCROLL, reopened.state.value.preferences.mode)
        reopened.toggleBookmark()
        runCurrent()
        assertTrue(reopened.state.value.bookmarks.isEmpty())
    }
}
