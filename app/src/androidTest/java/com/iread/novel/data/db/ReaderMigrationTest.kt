package com.iread.novel.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iread.novel.core.model.*
import com.iread.novel.data.preferences.DataStoreReaderPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderMigrationTest {
    @Test fun upgradesOriginalSchemaWithoutLosingBooksAndCascadesPersistentBookmarks() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("iread-migration-", ".db", context.cacheDir)
        var room: IReadDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                val schema = JSONObject(instrumentation.context.assets.open("com.iread.novel.data.db.IReadDatabase/1.json").bufferedReader().use { it.readText() })
                    .getJSONObject("database").getJSONArray("entities")
                for (i in 0 until schema.length()) {
                    val entity = schema.getJSONObject(i)
                    val table = entity.getString("tableName")
                    sqlite.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("$" + "{TABLE_NAME}", table))
                }
                sqlite.execSQL("INSERT INTO books VALUES ('old','旧书','作者','TXT','private-copy.txt','fingerprint',1,10,NULL)")
                sqlite.execSQL("INSERT INTO chapters VALUES ('old',0,'第一章','正文')")
                sqlite.execSQL("INSERT INTO reading_progress VALUES ('old',0,1,-1,10)")
                sqlite.version = 1
            }
            room = Room.databaseBuilder(context, IReadDatabase::class.java, file.absolutePath).addMigrations(MIGRATION_1_2).build()
            assertEquals("旧书", room.bookDao().getBook("old")!!.title)
            assertEquals(1, room.bookDao().getProgress("old")!!.characterOffset)
            assertNull(room.bookDao().getProgress("old")!!.mode)
            room.bookDao().upsertBookmark(BookmarkEntity("old",0,1,"正文",20))
            room.close()
            room = Room.databaseBuilder(context, IReadDatabase::class.java, file.absolutePath).addMigrations(MIGRATION_1_2).build()
            assertEquals("正文", room.bookDao().observeBookmarks("old").first().single().snippet)
            room.bookDao().deleteBook("old")
            assertTrue(room.bookDao().observeBookmarks("old").first().isEmpty())
            assertNull(room.bookDao().getProgress("old"))
            assertTrue(room.bookDao().getChapters("old").isEmpty())
        } finally {
            room?.close()
            context.deleteDatabase(file.absolutePath)
        }
    }

    @Test fun preferencesSurviveStoreCloseAndReopen() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "preferences-test-").toFile()
        var store = DataStoreReaderPreferencesStore(root)
        try {
            val wanted = ReaderPreferences(ReaderTheme.NIGHT, ReaderMode.SCROLL, 24, 2f)
            store.update(wanted)
            store.close()
            store = DataStoreReaderPreferencesStore(root)
            assertEquals(wanted, store.preferences.first())
        } finally { store.close(); root.deleteRecursively() }
    }
}
