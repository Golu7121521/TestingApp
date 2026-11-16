# Universal Stream Player (Android)

Media3 ExoPlayer + yt-dlp based player (HLS, DASH, SmoothStreaming, MP4, MKV, RTSP, RTMP, DRM, custom headers, social links).

## Build on GitHub (no PC needed)
1. Create a new GitHub repo and upload all files/folders from this project (keep folder structure, including `.github`).
2. Commit to `main` or `master`. The workflow in `.github/workflows/build.yml` builds an arm64-v8a debug APK.
3. Open the **Actions** tab when it finishes, then download `UniversalStreamPlayer-arm64.apk` from **Releases**.

Note: the build runs on GitHub's servers, so the first build is not tested locally. If the Actions log shows an error, paste it to get a fix.
