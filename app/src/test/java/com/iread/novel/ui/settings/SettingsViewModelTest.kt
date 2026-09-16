package com.iread.novel.ui.settings

import com.iread.novel.core.parser.TxtBookParser
import com.iread.novel.data.files.PrivateBookFileStore
import com.iread.novel.domain.*
import com.iread.novel.testutil.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun importsSequentiallyAndReportsSuccessDuplicateAndFailure() = runTest {
        val repository = FakeBookRepository()
        val importer = ImportTxtBookUseCase(repository, PrivateBookFileStore(temporary.root), TxtBookParser(), TimeSource { 1L })
        val model = SettingsViewModel(importer, backgroundScope, StandardTestDispatcher(testScheduler))
        val source = ByteArrayImportSource("书.txt", "第一章 开始\n正文".toByteArray())
        model.importSources(listOf({ source }, { source }, { ByteArrayImportSource("坏.pdf", byteArrayOf(1)) }))
        assertEquals(3, model.state.value.importingCount)
        runCurrent()
        assertEquals(0, model.state.value.importingCount)
        assertEquals(1, model.state.value.importedCount)
        assertEquals(1, model.state.value.duplicateCount)
        assertEquals(1, model.state.value.failureCount)
        assertEquals(1, repository.books.value.size)
        assertTrue(model.state.value.messages.any { it.contains("已在书架中") })
        assertTrue(model.state.value.messages.any { it.contains("TXT") })
    }
}
