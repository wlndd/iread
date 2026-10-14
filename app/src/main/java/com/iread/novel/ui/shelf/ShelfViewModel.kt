package com.iread.novel.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iread.novel.core.model.BookSummary
import com.iread.novel.data.repository.BookRepository
import com.iread.novel.domain.DeleteBookUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ShelfUiState(val books: List<BookSummary> = emptyList(), val message: String? = null)

class ShelfViewModel(
    private val repository: BookRepository,
    scope: CoroutineScope? = null,
    private val deleteBook: DeleteBookUseCase? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(ShelfUiState())
    val state: StateFlow<ShelfUiState> = mutableState.asStateFlow()

    init {
        workScope.launch {
            repository.observeBooks().catch { mutableState.update { it.copy(message = "暂时无法读取书架") } }
                .collect { books -> mutableState.update { it.copy(books = books) } }
        }
        if (deleteBook != null) workScope.launch {
            attempt("上次删除尚未恢复，请稍后重试") { withContext(io) { deleteBook.recover() } }
        }
    }

    fun updateMetadata(bookId: String, title: String, author: String) {
        if (title.isBlank()) {
            mutableState.update { it.copy(message = "书名不能为空") }
            return
        }
        workScope.launch {
            attempt("保存失败，请重试") { repository.updateMetadata(bookId, title.trim(), author.trim().ifBlank { "未知作者" }) }
        }
    }

    fun delete(bookId: String) {
        workScope.launch {
            attempt("删除未完成，私有副本已保留以便恢复，请重试") {
                withContext(io) { checkNotNull(deleteBook)(bookId) }
            }
        }
    }

    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    private suspend fun attempt(message: String, action: suspend () -> Unit) {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(message = message) } }
    }

    class Factory(private val repository: BookRepository, private val deleteBook: DeleteBookUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ShelfViewModel::class.java))
            return ShelfViewModel(repository, deleteBook = deleteBook) as T
        }
    }
}
