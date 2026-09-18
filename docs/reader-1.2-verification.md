# iRead 1.2.3 verification

Verified on 2026-09-18 with the official iread_api_37 AVD at emulator-5560 (Android API 37).

## Delivered behavior

- Offline TXT and unencrypted text EPUB import, including individual files and bounded recursive SAF folder scans.
- EPUB OPF/spine parsing, EPUB 3 nav and EPUB 2 NCX labels, fragment sections, and embedded shelf covers.
- Measured horizontal pagination by default with side taps, horizontal swipes, cross-chapter turning, and center-toggled controls.
- Optional vertical scrolling, warm paper/blue/night themes, font size and line spacing with anchor-preserving reflow.
- Directory navigation, persistent bookmarks, per-book position/mode, global defaults, and adjacent-chapter body loading.
- Non-destructive Room schema 1-to-2 migration retaining books, chapters and progress; bookmark cascade on book deletion.
- Custom adaptive launcher icon using the teal iRead wordmark and amber bookmark.
- Bottom chapter/page counters occupy a dedicated strip below the measured text while controls are hidden; the control panel replaces them as an overlay.

## Automated evidence

- JVM: 60 tests, 0 failures, 0 errors.
- Connected Android: 14 tests, 0 failures, 0 errors.
- Lint: 0 errors.
- Build: assembleDebug successful.
- Pixel regression: the pre-fix renderer drew 3,855 next-page pixels below the last complete line; the fixed renderer draws zero. Rendering is clipped to the actual page boundary as well as the viewport.

The suites cover pagination without lost/overlapping text, anchor reflow, cross-chapter transitions, restart restoration, theme/mode preferences, bookmarks, migration, folder traversal and import, TXT/EPUB journeys, nav/NCX labels and cover decoding.

## Visual evidence

Screenshots were captured at 1080 x 2400. Their dominant colors match the intended surfaces:

- paper: #FFF8E7
- blue: #E8F0F5
- night: #171A1D

The Pixel launcher accessibility tree exposes the installed application twice as iRead, including the normal app-list icon and the predicted-app icon.

## Known limits

This is a debug-signed personal-use build. EPUB reading is text-focused: original CSS, inline illustrations, audio, fixed-layout EPUB and DRM/font obfuscation are unsupported. Physical-device and long-duration performance testing remain separate from the completed API 37 emulator acceptance.
