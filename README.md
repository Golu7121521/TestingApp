# PlayVid 2.1 (minimal UI)

Android video player for **M3U8/HLS**, **MP4/direct URLs** and **local files**.
Kotlin + Jetpack Compose + Media3 (ExoPlayer). Package: `com.playvid`. minSdk 26, targetSdk 35.

## Build with GitHub Actions
1. Create a new GitHub repo and upload the *contents* of this folder (keep `.github/workflows/build.yml` at the repo root).
2. Push to `main`. Open the **Actions** tab -> "Build PlayVid".
3. When it is green, download the **PlayVid-debug** artifact, unzip, install `app-debug.apk`.

### Signed release (APK + AAB for Play Store)
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias playvid
base64 -w0 release.jks     # copy the output
```
Repo -> Settings -> Secrets and variables -> Actions, add:
`KEYSTORE_BASE64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (playvid), `KEY_PASSWORD`.
Next push also produces the **PlayVid-release** artifact (`.apk` and `.aab`). Keep `release.jks` safe: you need the same key for every future update.
Before each Play Store upload, increase `versionCode` in `app/build.gradle.kts`.

## Run locally (optional)
Open the folder in Android Studio (Ladybug or newer, JDK 17). It creates the Gradle wrapper for you. Run on a device/emulator.

## Architecture (clean, 3 layers)
```
playback/      PlaybackController (single ExoPlayer session shared by player screen + mini player)
domain/        models, repository interfaces, ValidateStreamUrlUseCase (pure Kotlin)
data/          Room (history), DataStore (settings)
di/            AppContainer (manual DI, no framework)
presentation/  Compose screens + ViewModels: home, files, settings, player  (see docs/UI_SPEC.md)
```

## Dependencies and why
| Dependency | Reason |
|---|---|
| media3-exoplayer, media3-exoplayer-hls | Official player; HLS/ABR, MP4, MKV, WebM, tracks |
| media3-ui | `PlayerView` video surface + subtitle rendering (controls are our own Compose UI) |
| media3-datasource-okhttp, okhttp | Explicit connect/read timeouts, redirects, custom User-Agent |
| room (+ksp compiler) | Watch history and resume positions |
| datastore-preferences | Settings |
| navigation-compose | Screen navigation |
| lifecycle-viewmodel/runtime-compose | ViewModels, lifecycle-aware state collection |
| compose BOM, material3, icons-extended | UI |
No Hilt, no image loaders, no extra networking libraries.

## Features map
- Home: URL field (clear button, validation), Play Video, Recent list with resume
- Files: system picker, search, list of opened local videos with size and duration
- Player: tap show/hide controls, double-tap +/-10 s, seek bar, mute, fullscreen, "Continue from mm:ss?" dialog
- More menu (only what the stream offers): quality, audio track, subtitles, speed, Picture-in-Picture, Fit/Fill
- Settings: default speed, auto quality, resume playback, theme, clear history, app version
- Mini-player: playback keeps going when you leave the player; slim bar with play/pause and close
- Files search icon, "Copy URL" in the More menu, snackbars ("Video added to history", "URL copied", ...)

## Platform limitations
- **Local files** use the system file picker (no storage permission). Format support = your phone's codecs; MP4/WebM/3GP are safe, MKV usually works, AVI/WMV/FLV often do not.
- **HTTP links** are allowed so plain-HTTP streams play, but HTTPS is recommended.
- **CORS** is a browser rule; it does not apply to a native Android app. A server can still refuse with 401/403 (expired token, Referer/User-Agent checks); the app shows that as "Access denied".
- **DRM** streams (Widevine etc.) are not supported and no bypass is attempted.
- **PiP** needs Android 8+ and is disabled if the user turned it off in system settings.
- Quality / Audio / Subtitles entries are hidden unless the stream really offers them.
- The Files tab lists videos you picked before (Android's picker gives access per file, not the whole storage).
- UI text is in English and hard-coded; move it to `strings.xml` when you add translations.
- This 2.0 UI was written without compiling it here; if Actions shows a compile error, send the log.
