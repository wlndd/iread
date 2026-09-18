package com.iread.novel.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iread.novel.core.model.*
import com.iread.novel.data.preferences.ReaderPreferencesStore
import com.iread.novel.data.repository.BookRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReaderUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val characterOffset: Int = 0,
    val errorMessage: String? = null,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val bookmarks: List<Bookmark> = emptyList(),
    val notice: String? = null,
)

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    scope: CoroutineScope? = null,
    private val preferencesStore: ReaderPreferencesStore? = null,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private var lastCompletedChapterIndex = -1
    private var offsetSaveJob: Job? = null
    private var chapterJob: Job? = null
    private var preferencesJob: Job? = null
    private var bookmarkJob: Job? = null
    private var progressDirty = false
    private var progressGeneration = 0L
    private val saveMutex = Mutex()

    init {
        workScope.launch {
            try {
                val book = repository.loadBookIndex(bookId)
                if (book == null || book.chapters.isEmpty()) {
                    mutableState.value = ReaderUiState(loading = false, errorMessage = "找不到可阅读的章节")
                    return@launch
                }
                val saved = repository.observeProgress(bookId).first()
                val index = (saved?.chapterIndex ?: 0).coerceIn(book.chapters.indices)
                val chapters = loadWindow(book.chapters, index)
                val prefs = preferencesStore?.preferences?.first() ?: ReaderPreferences()
                lastCompletedChapterIndex = (saved?.lastCompletedChapterIndex ?: -1).coerceIn(-1, chapters.lastIndex)
                mutableState.value = ReaderUiState(
                    loading = false, bookTitle = book.title, chapters = chapters, chapterIndex = index,
                    characterOffset = (saved?.characterOffset ?: 0).coerceIn(0, chapters[index].body.length),
                    preferences = prefs.copy(mode = saved?.mode ?: prefs.mode),
                )
                progressDirty = saved?.mode == null
                launch { repository.observeBookmarks(bookId).collect { marks -> mutableState.update { it.copy(bookmarks = marks) } } }
                preferencesStore?.let { store -> launch {
                    store.preferences.collect { defaults -> mutableState.update {
                        it.copy(preferences = defaults.copy(mode = it.preferences.mode))
                    } }
                } }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, errorMessage = "章节读取失败，请返回书架后重试") } }
        }
    }

    private suspend fun loadWindow(index: List<Chapter>, current: Int): List<Chapter> = index.mapIndexed { i, chapter ->
        if (i in current - 1..current + 1) repository.loadChapter(bookId, i) ?: error("Missing chapter")
        else chapter.copy(body = "")
    }

    fun openChapter(index: Int) = updatePosition(index, 0)

    fun updatePosition(index: Int, offset: Int) {
        val current = state.value
        if (current.loading || index !in current.chapters.indices) return
        if (index == current.chapterIndex) { updateCharacterOffset(offset); return }
        chapterJob?.cancel()
        chapterJob = workScope.launch {
            try {
                val chapters = loadWindow(current.chapters, index)
                if (index > current.chapterIndex) lastCompletedChapterIndex = maxOf(lastCompletedChapterIndex, current.chapterIndex)
                mutableState.update { it.copy(chapters = chapters, chapterIndex = index,
                    characterOffset = offset.coerceIn(0, chapters[index].body.length)) }
                scheduleSave(immediate = true)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(errorMessage = "章节读取失败，请返回书架后重试") } }
        }
    }

    fun updateCharacterOffset(offset: Int) {
        val current = state.value
        if (current.loading || current.chapters.isEmpty()) return
        val clamped = offset.coerceIn(0, current.chapters[current.chapterIndex].body.length)
        if (clamped == current.characterOffset) return
        mutableState.update { it.copy(characterOffset = clamped) }
        scheduleSave()
    }

    fun updatePreferences(value: ReaderPreferences) {
        mutableState.update { it.copy(preferences = value) }
        scheduleSave()
        preferencesJob = workScope.launch {
            try { preferencesStore?.update(value) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(notice = "阅读设置保存失败，请重试") } }
        }
    }

    fun toggleBookmark() {
        val current = state.value
        if (current.loading || current.chapters.isEmpty()) return
        if (bookmarkJob?.isActive == true) return
        val existing = current.bookmarks.firstOrNull { it.chapterIndex == current.chapterIndex && it.characterOffset == current.characterOffset }
        bookmarkJob = workScope.launch {
            try {
                if (existing != null) repository.removeBookmark(bookId, existing.chapterIndex, existing.characterOffset)
                else repository.upsertBookmark(Bookmark(bookId, current.chapterIndex, current.characterOffset,
                    current.chapters[current.chapterIndex].body.drop(current.characterOffset).take(80), System.currentTimeMillis()))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(notice = "书签保存失败，请重试") } }
        }
    }
    fun openBookmark(bookmark: Bookmark) = updatePosition(bookmark.chapterIndex, bookmark.characterOffset)

    private fun scheduleSave(immediate: Boolean = false) {
        progressGeneration++
        progressDirty = true
        offsetSaveJob?.cancel()
        val generation = progressGeneration
        val snapshot = progress()
        offsetSaveJob = workScope.launch {
            if (!immediate) delay(500)
            saveSnapshot(snapshot, generation)
        }
    }
    fun flushProgress() { workScope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { flushProgressAndWait() } } }
    suspend fun flushProgressAndWait() {
        chapterJob?.join()
        preferencesJob?.join()
        bookmarkJob?.join()
        if (state.value.loading || !progressDirty) return
        offsetSaveJob?.cancel()
        saveSnapshot(progress(), progressGeneration)
    }
    private fun progress() = ReadingProgress(bookId, state.value.chapterIndex, state.value.characterOffset,
        lastCompletedChapterIndex, state.value.preferences.mode)
    private suspend fun saveSnapshot(snapshot: ReadingProgress, generation: Long) {
        saveMutex.withLock {
            if (generation < progressGeneration) return@withLock
            try {
                repository.saveProgress(snapshot)
                if (generation == progressGeneration) progressDirty = false
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(notice = "阅读进度保存失败，请检查剩余空间") } }
        }
    }
    override fun onCleared() { offsetSaveJob?.cancel(); super.onCleared() }

    class Factory(private val bookId: String, private val repository: BookRepository,
        private val preferencesStore: ReaderPreferencesStore? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(bookId, repository, preferencesStore = preferencesStore) as T
    }
}
