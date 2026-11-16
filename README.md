# Universal Video Player

A Kotlin + Jetpack Compose Android starter application using Media3 ExoPlayer, Room, Material 3, and an ARM64-only GitHub Actions release pipeline.

## What works in this first build

- Direct HTTP/HTTPS media URL validation and playback through Media3.
- Media3 HLS, DASH, SmoothStreaming and progressive playback modules.
- MP4, M4V, WebM, MP3 and Matroska MIME hints when recognizable from the URL.
- Home, Advance, History and Playlist destinations.
- Cookie, Referer, Origin and selected User-Agent values applied to Media3 HTTP requests for sources you are authorized to access.
- Room-backed playback history and playlist creation/deletion.
- Clipboard paste with an explicit confirmation dialog.
- Basic player controls, seek, replay and play/pause.
- Light/dark in-app color scheme toggle.
- ARM64 ABI filter and GitHub Release publishing workflow.

## Important limitations

This is a functional first release foundation, not a claim of universal website compatibility. A URL must resolve to media that Android/Media3 can access. Many social/video websites expose web pages rather than direct media URLs, and may require official SDKs or supported platform integrations. Private, encrypted, subscription-only, geo-restricted or DRM-protected content is not bypassed. This build does not implement DRM license configuration, external subtitle import, PiP actions, thumbnail extraction, complete track/quality menus, background audio service, playlist item management, or full settings/DataStore screens. These should be added as separately tested increments rather than represented as working controls.

MKV playback depends on codecs in the file and decoders available on the device. Media3 can demux supported Matroska streams, but cannot add missing device codecs.

## Build from a phone using GitHub

1. Create a new empty GitHub repository.
2. Upload the contents of this ZIP to the repository root (not the ZIP file itself). GitHub mobile browser supports **Add file → Upload files**; for a large number of files, use the repository web editor or GitHub's mobile app file upload flow.
3. Open **Settings → Actions → General** and ensure Actions are enabled.
4. Open **Actions → Build and Release ARM64 APK → Run workflow**.
5. The workflow builds `app-release-unsigned.apk`, checks the APK and native ABI directories, creates a SHA-256 checksum, and publishes both files to a GitHub Release.
6. Open **Releases** in the repository and download `UniversalVideoPlayer-arm64-v1.0.0.apk`.

The workflow uses the repository's automatically provided `GITHUB_TOKEN`; no personal access token is required. It deliberately does not use `upload-artifact`.

## Versioning

Edit `versionName` and `versionCode` in `app/build.gradle.kts`, and update the workflow `VERSION` value or push a matching `v*` tag. The workflow dispatch input can override the release version.

## Release signing

By default the workflow creates an installable APK signed with the runner's generated Android debug key. This is suitable for initial evaluation, but the key changes between fresh runners, so a later build may require uninstalling the previous app before installing the new one. For repeatable production updates, create a private release keystore and add GitHub Actions repository secrets: `ANDROID_KEYSTORE_BASE64` (base64-encoded keystore), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The workflow decodes it into the runner's temporary directory and Gradle signs with it. Never commit signing material; keep a secure backup of the same keystore.

## Build locally (optional)

Android Studio is not required for the cloud workflow. If building elsewhere, use JDK 17 and Android SDK Platform 36. The included `gradlew` bootstrap script downloads Gradle 8.13 when invoked.

## Privacy

Request header fields are held in UI memory only and are not written to Room history. Avoid entering secrets into a shared device. The current starter does not persist cookies or DRM credentials.

## License

MIT. See [LICENSE](LICENSE).
