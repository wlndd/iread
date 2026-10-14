# EPUB import — version 1.1

Implemented on 2026-09-18 in the existing `feature/iread-phase-1` worktree.

The existing private-copy import now dispatches `.epub` files to an EPUB parser. It reads OCF container metadata and the OPF manifest/spine, preserves spine reading order, extracts title/creator and XHTML paragraphs, and stores the actual EPUB format in the existing database field. TXT compatibility and existing database/file locations are preserved. The file picker includes EPUB and ZIP MIME types. Version code/name are 2/1.1.

Validation:

- Before implementation, `EpubImportTest` failed its successful-import assertion because EPUB was rejected by the TXT-only gate.
- Final `testDebugUnitTest`: 50 tests, zero failures/errors. Six new tests cover actual EPUB import, metadata, percent-encoded chapter references, spine order, paragraphs, duplicate detection, private-copy bytes, missing content, entity declarations, encryption, escaping references, and expanded-size rejection/cleanup.
- Final `connectedDebugAndroidTest`: 8 tests, zero failures/skips, 19.561 seconds. New `EpubJourneyTest` exercises the real Android XML implementation and application container, EPUB format persistence, book shelf, reading/chapter change, fresh activity reopen and progress restoration, duplicate import, deletion, and original-byte preservation. Existing TXT picker flow verifies EPUB MIME inclusion as well.
- `lintDebug`: zero errors, seven existing warnings.
- `assembleDebug`: successful. APK version 1.1, 19,348,814 bytes.
- Installed with `adb -s emulator-5560 install -r`: Success; cold launch: Status ok.
- Only the verified `iread_api_37` emulator at `emulator-5560` was targeted.
- Delivery copy: `D:/iread/releases/iRead-1.1-epub-debug.apk`.
- Build and delivery SHA-256: `6BB204BFF7EF103128993FB67448C834BABB3539866B676238567DCF3C8AFE93`.

Limits: no physical-phone or third-party EPUB corpus verification. EPUB tests use generated real ZIP/XML fixtures; the EPUB journey imports through the production use case, while the picker test supplies a TXT URI. Text-only conversion does not preserve images/CSS, custom DTD entities, fixed layout, DRM/font obfuscation, or a separate navigation/TOC UI. See README for the archive size limits. The final whole-branch review from the earlier Phase 1 work remains unfinished; this change was checked locally, not independently reviewed by a separate agent.
