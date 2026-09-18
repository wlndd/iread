package com.iread.novel.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iread.novel.core.model.ReaderMode
import com.iread.novel.core.model.ReaderPreferences
import com.iread.novel.core.model.ReaderTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** One application-owned instance per privateRoot; close only when that owner is destroyed. */
class DataStoreReaderPreferencesStore(privateRoot: File) : ReaderPreferencesStore, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        File(privateRoot, "reader.preferences_pb")
    }
    override val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        ReaderPreferences(
            theme = ReaderTheme.entries.firstOrNull { it.name == values[THEME] } ?: ReaderTheme.PAPER,
            mode = ReaderMode.entries.firstOrNull { it.name == values[MODE] } ?: ReaderMode.PAGED,
            fontSize = (values[FONT_SIZE] ?: 19).coerceIn(14, 30),
            lineSpacing = (values[LINE_SPACING] ?: 1.7f).takeIf { it.isFinite() }?.coerceIn(1.2f, 2.4f) ?: 1.7f,
        )
    }

    override suspend fun update(value: ReaderPreferences) {
        store.edit { values ->
            values[THEME] = value.theme.name
            values[MODE] = value.mode.name
            values[FONT_SIZE] = value.fontSize.coerceIn(14, 30)
            values[LINE_SPACING] = value.lineSpacing.takeIf { it.isFinite() }?.coerceIn(1.2f, 2.4f) ?: 1.7f
        }
    }

    override fun close() {
        scope.cancel()
        kotlinx.coroutines.runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.join() }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val MODE = stringPreferencesKey("mode")
        val FONT_SIZE = intPreferencesKey("font_size")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
    }
}
