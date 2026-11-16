# Universal Player

Paste almost any video link and play it. Built with Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer and yt-dlp (youtubedl-android).

## Build with GitHub Actions (no PC needed)
1. Create a new GitHub repo and upload ALL files of this project (keep the `.github` folder).
2. Repo -> Settings -> Actions -> General -> Workflow permissions -> "Read and write permissions" -> Save.
3. Repo -> Actions -> "Build and Release (arm64)" -> Run workflow -> enter a tag (e.g. v1.0).
4. After ~5-10 min the APK `UniversalPlayer-arm64-v8a.apk` appears under Releases. No artifacts are used.

If the build fails, open the failed step log and send the error text.

## Tabs
Home (paste link) | Advanced (headers, user agent, DRM) | History | Playlist (auto plays next item)

## Extras
Resume playback, brightness/volume swipe, subtitle language menu, swipe-to-delete with undo, history search, shuffle play, animated UI.
