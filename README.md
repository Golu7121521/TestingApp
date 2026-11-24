# Saavn Player (personal use)

Android music player that streams songs from JioSaavn's unofficial web API and lets you download them.

## Features
- Search songs and artists (all UI text in English)
- Stream online with background playback and media notification
- Download songs to the app's music folder and play them offline
- Only an arm64-v8a APK is built

## Build with GitHub Actions (no PC needed)
1. Create a new GitHub repository and upload all files from this project, keeping the folder structure (including `.github`).
2. Open the repo, go to the **Actions** tab, select **Build arm64 APK**, and click **Run workflow**.
3. When the run finishes, open **Releases** and download `app-arm64-v8a-release.apk` from the newest release.
4. Install it on your phone (allow "Install unknown apps" for your browser or file manager).

## Notes
- The APK is signed with the debug key. Because that key can differ between CI runs, if installing a new build fails with a signature conflict, uninstall the old version first.
- The JioSaavn endpoints are unofficial and may change or stop working. Check the terms of service and your local copyright laws before downloading content.
- Downloads go to the app's private music folder (Android/data/com.personal.saavnplayer/files/Music).
