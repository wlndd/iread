package com.iread.novel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iread.novel.data.files.ImportSource
import com.iread.novel.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SettingsUiState(
    val importingCount: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val failureCount: Int = 0,
    val messages: List<String> = emptyList(),
)

class SettingsViewModel(
    private val importer: ImportTxtBookUseCase,
    scope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()

    // Factories keep ContentResolver metadata queries on the IO dispatcher too.
    fun importSources(sources: List<() -> ImportSource>) {
        if (sources.isEmpty() || state.value.importingCount > 0) return
        mutableState.value = SettingsUiState(importingCount = sources.size)
        workScope.launch {
            try {
                withContext(io) {
                    for (createSource in sources) {
                        var name = "所选文件"
                        val result = try {
                            val source = createSource()
                            name = source.displayName
                            importer(source)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { ImportResult.Failed(ImportFailure.UNREADABLE_FILE) }
                        mutableState.update { old ->
                            when (result) {
                                is ImportResult.Imported -> old.copy(importingCount = old.importingCount - 1, importedCount = old.importedCount + 1)
                                ImportResult.Duplicate -> old.copy(importingCount = old.importingCount - 1, duplicateCount = old.duplicateCount + 1, messages = old.messages + "$name：已在书架中")
                                is ImportResult.Failed -> old.copy(importingCount = old.importingCount - 1, failureCount = old.failureCount + 1, messages = old.messages + "$name：${result.reason.message()}")
                            }
                        }
                    }
                }
            } finally { mutableState.update { it.copy(importingCount = 0) } }
        }
    }

    private fun ImportFailure.message() = when (this) {
        ImportFailure.EMPTY_FILE -> "文件为空"
        ImportFailure.UNSUPPORTED_FORMAT -> "目前支持 TXT 和 EPUB 文件"
        ImportFailure.INVALID_EPUB -> "无法解析 EPUB：文件损坏、加密或内容超出支持范围"
        ImportFailure.UNREADABLE_FILE -> "无法读取文件，请重新选择"
        ImportFailure.UNKNOWN_ENCODING -> "无法识别文本编码"
        ImportFailure.NO_STORAGE -> "保存失败，请检查可用空间后重试"
    }

    class Factory(private val importer: ImportTxtBookUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(importer) as T
        }
    }
}
