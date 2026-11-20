# Universal Player (personal use)

Paste almost any video link and play it. Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer, yt-dlp.

## Build with GitHub Actions (no PC needed)
1. Upload ALL files to a **private** GitHub repo (keep `.github` and `app/release.keystore`).
2. Settings -> Actions -> General -> Workflow permissions -> "Read and write" -> Save.
3. Actions -> "Build and Release (arm64)" -> Run workflow -> tag (v1.0, then v1.1, v1.2 ...).
4. The APK `UniversalPlayer-arm64-v8a.apk` appears in Releases.

## Updating
Every build is signed with the same key (`app/release.keystore`), so friends can install a new
version over the old one without losing history or playlists. Use a new tag for each build.
Do not delete or replace the keystore, otherwise updates will fail to install.

## Sharing with friends
Send the APK. They must allow "Install unknown apps" for the app they open it from.
Works on arm64 phones (almost all phones from the last years), Android 8.0+.

## APK size
The APK is ~30 MB because it contains Python + yt-dlp (needed to open 1000+ sites).
To try a smaller build: Run workflow -> tick "Shrink APK" and use a NEW tag. Install and test it
(play a YouTube link, open History/Playlist) before sharing. If it crashes, run again without the tick.
