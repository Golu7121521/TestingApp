# Business Music App (Android - ARM64)

A lightweight, dark-mode Android music player built for business environments (lobbies, cafes, showrooms, workspaces).

## Features
- **Business Stream Playlists**: Pre-configured curated streams in `TrackRepository.kt` (easily replace with your own MP3 stream URLs).
- **Local Audio Playback**: Tap **Local Files** to load MP3/audio tracks saved on your Android phone.
- **Full Player Controls**: Play/Pause, Next, Previous, Seek Bar, Loop/Repeat, and live Search/Filter.
- **ARM64-Only Build**: Configured via `abiFilters` and `splits.abi` to generate only `arm64-v8a` APKs.
- **Automatic GitHub Releases**: Every push to `main` builds a signed `arm64-v8a` release APK and attaches it to the **Releases** tab.

## How to Build on Phone (No PC Needed)
1. Create a new repository on GitHub (via browser or GitHub Mobile app).
2. Extract this ZIP on your phone and upload all project files (including the `.github/workflows/build-arm64-release.yml` file) to the repository.
3. Open the **Actions** tab in your GitHub repository to watch **Build ARM64 Release APK** run.
4. Once complete, open the **Releases** section of your repository and download the `-arm64-v8a.apk` file directly to your phone.
