# Universal Video Player

Android video player (Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, Room, DataStore) that you can
build **entirely from your phone** with GitHub Actions. The workflow publishes an ARM64 APK to GitHub Releases
and does **not** use workflow artifact storage.

> **Honest status:** this project was generated without access to Android build tools, so it has **not been
> compiled or run yet**. The first Actions run is its first real compile. If it fails, open the failed run, copy
> the red error lines and send them back to get a fix. Nothing in this README claims a feature was tested.

## 1. Put the project on GitHub (phone only)
1. Create a free account at github.com and open **New repository** (name it e.g. `UniversalVideoPlayer`, Public or Private).
2. In the repo, tap **Add file → Upload files** and upload the contents of the ZIP. Unzip it on your phone first
   (Files app), then upload the **folders and files**, keeping the structure. The folder `.github/workflows/`
   must end up containing `release.yml`. Hidden-folder tip: if your uploader skips `.github`, create the file via
   **Add file → Create new file**, type `.github/workflows/release.yml`, and paste the workflow text.
3. Tap **Commit changes**.

## 2. Run the build from a mobile browser
1. Open the repo → **Actions** tab (use "Desktop site" in your browser if the tab is hidden).
2. Choose **Build and Release ARM64 APK** → **Run workflow**, enter a version such as `1.0.0`, run it.
3. Wait roughly 8–15 minutes. Alternatively push a tag like `v1.0.0` to trigger it.

## 3. Download the APK
Open the repo → **Releases** → latest release → tap `UniversalVideoPlayer-arm64-vX.X.X.apk`. Allow
"Install unknown apps" for your browser when Android asks. `SHA256SUMS.txt` is attached to verify the file.

## 4. Persistent signing key (recommended)
Without secrets, every build uses a **temporary key**, so a new build cannot update an older install (uninstall first).
For a permanent key, generate a keystore once (any machine or an online tool you trust, or a one-off Actions
step), then add these repo secrets under **Settings → Secrets and variables → Actions**:
`RELEASE_KEYSTORE_BASE64` (base64 of the .jks/.p12), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`. Never commit a keystore (`.gitignore` blocks `*.jks`/`*.keystore`).

## 5. About ARM64
ABI filters are set to `arm64-v8a`. The current dependencies (Media3 software-free build, Compose, Room, Coil) ship
no native libraries, so the APK has none; the workflow checks this and fails the build if any x86/32-bit native
library ever appears. Devices running 32-bit-only ARM cannot be targeted by an "ARM64-only" release.

## 6. Gradle wrapper note
`gradle-wrapper.jar` is a binary that could not be generated here, so the workflow uses
`gradle/actions/setup-gradle` with Gradle 8.7 instead of `./gradlew`. `gradle-wrapper.properties` is included.

## Supported sources
- Direct files: MP4, M4V, MKV, WebM, MOV, 3GP, TS, MP3, M4A, AAC, OGG, Opus, FLAC, WAV (device decoders permitting)
- Streaming: HLS (`.m3u8`), MPEG-DASH (`.mpd`), SmoothStreaming (`.ism`); unknown extensions are probed by Content-Type
- Headers (Cookie, Referer, Origin, User-Agent) and DRM (Widevine / PlayReady / ClearKey) on the Advance tab

## Known limitations
- YouTube, Instagram, TikTok, Netflix, Vimeo and similar page links are intentionally rejected with an explanation.
  No DRM, login or paywall is bypassed.
- DRM only works if the device supports the scheme and you have a valid license URL.
- Background playback and Picture-in-Picture controls beyond play/pause are not implemented; there is no buffering-size setting.
- Playlist reordering uses up/down buttons rather than drag and drop.
- Minimum Android is 7.0 (API 24), a deliberate raise from 23 for reliable vector launcher icons.
- compileSdk/targetSdk are 34 to match the pinned Android Gradle Plugin 8.5.2.
