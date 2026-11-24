# VIBE MUSIC — Offline Hi-Fi Android Music Player

A complete, production-ready offline Android music player built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Media3 ExoPlayer**, **Room Database**, **DataStore**, and **Clean Architecture + MVVM**.

## Key Features
- **100% Offline Audio Engine**: Plays MP3, FLAC, WAV, M4A, AAC, OGG, OPUS, and WMA via Android Media3 `ExoPlayer` with gapless playback, audio focus management, and automatic pause on headphone unplug.
- **Background & Lock-Screen Controls**: Foreground `MediaLibraryService` with `MediaSession` supporting lock-screen controls, notification media art, Bluetooth/wired headsets, and Android Auto.
- **AMOLED Dark & Custom Themes**: Deep AMOLED black (`#09090D`), dark charcoal (`#15151D`), Electric Purple (`#A855F7`), Neon Blue (`#38BDF8`), Light Mode, System Theme, and 6 selectable accent colors.
- **Hardware 5/10-Band Equalizer**: Dynamically queries device audio hardware (`Equalizer`, `BassBoost`, `Virtualizer`) and only displays supported bands and effects.
- **Full Library & Folder Browser**: MediaStore scanner with pull-to-refresh, sorting (Title, Date Added, Duration, Artist, Album), folder browser, and genre tabs.
- **Smart & Custom Playlists**: Persistent Room database for custom playlists (create, rename, delete, reorder tracks) plus automatic Smart Playlists (Favorites, Recently Played, Most Played, Recently Added).
- **Synced LRC & Embedded Lyrics**: Automatically reads sibling `.lrc` / `.txt` files or embedded lyrics tags with tap-to-seek support.
- **Premium Extras**: Sleep timer, playback speed (`0.75x` – `2.0x`), queue management, JSON backup/restore, share audio files, set ringtone, exclude folders/tracks, and Keep Screen On.

## Build ARM64 APK on GitHub Actions (No PC/Laptop Needed)

This repository includes `.github/workflows/build-arm64-release.yml` pre-configured to compile and sign **only the `arm64-v8a` release APK** and publish it automatically to your repository's **Releases** section:

1. Extract `VibeMusic-Android-Project.zip` on your phone (using ZArchiver or your Files app).
2. Create a new repository on [GitHub](https://github.com) using your mobile browser.
3. Upload all extracted project files (including the hidden `.github/workflows/build-arm64-release.yml` file) to the `main` branch, or push via a mobile Git client (like Termux or MGit).
4. Open the **Actions** tab in your GitHub repository to watch the **Build & Release VIBE MUSIC (ARM64 APK)** workflow run (or tap **Run workflow** manually).
5. Once complete, open the **Releases** section of your repository and download **`VibeMusic-arm64-v8a-release.apk`** directly to your phone and install it!
