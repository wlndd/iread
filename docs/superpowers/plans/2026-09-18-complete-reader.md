# Complete the approved iRead specification

Goal: deliver the previously approved Android reader features, including real horizontal pagination, scroll mode, persistent preferences/bookmarks, directory navigation, folder import and EPUB cover/TOC support.

Spec: ../specs/2026-09-15-iread-android-design.md. The user explicitly asked to complete that scope; no new product decision is required.

Architecture: retain Kotlin/Compose and Room. Add DataStore preferences and a non-destructive Room migration. Paginate with actual measured text dimensions and stable character offsets. Folder scanning is confined to a user-selected SAF tree. Keep original files unchanged.

## Parallel ownership and contracts

1. Data task owns core/model, data/db, data/repository, data/preferences, Gradle dependencies, shared test fakes and related tests. Add ReaderMode(PAGED, SCROLL), ReaderTheme(PAPER, BLUE, NIGHT), ReaderPreferences(theme=PAPER, mode=PAGED, fontSize=19, lineSpacing=1.7f), Bookmark(bookId, chapterIndex, characterOffset, snippet, createdAt). ReadingProgress gains mode: ReaderMode? = null. BookSummary gains sourcePath: String? = null. BookRepository adds loadBookIndex(bookId): BookContent?, loadChapter(bookId,index): Chapter?, observeBookmarks(bookId): Flow<List<Bookmark>>, upsertBookmark(bookmark), removeBookmark(bookId,chapterIndex,characterOffset). ReaderPreferencesStore exposes preferences: Flow<ReaderPreferences> and suspend update(value). DataStoreReaderPreferencesStore(File) implements it. Add migration preserving current books/chapters/progress; bookmarks cascade on deletion. Run migration and persistence tests.
2. Reader task owns ui/reader and reader tests. Use data contracts above; default paged mode, actual text layout based pagination, tap zones/swipe, adjacent-chapter transitions, stable anchor on reflow, three themes, font/spacing controls, mode control, center-toggle toolbars, TOC and bookmarks. ReaderViewModel loads chapter index and only current/adjacent bodies. Keep its existing construction compatible by adding optional preferencesStore as trailing parameter. ReaderScreen accepts onPreferencesChanged, onToggleBookmark, onOpenBookmark, onPositionChanged callbacks; preserve existing chapter/back/offset callbacks. Add focused ViewModel and pagination/UI tests.
3. Import task owns EPUB parser, folder scanner, shelf cover rendering and related tests. Parse EPUB nav/NCX titles and embedded cover safely. Shelf reads cover from private sourcePath; use sampled decode/cache and generated cover fallback. Add FolderScanner(ContentResolver).scan(treeUri): FolderScanResult with uris and warnings, bounded traversal, user-selected tree only, TXT/EPUB filter. Main controller integrates scanner into settings.
4. Controller owns AppContainer, navigation and settings UI/ViewModel integration, release docs/version/artifact. Settings becomes a simple list with import, folder scan and persistent default theme/mode/font/spacing controls. Wire migration/preferences to container, bind reader actions, ensure back/background saves complete. Update UI acceptance tests for the final interactions.

## Verification and delivery

- [x] Data migration, preference persistence, bookmark cascade and chapter-window tests.
- [x] Pagination no-loss/no-overlap, resized text anchor, cross-chapter and restart tests.
- [x] Folder filtering/errors, EPUB nav/NCX/cover tests.
- [x] Full JVM suite, connected tests on verified iread_api_37 emulator-5560, lint and APK build.
- [x] Inspect actual reader screenshots in yellow/blue/night and paging/settings layouts.
- [x] Install version 1.2 APK, record limitations honestly, and copy named artifact to D:/iread/releases.

Use apply_patch for edits, preserve user changes, never target unrelated devices. Execute independent file owners in parallel using the dispatching-parallel-agents workflow; serialize Gradle/device runs through controller. Review each returned implementation and run integration checks before delivery.
