# SaavnTune (Personal Android Music App)

An Android music streaming and downloading app built with **Kotlin**, **Jetpack Compose**, and **Media3 ExoPlayer**, powered directly by the JioSaavn API with built-in 320kbps audio stream decryption.

## Features
- **Online Streaming**: Search any song, artist, or album and stream directly in high quality.
- **Offline Downloads**: Download songs straight to your phone's `Music/SaavnTune` folder and play them offline from the **Downloads** tab.
- **Background Playback**: Powered by Media3 `MediaSessionService` with lock-screen and notification controls.
- **ARM64 Optimized**: Configured to build only `arm64-v8a` for a lightweight APK.
- **Zero-Artifact GitHub Releases**: GitHub Actions compiles the release APK and publishes it directly under **Releases** (bypassing GitHub Actions artifact storage limits).

## How to Build APK via GitHub Actions (No PC Required)
1. Extract this ZIP file on your phone (using ZArchiver or your file manager).
2. Create a new repository on [GitHub](https://github.com/new) (Public or Private).
3. Upload all extracted files and folders (make sure the `.github/workflows/build-apk.yml` file is included!) to the repository.
4. Go to the **Actions** tab in your repository and trigger **Build ARM64 APK & Publish Release** (or push any commit).
5. Once the workflow finishes, open the **Releases** section of your repository and download `SaavnTune-arm64-v8a.apk`.
