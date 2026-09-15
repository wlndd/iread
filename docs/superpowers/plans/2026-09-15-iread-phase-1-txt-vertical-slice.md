# iRead Phase 1 TXT Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android app that imports a real TXT file from the system picker, persists it, displays it on the approved bookshelf, opens it in a basic reader, and restores reading progress after restart.

**Architecture:** Use one Android `app` module with feature-oriented packages. Compose screens talk to ViewModels; ViewModels call small use cases; use cases depend on repository interfaces; Room and application-private files provide the production data implementation. Phase 1 establishes the complete TXT vertical slice, while pagination, bookmarks, EPUB, folder scanning, and final visual polish are separate follow-up plans.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.4.0, Gradle 9.6.0, compile/target SDK 37, min SDK 26, Jetpack Compose BOM 2026.08.00, Navigation Compose 2.10.1, Lifecycle 2.11.0, Room 2.8.5, coroutines 1.10.2, JUnit 4, AndroidX Test, Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-09-15-iread-android-design.md`

## Global Constraints

- Android only; package name is `com.iread.novel` and application label is `iRead`.
- Minimum Android version is Android 8.0 / API 26.
- The app is offline-first and must not request the `INTERNET` permission.
- Import uses the Storage Access Framework and copies the selected file into application-private storage.
- The original user file is never modified or deleted.
- Phase 1 accepts `.txt` only; `.epub` is added in a later plan through the same parser interface.
- All user-visible text is Simplified Chinese.
- New behavior follows red-green-refactor TDD and each task ends in a focused commit.
- Do not add dependency injection, analytics, networking, accounts, ads, or a multi-module build.

---

## Planned File Map

### Build and application shell

- `settings.gradle.kts`: repositories and the single `app` module.
- `build.gradle.kts`: root plugin declarations.
- `gradle.properties`: AndroidX and Gradle settings.
- `gradle/libs.versions.toml`: pinned dependency versions and aliases.
- `app/build.gradle.kts`: Android configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: application and launcher activity without network permission.
- `app/src/main/java/com/iread/novel/MainActivity.kt`: edge-to-edge Compose host.
- `app/src/main/java/com/iread/novel/IReadApplication.kt`: production dependency container owner.
- `app/src/main/java/com/iread/novel/IReadApp.kt`: root theme and navigation host.

### Core and TXT parsing

- `app/src/main/java/com/iread/novel/core/model/BookModels.kt`: domain types shared by features.
- `app/src/main/java/com/iread/novel/core/parser/BookParser.kt`: format-independent parser interface.
- `app/src/main/java/com/iread/novel/core/parser/BookMetadataParser.kt`: title and author inference.
- `app/src/main/java/com/iread/novel/core/parser/TxtDecoder.kt`: UTF-8 and GB18030 decoding.
- `app/src/main/java/com/iread/novel/core/parser/TxtBookParser.kt`: TXT chapter extraction.

### Persistence and import

- `app/src/main/java/com/iread/novel/data/db/Entities.kt`: Room entities and foreign keys.
- `app/src/main/java/com/iread/novel/data/db/BookDao.kt`: book, chapter, and progress operations.
- `app/src/main/java/com/iread/novel/data/db/IReadDatabase.kt`: Room database.
- `app/src/main/java/com/iread/novel/data/files/ImportSource.kt`: repeatable source stream abstraction.
- `app/src/main/java/com/iread/novel/data/files/AndroidImportSource.kt`: `ContentResolver` and `Uri` adapter.
- `app/src/main/java/com/iread/novel/data/files/PrivateBookFileStore.kt`: temporary copy, hash, finalize, cleanup.
- `app/src/main/java/com/iread/novel/data/repository/BookRepository.kt`: domain repository contract.
- `app/src/main/java/com/iread/novel/data/repository/RoomBookRepository.kt`: transactional production repository.
- `app/src/main/java/com/iread/novel/domain/ImportTxtBookUseCase.kt`: duplicate-safe TXT import coordinator.

### UI features

- `app/src/main/java/com/iread/novel/ui/theme/IReadTheme.kt`: approved light-green visual tokens.
- `app/src/main/java/com/iread/novel/ui/navigation/IReadNavHost.kt`: shelf, settings, and reader routes.
- `app/src/main/java/com/iread/novel/ui/shelf/ShelfViewModel.kt`: shelf state and book actions.
- `app/src/main/java/com/iread/novel/ui/shelf/ShelfScreen.kt`: approved shelf layout.
- `app/src/main/java/com/iread/novel/ui/settings/SettingsViewModel.kt`: import state.
- `app/src/main/java/com/iread/novel/ui/settings/SettingsScreen.kt`: settings list and file picker entry.
- `app/src/main/java/com/iread/novel/ui/reader/ReaderViewModel.kt`: chapter loading and progress updates.
- `app/src/main/java/com/iread/novel/ui/reader/ReaderScreen.kt`: Phase 1 vertical reader.

### Tests

- `app/src/test/java/com/iread/novel/core/parser/BookMetadataParserTest.kt`
- `app/src/test/java/com/iread/novel/core/parser/TxtDecoderTest.kt`
- `app/src/test/java/com/iread/novel/core/parser/TxtBookParserTest.kt`
- `app/src/test/java/com/iread/novel/domain/ImportTxtBookUseCaseTest.kt`
- `app/src/test/java/com/iread/novel/ui/shelf/ShelfViewModelTest.kt`
- `app/src/test/java/com/iread/novel/ui/reader/ReaderViewModelTest.kt`
- `app/src/androidTest/java/com/iread/novel/data/db/BookDaoTest.kt`
- `app/src/androidTest/java/com/iread/novel/ui/ShelfSettingsFlowTest.kt`

---

### Task 1: Reproducible Android Build and App Shell

**Files:**

- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/iread/novel/MainActivity.kt`
- Create: `app/src/main/java/com/iread/novel/IReadApp.kt`
- Create: `app/src/main/java/com/iread/novel/AppMetadata.kt`
- Test: `app/src/test/java/com/iread/novel/AppMetadataTest.kt`

**Interfaces:**

- Consumes: no application code.
- Produces: `AppMetadata.name: String`, `AppMetadata.packageName: String`, and a launchable `IReadApp()` composable.

- [ ] **Step 1: Install the missing build tools with explicit user approval**

The current machine has Java 25 but no Gradle command, Android SDK, or Android Studio. Download only official packages. Install Android command-line tools under `C:\Users\fhj\AppData\Local\Android\Sdk`, then run:

```powershell
sdkmanager.bat --sdk_root="C:\Users\fhj\AppData\Local\Android\Sdk" "platform-tools" "platforms;android-37" "build-tools;37.0.0"
sdkmanager.bat --sdk_root="C:\Users\fhj\AppData\Local\Android\Sdk" --licenses
```

Download Gradle 9.6.0 from `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip`, extract it to a task-owned temporary directory, and use it once to generate the wrapper:

```powershell
gradle-9.6.0\bin\gradle.bat wrapper --gradle-version 9.6.0 --distribution-type bin
```

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/` exist. Do not commit the Android SDK or downloaded Gradle distribution.

- [ ] **Step 2: Create the root Gradle configuration**

Use this version catalog:

```toml
[versions]
agp = "9.4.0"
kotlin = "2.3.21"
ksp = "2.3.6"
composeBom = "2026.08.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.10.1"
room = "2.8.5"
coroutines = "1.10.2"
junit = "4.13.2"
androidxJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

`settings.gradle.kts` must use `google()`, `mavenCentral()`, and `gradlePluginPortal()` for plugin management, and include only `:app`. `gradle.properties` must contain:

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 3: Create the failing application metadata test**

```kotlin
package com.iread.novel

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMetadataTest {
    @Test
    fun exposesApprovedApplicationIdentity() {
        assertEquals("iRead", AppMetadata.name)
        assertEquals("com.iread.novel", AppMetadata.packageName)
    }
}
```

- [ ] **Step 4: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.AppMetadataTest
```

Expected: FAIL during Kotlin compilation because `AppMetadata` is unresolved.

- [ ] **Step 5: Implement the minimal application identity and Compose shell**

```kotlin
package com.iread.novel

object AppMetadata {
    const val name = "iRead"
    const val packageName = "com.iread.novel"
}
```

`MainActivity` uses `enableEdgeToEdge()` and calls `setContent { IReadApp() }`. `IReadApp()` initially displays `Text("我的书架")`. The manifest sets label `iRead`, declares exported launcher activity `.MainActivity`, omits a custom application class until Task 6, and contains no `uses-permission` entry for networking.

- [ ] **Step 6: Run unit test and debug build**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.AppMetadataTest
.\gradlew.bat assembleDebug
```

Expected: one passing test and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 7: Commit**

```powershell
git add settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: bootstrap iRead Android app"
```

---

### Task 2: Domain Models and Filename Metadata Detection

**Files:**

- Create: `app/src/main/java/com/iread/novel/core/model/BookModels.kt`
- Create: `app/src/main/java/com/iread/novel/core/parser/BookMetadataParser.kt`
- Test: `app/src/test/java/com/iread/novel/core/parser/BookMetadataParserTest.kt`

**Interfaces:**

- Consumes: plain file display names.
- Produces: `BookMetadataParser.parse(fileName: String): BookMetadata`, plus `BookFormat`, `BookSummary`, `Chapter`, `ReadingProgress`, and `ParsedBook`.

- [ ] **Step 1: Write metadata behavior tests**

```kotlin
package com.iread.novel.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataParserTest {
    @Test fun parsesDashSeparatedAuthor() {
        assertEquals(BookMetadata("雾隐长安", "林渡"), BookMetadataParser.parse("雾隐长安 - 林渡.txt"))
    }

    @Test fun parsesUnderscoreSeparatedAuthor() {
        assertEquals(BookMetadata("剑来", "烽火戏诸侯"), BookMetadataParser.parse("剑来_烽火戏诸侯.TXT"))
    }

    @Test fun fallsBackToUnknownAuthor() {
        assertEquals(BookMetadata("长夜行", "未知作者"), BookMetadataParser.parse("长夜行.txt"))
    }
}
```

- [ ] **Step 2: Run the parser tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.core.parser.BookMetadataParserTest
```

Expected: FAIL because `BookMetadataParser` and domain models do not exist.

- [ ] **Step 3: Add the minimal domain model contract**

```kotlin
package com.iread.novel.core.model

enum class BookFormat { TXT, EPUB }

data class BookMetadata(val title: String, val author: String)
data class Chapter(val index: Int, val title: String, val body: String)
data class ParsedBook(val metadata: BookMetadata, val chapters: List<Chapter>)
data class ImportedBook(
    val id: String,
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val sourcePath: String,
    val fingerprint: String,
    val importedAt: Long,
)
data class BookContent(
    val id: String,
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
)
data class BookSummary(
    val id: String,
    val title: String,
    val author: String,
    val unreadChapters: Int,
    val totalChapters: Int,
)
data class ReadingProgress(
    val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val lastCompletedChapterIndex: Int,
)
```

Implement `BookMetadataParser` by removing a case-insensitive `.txt` or `.epub` suffix, splitting once on a spaced dash, full-width dash, or underscore, trimming both sides, and using `未知作者` when the author is blank.

- [ ] **Step 4: Run tests and verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.core.parser.BookMetadataParserTest
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/iread/novel/core app/src/test/java/com/iread/novel/core
git commit -m "feat: detect TXT book metadata"
```

---

### Task 3: TXT Decoding and Chapter Parsing

**Files:**

- Create: `app/src/main/java/com/iread/novel/core/parser/BookParser.kt`
- Create: `app/src/main/java/com/iread/novel/core/parser/TxtDecoder.kt`
- Create: `app/src/main/java/com/iread/novel/core/parser/TxtBookParser.kt`
- Test: `app/src/test/java/com/iread/novel/core/parser/TxtDecoderTest.kt`
- Test: `app/src/test/java/com/iread/novel/core/parser/TxtBookParserTest.kt`

**Interfaces:**

- Consumes: `BookMetadata` and repeatable `InputStream` content.
- Produces: `BookParser.parse(metadata: BookMetadata, openStream: () -> InputStream): ParsedBook` and `TxtDecoder.decode(bytes: ByteArray): String`.

- [ ] **Step 1: Write decoder tests using real encoded bytes**

```kotlin
class TxtDecoderTest {
    @Test fun decodesUtf8() {
        assertEquals("第一章 雨夜", TxtDecoder.decode("第一章 雨夜".toByteArray(Charsets.UTF_8)))
    }

    @Test fun decodesGb18030() {
        val bytes = "第一章 雨夜".toByteArray(Charset.forName("GB18030"))
        assertEquals("第一章 雨夜", TxtDecoder.decode(bytes))
    }
}
```

- [ ] **Step 2: Write chapter parser tests**

```kotlin
class TxtBookParserTest {
    private val metadata = BookMetadata("雾隐长安", "林渡")

    @Test fun splitsCommonChineseChapterHeadings() {
        val source = "序言\n风起。\n第十二章 城门夜雨\n雨落长街。\n第13回 故人\n灯火未眠。"
        val parsed = TxtBookParser().parse(metadata) { source.byteInputStream() }
        assertEquals(listOf("序章", "第十二章 城门夜雨", "第13回 故人"), parsed.chapters.map { it.title })
    }

    @Test fun treatsUnsectionedTextAsOneChapter() {
        val parsed = TxtBookParser().parse(metadata) { "只有一段正文。".byteInputStream() }
        assertEquals(1, parsed.chapters.size)
        assertEquals("正文", parsed.chapters.single().title)
    }
}
```

- [ ] **Step 3: Run both test classes and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.iread.novel.core.parser.*Test"
```

Expected: FAIL because decoder and parser implementations are missing.

- [ ] **Step 4: Implement strict UTF-8 with GB18030 fallback**

`TxtDecoder` first decodes with a `CharsetDecoder` configured with `CodingErrorAction.REPORT`; on `CharacterCodingException`, it retries with `Charset.forName("GB18030")`. It removes an initial BOM and normalizes CRLF/CR to LF. If both decoders fail, it throws `UnsupportedTextEncodingException`.

`TxtBookParser` uses this anchored heading expression:

```kotlin
private val chapterHeading = Regex(
    pattern = "^\\s*第[0-9零一二三四五六七八九十百千万两]+[章回节卷部篇][^\\n]{0,40}$",
)
```

Text before the first heading becomes `序章`; blank sections are discarded; without a heading the full nonblank text becomes `正文`.

- [ ] **Step 5: Run parser tests and the full JVM suite**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.iread.novel.core.parser.*Test"
.\gradlew.bat testDebugUnitTest
```

Expected: all JVM tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/iread/novel/core/parser app/src/test/java/com/iread/novel/core/parser
git commit -m "feat: parse UTF-8 and GB18030 TXT books"
```

---

### Task 4: Room Persistence for Books, Chapters, and Progress

**Files:**

- Create: `app/src/main/java/com/iread/novel/data/db/Entities.kt`
- Create: `app/src/main/java/com/iread/novel/data/db/BookDao.kt`
- Create: `app/src/main/java/com/iread/novel/data/db/IReadDatabase.kt`
- Create: `app/src/main/java/com/iread/novel/data/db/DbMappers.kt`
- Test: `app/src/androidTest/java/com/iread/novel/data/db/BookDaoTest.kt`

**Interfaces:**

- Consumes: parsed books and progress values from Tasks 2 and 3.
- Produces: `BookDao.observeBookRows(): Flow<List<BookRow>>`, `BookDao.getChapters(bookId: String): List<ChapterEntity>`, `BookDao.upsertProgress(progress: ReadingProgressEntity)`, and `BookDao.deleteBook(bookId: String)`.

- [ ] **Step 1: Install an API 37 emulator image for framework tests**

With explicit user approval, run:

```powershell
sdkmanager.bat --sdk_root="C:\Users\fhj\AppData\Local\Android\Sdk" "emulator" "system-images;android-37;google_apis;x86_64"
avdmanager.bat create avd --force --name iread_api_37 --package "system-images;android-37;google_apis;x86_64"
emulator.exe -avd iread_api_37 -no-snapshot-save
```

Verify `adb devices` shows one device before running `connectedDebugAndroidTest`.

- [ ] **Step 2: Write the DAO deletion and observation test**

```kotlin
@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var database: IReadDatabase
    private lateinit var dao: BookDao

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IReadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.bookDao()
    }

    @After fun closeDatabase() = database.close()

    @Test fun deletingBookCascadesChaptersAndProgress() = runTest {
        dao.insertBook(BookEntity("book-1", "雾隐长安", "林渡", "TXT", "books/book-1.txt", "abc", 1, 1L, null))
        dao.insertChapters(listOf(ChapterEntity("book-1", 0, "第一章", "正文")))
        dao.upsertProgress(ReadingProgressEntity("book-1", 0, 2, -1, 2L))

        dao.deleteBook("book-1")

        assertTrue(dao.getChapters("book-1").isEmpty())
        assertNull(dao.getProgress("book-1"))
    }

    @Test fun observedRowReportsRemainingUnreadChapters() = runTest {
        dao.insertBook(BookEntity("book-2", "长夜行", "未知作者", "TXT", "books/book-2.txt", "def", 3, 1L, null))
        dao.insertChapters((0..2).map { ChapterEntity("book-2", it, "第${it + 1}章", "正文") })
        dao.upsertProgress(ReadingProgressEntity("book-2", 1, 0, 0, 2L))

        val row = dao.observeBookRows().first().single()

        assertEquals(2, row.unreadChapters)
    }
}
```

- [ ] **Step 3: Run the DAO test and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.data.db.BookDaoTest
```

Expected: FAIL because the database, entities, and DAO do not exist.

- [ ] **Step 4: Implement the Room schema**

Create:

```kotlin
@Entity(tableName = "books", indices = [Index(value = ["fingerprint"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val format: String,
    val sourcePath: String,
    val fingerprint: String,
    val totalChapters: Int,
    val importedAt: Long,
    val lastReadAt: Long?,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChapterEntity(val bookId: String, val chapterIndex: Int, val title: String, val body: String)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val characterOffset: Int,
    val lastCompletedChapterIndex: Int,
    val updatedAt: Long,
)

data class BookRow(
    val id: String,
    val title: String,
    val author: String,
    val totalChapters: Int,
    val unreadChapters: Int,
)
```

Enable foreign keys through Room, export schemas to `app/schemas`, and make the insert of one book plus its chapters a `@Transaction` DAO method. `observeBookRows()` performs a left join with progress and aliases this expression to `unreadChapters`:

```sql
MAX(b.totalChapters - MAX(COALESCE(p.lastCompletedChapterIndex, -1) + 1, 0), 0)
```

The DAO orders rows by `COALESCE(lastReadAt, importedAt) DESC`; `DbMappers.kt` maps each `BookRow` directly to `BookSummary`.

- [ ] **Step 5: Run DAO and JVM tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.data.db.BookDaoTest
.\gradlew.bat testDebugUnitTest
```

Expected: DAO test and all JVM tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/iread/novel/data/db app/src/androidTest app/schemas app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: persist books chapters and progress"
```

---

### Task 5: Duplicate-Safe Private TXT Import

**Files:**

- Create: `app/src/main/java/com/iread/novel/data/files/ImportSource.kt`
- Create: `app/src/main/java/com/iread/novel/data/files/AndroidImportSource.kt`
- Create: `app/src/main/java/com/iread/novel/data/files/PrivateBookFileStore.kt`
- Create: `app/src/main/java/com/iread/novel/data/repository/BookRepository.kt`
- Create: `app/src/main/java/com/iread/novel/data/repository/RoomBookRepository.kt`
- Create: `app/src/main/java/com/iread/novel/domain/ImportTxtBookUseCase.kt`
- Test: `app/src/test/java/com/iread/novel/domain/ImportTxtBookUseCaseTest.kt`
- Create: `app/src/test/java/com/iread/novel/testutil/ImportFakes.kt`

**Interfaces:**

- Consumes: `BookMetadataParser`, `TxtBookParser`, `BookDao`, and an `ImportSource` whose `open()` returns a fresh stream.
- Produces: `ImportTxtBookUseCase.invoke(source: ImportSource): ImportResult`, `BookRepository.observeBooks()`, `BookRepository.loadBook(bookId)`, `BookRepository.saveProgress(progress)`, `BookRepository.deleteBook(bookId)`, and `TimeSource.nowMillis()`.

- [ ] **Step 1: Write import success and duplicate tests**

```kotlin
class ImportTxtBookUseCaseTest {
    @Test fun importsCopyAndParsedChapters() = runTest {
        val repository = FakeBookRepository()
        val files = FakePrivateBookFileStore()
        val useCase = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource { 10L })
        val source = ByteArrayImportSource("雾隐长安 - 林渡.txt", "第一章 雨\n正文".toByteArray())

        val result = useCase(source)

        assertTrue(result is ImportResult.Imported)
        assertEquals("雾隐长安", repository.saved.single().title)
        assertEquals(1, repository.saved.single().chapters.size)
        assertEquals(1, files.finalized.size)
    }

    @Test fun rejectsARepeatedFingerprintWithoutSecondRecord() = runTest {
        val repository = FakeBookRepository(existingFingerprints = mutableSetOf("same"))
        val files = FakePrivateBookFileStore(fingerprint = "same")
        val useCase = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource { 10L })

        val result = useCase(ByteArrayImportSource("重复.txt", "正文".toByteArray()))

        assertEquals(ImportResult.Duplicate, result)
        assertTrue(repository.saved.isEmpty())
        assertEquals(1, files.discardedTemporaryCopies)
    }
}
```

Keep `ByteArrayImportSource`, `FakeBookRepository`, and `FakePrivateBookFileStore` under the test source set.

- [ ] **Step 2: Run import tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.domain.ImportTxtBookUseCaseTest
```

Expected: FAIL because the import contracts and use case do not exist.

- [ ] **Step 3: Implement the import contracts**

```kotlin
interface ImportSource {
    val displayName: String
    val sizeBytes: Long?
    fun open(): InputStream
}

fun interface TimeSource {
    fun nowMillis(): Long
}

sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult
    data object Duplicate : ImportResult
    data class Failed(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    EMPTY_FILE, UNSUPPORTED_FORMAT, UNREADABLE_FILE, UNKNOWN_ENCODING, NO_STORAGE,
}

interface BookRepository {
    fun observeBooks(): Flow<List<BookSummary>>
    suspend fun hasFingerprint(fingerprint: String): Boolean
    suspend fun insertImportedBook(book: ImportedBook)
    suspend fun loadBook(bookId: String): BookContent?
    fun observeProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun updateMetadata(bookId: String, title: String, author: String)
    suspend fun deleteBook(bookId: String)
}
```

`PrivateBookFileStore.stage()` streams the file once to `filesDir/importing/<uuid>.part`, calculates SHA-256 while copying, rejects zero bytes, and returns a `StagedBookFile`. `finalize()` moves the staged file to `filesDir/books/<bookId>.txt`. Both paths are descendants of `filesDir`; never accept a caller-provided destination path.

`ImportTxtBookUseCase` verifies the `.txt` suffix case-insensitively, stages and hashes, rejects an existing fingerprint, parses the staged file, finalizes it, inserts the book and chapters, and removes the finalized copy if the database insertion fails. It maps exceptions to `ImportFailure` without exposing stack traces to UI.

`ImportFakes.kt` provides reusable real in-memory test implementations: `ByteArrayImportSource`, `FakePrivateBookFileStore`, and `FakeBookRepository`. The fake repository stores books and progress in `MutableStateFlow`, enforces fingerprint uniqueness, records metadata updates, and implements every method in `BookRepository`; it must not return unconstrained mocks.

- [ ] **Step 4: Run import and parser tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.domain.ImportTxtBookUseCaseTest
.\gradlew.bat testDebugUnitTest
```

Expected: import tests and complete JVM suite PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/iread/novel/data/files app/src/main/java/com/iread/novel/data/repository app/src/main/java/com/iread/novel/domain app/src/test/java/com/iread/novel/domain
git commit -m "feat: import TXT into private storage"
```

---

### Task 6: Approved Shelf and Settings Import Flow

**Files:**

- Create: `app/src/main/java/com/iread/novel/IReadApplication.kt`
- Create: `app/src/main/java/com/iread/novel/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/iread/novel/IReadApp.kt`
- Create: `app/src/main/java/com/iread/novel/ui/theme/IReadTheme.kt`
- Create: `app/src/main/java/com/iread/novel/ui/navigation/IReadNavHost.kt`
- Create: `app/src/main/java/com/iread/novel/ui/shelf/ShelfViewModel.kt`
- Create: `app/src/main/java/com/iread/novel/ui/shelf/ShelfScreen.kt`
- Create: `app/src/main/java/com/iread/novel/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/iread/novel/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/iread/novel/ui/shelf/ShelfViewModelTest.kt`
- Test: `app/src/androidTest/java/com/iread/novel/ui/ShelfSettingsFlowTest.kt`

**Interfaces:**

- Consumes: `BookRepository.observeBooks()` and `ImportTxtBookUseCase`.
- Produces: `ShelfUiState`, `SettingsUiState`, `ShelfScreen(onOpenBook, onOpenSettings)`, and `SettingsScreen(onBack, onImportUri)`.

- [ ] **Step 1: Write ViewModel state tests**

```kotlin
class ShelfViewModelTest {
    @Test fun exposesRepositoryBooksAsShelfRows() = runTest {
        val repository = FakeBookRepository(
            books = MutableStateFlow(listOf(BookSummary("1", "雾隐长安", "林渡", 21, 24))),
        )
        val viewModel = ShelfViewModel(repository, backgroundScope)

        assertEquals("雾隐长安", viewModel.state.value.books.single().title)
        assertEquals(21, viewModel.state.value.books.single().unreadChapters)
    }
}
```

- [ ] **Step 2: Write Compose navigation test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ShelfSettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun iconOnlySettingsButtonOpensImportOptions() {
        compose.onNodeWithContentDescription("设置").assertIsDisplayed().performClick()
        compose.onNodeWithText("导入文件").assertIsDisplayed()
        compose.onNodeWithText("扫描文件夹").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run both tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.ui.shelf.ShelfViewModelTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.ui.ShelfSettingsFlowTest
```

Expected: FAIL because the ViewModel and Compose screens are missing.

- [ ] **Step 4: Implement production dependencies and navigation**

`AppContainer` creates one `IReadDatabase`, `RoomBookRepository`, `PrivateBookFileStore`, and `ImportTxtBookUseCase`. `IReadApplication` owns the container. ViewModels receive dependencies through explicit factories; do not add Hilt or another container.

Task 6 adds `android:name=".IReadApplication"` to the manifest. Use routes without an additional serialization plugin:

```kotlin
object Routes {
    const val Shelf = "shelf"
    const val Settings = "settings"
    const val Reader = "reader/{bookId}"
    fun reader(bookId: String) = "reader/${Uri.encode(bookId)}"
}
```

Navigation begins at the shelf. System back from settings returns to shelf.

- [ ] **Step 5: Implement the approved shelf UI**

Render one `LazyColumn` with a title `我的书架`, an icon-only `Icons.Outlined.Settings` button with content description `设置` and minimum 48 dp Compose touch target, and no bottom navigation. Each row renders generated cover artwork, title, `作者 · N章未读`, and an icon-only horizontal three-dot menu.

The menu contains `编辑书籍信息` and `删除本书`. In Phase 1, editing persists title and author through `BookRepository.updateMetadata`. Deletion shows the book title in a confirmation dialog, moves the private copy into an application-owned quarantine directory, deletes the Room record, restores the file if the database operation fails, and permanently removes the quarantined copy only after Room succeeds. The empty state contains `书架还是空的` and directs users to settings.

- [ ] **Step 6: Implement settings and the real file picker**

`SettingsScreen` shows `本地书库` and `阅读偏好` groups. `导入文件` invokes `ActivityResultContracts.OpenMultipleDocuments()` with MIME types `text/plain` and `application/octet-stream`. Each returned URI becomes `AndroidImportSource`; imports run sequentially on `Dispatchers.IO`, and state reports importing count, imported count, duplicate, and failure messages. `扫描文件夹` remains visible but disabled with supporting text `下一阶段开放`; this limitation is Phase 1 only.

- [ ] **Step 7: Run UI, ViewModel, and complete tests**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
```

Expected: all tests PASS and debug APK builds.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/iread/novel app/src/test/java/com/iread/novel/ui app/src/androidTest/java/com/iread/novel/ui
git commit -m "feat: add bookshelf and TXT import settings"
```

---

### Task 7: Basic TXT Reader and Restart-Safe Progress

**Files:**

- Create: `app/src/main/java/com/iread/novel/ui/reader/ReaderViewModel.kt`
- Create: `app/src/main/java/com/iread/novel/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/iread/novel/ui/navigation/IReadNavHost.kt`
- Modify: `app/src/main/java/com/iread/novel/data/repository/BookRepository.kt`
- Modify: `app/src/main/java/com/iread/novel/data/repository/RoomBookRepository.kt`
- Test: `app/src/test/java/com/iread/novel/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**

- Consumes: `BookRepository.loadBook(bookId)`, `BookRepository.observeProgress(bookId)`, and `BookRepository.saveProgress(progress)`.
- Produces: `ReaderUiState`, `ReaderViewModel.openChapter(index)`, `ReaderViewModel.updateCharacterOffset(offset)`, and `ReaderScreen(state, onBack, onOpenChapter, onOffsetChanged)`.

- [ ] **Step 1: Write progress restoration and chapter completion tests**

```kotlin
class ReaderViewModelTest {
    @Test fun restoresSavedChapterAndOffset() = runTest {
        val repository = FakeBookRepository(
            progress = ReadingProgress("book-1", 2, 140, 1),
        )
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)

        assertEquals(2, viewModel.state.value.chapterIndex)
        assertEquals(140, viewModel.state.value.characterOffset)
    }

    @Test fun movingForwardMarksPreviousChapterCompleted() = runTest {
        val repository = FakeBookRepository(progress = ReadingProgress("book-1", 0, 30, -1))
        val viewModel = ReaderViewModel("book-1", repository, backgroundScope)

        viewModel.openChapter(1)

        assertEquals(0, repository.savedProgress.last().lastCompletedChapterIndex)
    }
}
```

- [ ] **Step 2: Run reader tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.ui.reader.ReaderViewModelTest
```

Expected: FAIL because `ReaderViewModel` and reader state are missing.

- [ ] **Step 3: Implement reader state and persistence**

```kotlin
data class ReaderUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val characterOffset: Int = 0,
    val errorMessage: String? = null,
)
```

The ViewModel loads the book and saved progress in `viewModelScope`, clamps indices and offsets, saves immediately on chapter change, and debounces offset saves by 500 ms. `onCleared()` launches no new coroutine; the UI calls `flushProgress()` from a lifecycle observer when the app receives `ON_STOP`.

- [ ] **Step 4: Implement the Phase 1 reader UI**

Use a vertically scrolling chapter page with warm paper yellow background, serif Chinese text, chapter title, two-em paragraph indentation, and comfortable line spacing. Show a compact top bar with back navigation and book title. A bottom chapter navigator provides previous/next chapter actions and `第 N / M 章`.

Track the first visible paragraph and its character start offset using `LazyListState`; call `onOffsetChanged` only when the anchor changes. The full pagination mode, theme switcher, typography controls, directory sheet, and bookmark controls belong to the Phase 2 plan, so do not add temporary fake controls.

- [ ] **Step 5: Run reader and complete tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.iread.novel.ui.reader.ReaderViewModelTest
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/iread/novel/ui/reader app/src/main/java/com/iread/novel/ui/navigation app/src/main/java/com/iread/novel/data/repository app/src/test/java/com/iread/novel/ui/reader
git commit -m "feat: read TXT chapters and restore progress"
```

---

### Task 8: Phase 1 End-to-End Acceptance and Handoff

**Files:**

- Create: `app/src/androidTest/java/com/iread/novel/PhaseOneJourneyTest.kt`
- Create: `app/src/androidTest/java/com/iread/novel/TestIReadApplication.kt`
- Create: `app/src/androidTest/AndroidManifest.xml`
- Create: `README.md`
- Modify: `.gitignore`

**Interfaces:**

- Consumes: the complete Phase 1 app.
- Produces: a repeatable acceptance test, installation notes, and a debug APK.

- [ ] **Step 1: Write the end-to-end journey test**

Create an instrumented test that seeds a temporary UTF-8 TXT through the same `ImportTxtBookUseCase`, launches `MainActivity`, verifies `雾隐长安`, `林渡 · 2章未读`, opens the book, advances to chapter two, recreates the activity, and verifies chapter two is restored. Use production Room and private file storage under the test application; clear only the test application's own database and files in `@After`.

- [ ] **Step 2: Run the journey test before adding any test-only wiring**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.PhaseOneJourneyTest
```

Expected: FAIL because the test application override does not exist yet.

- [ ] **Step 3: Complete the smallest test boundary and rerun**

Make the application composition root explicitly replaceable:

```kotlin
open class IReadApplication : Application() {
    lateinit var container: AppContainer
        protected set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer = DefaultAppContainer(this)
}
```

`TestIReadApplication` overrides `createContainer()` and supplies an in-memory Room database plus `File(cacheDir, "phase-one-test")` as the private book root. `app/src/androidTest/AndroidManifest.xml` sets `android:name=".TestIReadApplication"`. Production continues to use the normal database and `filesDir`; no cleanup-only method is added to a production class. Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.PhaseOneJourneyTest
```

Expected: PASS.

- [ ] **Step 4: Document local build and manual acceptance**

`README.md` must list prerequisites, `local.properties` SDK path setup, test commands, build command, APK path, supported format, and the fact that originals are never deleted. Include this manual checklist:

```text
1. Launch with no books and verify the empty shelf.
2. Open the icon-only settings button and import UTF-8 and GB18030 TXT files.
3. Verify title, author, and unread count on the shelf.
4. Open a book, move to another chapter, close the app, and verify restoration.
5. Edit metadata and verify it survives restart.
6. Delete a book, then verify the original source file still exists.
7. Disable network connectivity and repeat import and reading.
```

`.gitignore` includes `.gradle/`, `local.properties`, `build/`, `**/build/`, `.idea/`, `captures/`, `.android-sdk/`, and `*.jks`.

- [ ] **Step 5: Run the full verification suite from a clean build**

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Expected: every command exits 0; no failing tests; `app-debug.apk` exists.

- [ ] **Step 6: Install the APK and perform the manual checklist**

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.iread.novel/.MainActivity
```

Record any visual or device-specific issue before marking Phase 1 complete.

- [ ] **Step 7: Commit**

```powershell
git add README.md .gitignore app/src/androidTest
git commit -m "test: verify TXT reader vertical slice"
```

---

## Follow-Up Plans After Phase 1

1. **Phase 2 — Full reader experience:** precise left/right pagination, vertical mode switching, warm/blue/night themes, font size, line spacing, directory sheet, bookmarks, and progress-anchor preservation during reflow.
2. **Phase 3 — EPUB and complete settings:** EPUB container/OPF/spine/TOC parsing, embedded covers, folder scanning, persistent default preferences, final delete recovery, performance tests, and release APK preparation.

Each phase starts only after the previous phase has a passing automated suite and a usable installed build.
