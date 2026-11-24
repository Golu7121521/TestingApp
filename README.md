# ScreenRecorder Android App (ARM64 + GitHub Actions)

Complete Android screen recording application built in Kotlin with automatic GitHub Actions CI/CD configured to compile and publish **only the `arm64-v8a` APK** directly to **GitHub Releases**.

## Features
- Native `MediaProjection` + `MediaRecorder` H.264/AAC capture
- Scaled up to 1080p at 30 FPS or 60 FPS
- Optional microphone recording toggle
- Notification shade **Stop Recording** button
- Saves directly to `Movies/ScreenRecorder` (visible immediately in your Gallery)
- GitHub Actions workflow (`build.yml`) builds and signs an `arm64-v8a`-only APK and attaches it to **Releases**

## How to Build on Phone Without a PC
1. Create a new repository on GitHub (via your mobile browser).
2. Extract this ZIP on your phone and upload all files (including the `.github/workflows/build.yml` file) to your repository, **or** use a mobile Git client like Termux / GitJournal / Working Copy / Acode.
3. Ensure `.github/workflows/build.yml` is present in the repo.
4. Go to the **Actions** tab on GitHub—the workflow starts automatically (or tap **Run workflow**).
5. Once the workflow finishes, open the **Releases** section on your repository page and download `ScreenRecorder-v1.0.x-arm64-v8a.apk`.
