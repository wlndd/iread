package com.iread.novel.data.preferences

import com.iread.novel.core.model.ReaderPreferences
import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesStore {
    val preferences: Flow<ReaderPreferences>
    suspend fun update(value: ReaderPreferences)
}
