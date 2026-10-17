# Vibe Player
Offline video player (Flutter, Android arm64).

## Build (no PC needed)
1. Is project ko GitHub repo me upload karo (zip extract karke, `.github` folder ke saath).
2. Repo > Actions > "Build arm64 APK" > Run workflow.
3. Kuch minute baad Releases me `VibePlayer-arm64.apk` milega.

## Implemented
MediaStore scan, thumbnails, search, sort, continue watching + progress, file picker,
player (play/pause, +-10s, seek bar, double-tap seek, speed, lock, rotate), resume position.

## Limits
Playback engine = video_player (ExoPlayer): MP4/MKV/WebM/MOV mostly OK, AVI & some codecs may fail.
Subtitles, audio tracks, PiP, playlists, settings: abhi pending.
