package com.iread.novel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iread.novel.data.files.ImportSource
import com.iread.novel.core.model.ReaderPreferences
import com.iread.novel.data.preferences.ReaderPreferencesStore
import com.iread.novel.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SettingsUiState(
    val scanning: Boolean = false,
    val importingCount: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val failureCount: Int = 0,
    val messages: List<String> = emptyList(),
    val candidates: List<BookCandidate> = emptyList(),
    val totalImports: Int = 0,
    val currentBook: String = "",
)

data class BookCandidate(val id: String, val name: String, val size: Long?, val selected: Boolean = true)
data class ScannedSources(val sources: List<() -> ImportSource>, val warnings: List<String> = emptyList())

class SettingsViewModel(
    private val importer: ImportTxtBookUseCase,
    scope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val preferencesStore: ReaderPreferencesStore? = null,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()
    private var candidateSources: Map<String, ImportSource> = emptyMap()
    val preferences = (preferencesStore?.preferences ?: flowOf(ReaderPreferences()))
        .stateIn(workScope, SharingStarted.Eagerly, ReaderPreferences())

    fun updatePreferences(value: ReaderPreferences) {
        workScope.launch {
            try { preferencesStore?.update(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(messages = it.messages + "阅读设置保存失败，请重试") } }
        }
    }

    fun scanBooks(scan: suspend () -> ScannedSources) {
        if (state.value.scanning || state.value.importingCount > 0) return
        mutableState.value = SettingsUiState(scanning = true)
        candidateSources = emptyMap()
        workScope.launch {
            try {
                val (found, sources) = withContext(io) {
                    val found = scan()
                    val sources = linkedMapOf<String, ImportSource>()
                    val warnings = found.warnings.toMutableList()
                    found.sources.forEachIndexed { index, create ->
                        try {
                            val source = create()
                            sources.putIfAbsent(source.sourceUri ?: "candidate-$index", source)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { warnings += "部分文件信息无法读取，已跳过" }
                    }
                    found.copy(warnings = warnings.distinct()) to sources
                }
                candidateSources = sources
                mutableState.value = SettingsUiState(
                    candidates = sources.map { (id, source) -> BookCandidate(id, source.displayName, source.sizeBytes) },
                    messages = found.warnings + if (sources.isEmpty()) listOf("未找到可导入的 TXT 或 EPUB 文件") else emptyList(),
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = SettingsUiState(messages = listOf("无法扫描文件夹，请重新选择并授予读取权限"))
            } finally { mutableState.update { it.copy(scanning = false) } }
        }
    }

    fun selectCandidate(id: String) {
        mutableState.update { it.copy(candidates = it.candidates.map { book -> if (book.id == id) book.copy(selected = !book.selected) else book }) }
    }

    fun selectAllCandidates(selected: Boolean) {
        mutableState.update { it.copy(candidates = it.candidates.map { book -> book.copy(selected = selected) }) }
    }

    fun dismissCandidates() {
        candidateSources = emptyMap()
        mutableState.update { it.copy(candidates = emptyList()) }
    }

    fun importSelected() {
        if (state.value.scanning || state.value.importingCount > 0) return
        val selected = state.value.candidates.filter { it.selected }.mapNotNull { candidateSources[it.id] }
        if (selected.isEmpty()) return
        val warnings = state.value.messages
        dismissCandidates()
        startImport(selected.map { source -> { source } }, warnings)
    }

    // Factories keep ContentResolver metadata queries on the IO dispatcher too.
    fun clearMessages() {
        mutableState.update { it.copy(messages = emptyList()) }
    }

    fun importSources(sources: List<() -> ImportSource>) {
        if (sources.isEmpty() || state.value.importingCount > 0 || state.value.scanning) return
        startImport(sources)
    }

    private fun startImport(sources: List<() -> ImportSource>, warnings: List<String> = emptyList()) {
        if (sources.isEmpty()) return
        candidateSources = emptyMap()
        mutableState.value = SettingsUiState(importingCount = sources.size, totalImports = sources.size, messages = warnings)
        workScope.launch {
            try {
                withContext(io) {
                    for (createSource in sources) {
                        var name = "所选文件"
                        val result = try {
                            val source = createSource()
                            name = source.displayName
                            mutableState.update { it.copy(currentBook = name) }
                            importer(source)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { ImportResult.Failed(ImportFailure.UNREADABLE_FILE) }
                        mutableState.update { old ->
                            when (result) {
                                is ImportResult.Imported -> old.copy(importingCount = old.importingCount - 1, importedCount = old.importedCount + 1)
                                ImportResult.Duplicate -> old.copy(importingCount = old.importingCount - 1, duplicateCount = old.duplicateCount + 1)
                                is ImportResult.Failed -> old.copy(importingCount = old.importingCount - 1, failureCount = old.failureCount + 1, messages = (old.messages + "$name：${result.reason.message()}").distinct())
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

    class Factory(private val importer: ImportTxtBookUseCase, private val preferencesStore: ReaderPreferencesStore? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(importer, preferencesStore = preferencesStore) as T
        }
    }
}
