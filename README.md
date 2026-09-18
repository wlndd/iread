# iRead — TXT and EPUB reader (1.1)

An offline Android Chinese novel reader. Phase 1 supports local `.txt` files in UTF-8 (with or without BOM), GB18030, and GBK-compatible text. It imports a private copy, extracts chapters and filename metadata, shows unread chapters on the shelf, edits metadata, deletes shelf entries/private copies, and restores reading progress. The reader uses vertical scrolling and a warm paper background.

Version 1.1 also imports unencrypted, text-based `.epub` books. Settings → Import accepts TXT and EPUB. EPUB metadata supplies the title/author (filename metadata is the fallback), and the OPF spine determines chapter order. XHTML headings/document titles supply chapter titles. Paragraphs and inline text are converted into the existing vertical reader; duplicate detection, metadata editing, progress, and delete recovery are shared with TXT. No database migration or clearing existing books is required.

EPUB limits: text only; original CSS layout, embedded images/covers, audio, fixed-layout books, and a separate EPUB navigation/TOC interface are not implemented. Books with `META-INF/encryption.xml` (including font-obfuscated books) are rejected. XML must be UTF-8 or UTF-16 and well formed; custom DTD entities are rejected. Archive limits are 4,096 entries, 8 MiB per expanded entry and 32 MiB total. Invalid/unsupported books produce a visible failure and no shelf entry. Archives are never extracted to arbitrary filesystem paths or allowed to load external XML resources. Private filenames retain the legacy `.txt` suffix as opaque storage identifiers even when their bytes are EPUB; the database records the actual format.

Original source files are never deleted by iRead. Deleting a book removes its shelf data and app-private copy only. Import, metadata editing, deletion, and reading work offline; no account or network connection is needed. The first development build may need network access to download dependencies.

## Prerequisites

- Windows PowerShell, Git, and a JDK supported by Gradle 9.6 (JDK 17 or newer; acceptance was run with Oracle JDK 25.0.3). Set `JAVA_HOME` to your JDK if Java is not on `PATH`.
- Android SDK platform 37, build-tools 37.0.0 (the acceptance machine also has 36.0.0 installed), platform-tools (`adb`), and the Android Emulator. Install them through Android Studio SDK Manager or Android command-line tools.
- For connected tests, create the official Google APIs x86_64 API 37 AVD named `iread_api_37`; enable hardware virtualization. The tested device reports `sdk_gphone64_x86_64`.
- Use the committed `gradlew.bat`; a separate global Gradle installation is unnecessary. Dependency versions are pinned in `gradle/libs.versions.toml`.

Create an untracked `local.properties` in the project root, using your own SDK path:

```properties
sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

Gradle can use `local.properties`, but the emulator command below also needs the SDK path in the shell. Set `ANDROID_HOME` to the same SDK directory, then set a task-local Gradle cache and pin the official emulator:

```powershell
$env:ANDROID_HOME = 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = "$PWD\.gradle-task8"
$env:ANDROID_SERIAL = 'emulator-5560'
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd iread_api_37 -port 5560
```

Start the emulator in a separate terminal, wait until fully booted, and verify identity before installing or testing:

```powershell
adb -s emulator-5560 shell getprop ro.boot.qemu.avd_name
adb -s emulator-5560 shell getprop ro.build.version.sdk
adb -s emulator-5560 shell getprop ro.product.model
adb -s emulator-5560 shell getprop sys.boot_completed
```

Expected values are `iread_api_37`, `37`, `sdk_gphone64_x86_64`, and `1`. Put SDK `platform-tools` on `PATH` for the `adb` examples. Never install to or interact with the unrelated a55x endpoints `127.0.0.1:16384` or `emulator-5554`.

## Repeatable verification and build

From the project root, run each command and require exit code 0:

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Also verify actual test counts and failures in `app/build/test-results/testDebugUnitTest/` and `app/build/outputs/androidTest-results/connected/debug/`; an unavailable or still-booting device can prevent tests from running. HTML reports are under `app/build/reports/`. `clean` removes generated build output, not imported device books or source documents.

To run only the acceptance journey (quote the dotted property in PowerShell):

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.iread.novel.PhaseOneJourneyTest'
```

The test runner instantiates `TestIReadApplication` with in-memory production Room and `cacheDir/phase-one-test` private storage. Production repositories, parser, importer, shelf, reader, and progress persistence remain real. Cleanup touches only test-owned rows/files. The normal installed app uses persistent `iread.db` and `filesDir`. Activity recreation and fresh activity/reader reopening are tested; process restart is a separate installed-app manual check.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
adb -s emulator-5560 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5560 shell am start -n com.iread.novel/.MainActivity
```

The debug APK is for local acceptance, not a release-signed distribution.

## Manual acceptance checklist

Use local files, for example `雾隐长安 - 林渡.txt` with two Chinese chapter headings and distinct body paragraphs. Create one UTF-8 file and another GB18030 file with different contents so duplicate detection does not skip them. Keep originals in Downloads and record their hashes before/after deletion.

1. Launch with no books and verify the empty shelf.
2. Open the icon-only settings button and import UTF-8 and GB18030 TXT files, then a two-chapter unencrypted EPUB.
3. Verify title, author, and unread count on the shelf.
4. Open a book, move to another chapter, close the app, and verify restoration.
5. Edit metadata and verify it survives restart.
6. Delete a book, then verify the original source file still exists.
7. Disable network connectivity and repeat import and reading.

For a real process restart, leave the reader, force-stop this app on the pinned emulator, relaunch, and open the book. Record any visual/device-specific issue and any checklist step that was not actually verified. Restore device connectivity after the offline check.

## Later phases

Later work includes precise left/right pagination, vertical mode switching, warm/blue/night themes, font size, line spacing, directory sheet, bookmarks, and progress-anchor preservation during reflow, plus EPUB navigation/TOC and embedded covers, folder scanning, persistent preferences, performance tests, and release signing. EPUB container/OPF/spine text import was brought forward into version 1.1 at the user's request; the remaining features are not claimed by this APK.
