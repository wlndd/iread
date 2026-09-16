package com.iread.novel.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iread.novel.core.model.Chapter
import com.iread.novel.core.model.ReadingProgress
import com.iread.novel.data.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val characterOffset: Int = 0,
    val errorMessage: String? = null,
)

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private var lastCompletedChapterIndex = -1
    private var offsetSaveJob: Job? = null
    private var progressDirty = false

    init {
        workScope.launch {
            val book = repository.loadBook(bookId)
            if (book == null) {
                mutableState.value = ReaderUiState(loading = false, errorMessage = "找不到这本书")
                return@launch
            }
            if (book.chapters.isEmpty()) {
                mutableState.value = ReaderUiState(
                    loading = false,
                    bookTitle = book.title,
                    errorMessage = "这本书没有可阅读的章节",
                )
                return@launch
            }
            val saved = repository.observeProgress(bookId).first()
            val chapterIndex = (saved?.chapterIndex ?: 0).coerceIn(book.chapters.indices)
            val characterOffset = (saved?.characterOffset ?: 0)
                .coerceIn(0, book.chapters[chapterIndex].body.length)
            lastCompletedChapterIndex = (saved?.lastCompletedChapterIndex ?: -1)
                .coerceIn(-1, book.chapters.lastIndex)
            mutableState.value = ReaderUiState(
                loading = false,
                bookTitle = book.title,
                chapters = book.chapters,
                chapterIndex = chapterIndex,
                characterOffset = characterOffset,
            )
        }
    }

    fun openChapter(index: Int) {
        val current = mutableState.value
        if (current.loading || index !in current.chapters.indices || index == current.chapterIndex) return
        if (index > current.chapterIndex) {
            lastCompletedChapterIndex = maxOf(lastCompletedChapterIndex, current.chapterIndex)
        }
        offsetSaveJob?.cancel()
        mutableState.update { it.copy(chapterIndex = index, characterOffset = 0) }
        progressDirty = false
        workScope.launch { repository.saveProgress(progress()) }
    }

    fun updateCharacterOffset(offset: Int) {
        val current = mutableState.value
        if (current.loading || current.chapters.isEmpty()) return
        val clamped = offset.coerceIn(0, current.chapters[current.chapterIndex].body.length)
        if (clamped == current.characterOffset) return
        mutableState.update { it.copy(characterOffset = clamped) }
        progressDirty = true
        offsetSaveJob?.cancel()
        offsetSaveJob = workScope.launch {
            delay(500)
            repository.saveProgress(progress())
            progressDirty = false
        }
    }

    fun flushProgress() {
        if (!progressDirty || mutableState.value.loading) return
        offsetSaveJob?.cancel()
        progressDirty = false
        workScope.launch { repository.saveProgress(progress()) }
    }

    override fun onCleared() {
        offsetSaveJob?.cancel()
        super.onCleared()
    }

    private fun progress(): ReadingProgress = ReadingProgress(
        bookId = bookId,
        chapterIndex = state.value.chapterIndex,
        characterOffset = state.value.characterOffset,
        lastCompletedChapterIndex = lastCompletedChapterIndex,
    )

    class Factory(
        private val bookId: String,
        private val repository: BookRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReaderViewModel::class.java))
            return ReaderViewModel(bookId, repository) as T
        }
    }
}
