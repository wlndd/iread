package com.iread.novel

import androidx.room.Room
import com.iread.novel.data.db.IReadDatabase
import java.io.File

class TestIReadApplication : IReadApplication() {
    override fun createContainer(): AppContainer = AppContainer(
        Room.inMemoryDatabaseBuilder(this, IReadDatabase::class.java).build(),
        File(cacheDir, "phase-one-test"),
    )
}
