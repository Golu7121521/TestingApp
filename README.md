# Git Action Build

Personal Android app to manage GitHub repositories, edit files, push changes and
watch GitHub Actions builds (with a notification when a build passes or fails).

## Structure

| File | What it holds |
|---|---|
| `MainActivity.kt` | App start, token, permissions, GitHub API calls, shared UI helpers |
| `RepoScreen.kt` | Repository list, clone |
| `FileBrowserScreen.kt` | Folders, file rows (swipe to rename/delete), import, search |
| `EditorScreen.kt` | Sora editor: find/replace, save, push |
| `BuildScreen.kt` | Terminal: push steps, build watch, build log, APK download |
| `TextColorLanguage.kt` | Colors keywords, strings, numbers, comments, functions and types |
| `FileUtils.kt` | Pure helpers: zip extraction, log cleanup, progress text, file listing |
| `Fx.kt` | Matrix rain, swipe row, pull to refresh, download counter |

## Build and test

- Build: `gradle assembleDebug`
- Unit tests: `gradle testDebugUnitTest`

## Notes

- Data lives in `/storage/emulated/0/GitActonBuild`.
  `MANAGE_EXTERNAL_STORAGE` is needed on Android 11 and newer.
  On Android 10 the legacy storage flag in the manifest keeps access working.
- Build notifications appear while the app process is alive.
