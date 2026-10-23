# Offline Video Player (Android)

Ek premium, edge-to-edge Android video player. Device me saved videos offline play karta hai, aur ab direct links (MP4, MKV, HLS .m3u8, DASH .mpd, RTSP) bhi play kar sakta hai.

## Features
- **Playback engine:** Media3 ExoPlayer (Google ka latest, best-maintained player library) — MP4, MKV, WebM, 3GP, OGG, TS, FLV, MP3/AAC/FLAC/WAV + HLS/DASH/RTSP streaming links, sab support karta hai.
- **"Play from link"** — home screen ke FAB se koi bhi video URL paste karke seedha play karo.
- **Open from other apps** — file manager / browser / WhatsApp me "Open with" ya "Share" se bhi video is app me khul jayega.
- Premium dark UI, edge-to-edge (status/nav bar ke peeche bhi content)
- Grid video list with real thumbnails (Glide)
- Full custom player controls: double-tap seek, gesture-based brightness/volume, speed control, lock, aspect ratio

## Project Structure
```
app/src/main/
 ├── java/com/example/offlineplayer/
 │     ├── MainActivity.kt      # Video grid + "play from link"
 │     ├── PlayerActivity.kt    # Full custom ExoPlayer screen
 │     ├── VideoAdapter.kt
 │     └── VideoItem.kt
 ├── res/layout, drawable, values/
 └── AndroidManifest.xml
.github/workflows/build.yml      # CI: builds a shrunk release APK → GitHub Release
```

## GitHub Actions se build & download kaise kare
Is baar APK **Actions artifact storage me nahi**, seedha **GitHub Releases** me jaata hai (Actions storage quota ka use nahi hota):

1. Poora folder apne GitHub repo me push karo.
2. Push hote hi workflow chalega: `gradle assembleRelease` se ek **minified + shrunk** release APK banega.
3. Workflow us APK ko turant ek **naye GitHub Release** (`v1.0.<run number>`) me attach kar dega.
4. Apne repo ke **"Releases"** tab (right sidebar, ya `github.com/<user>/<repo>/releases`) me jaake latest release se `app-release.apk` download kar lo.

> Release build **debug key se signed** hai (CI me koi real keystore nahi hai), isliye seedha install ho jayega — bas pehli baar phone me "install unknown apps" allow karna padega.

## App size chhota kaise rakha
- `minifyEnabled true` + `shrinkResources true` (R8 se dead code aur unused resources hat jaate hain)
- `resConfigs "en"` — sirf English strings, baaki language resources exclude
- META-INF license/notice files jaise extra junk APK se exclude
- App me koi native/JNI library nahi hai, isliye poora APK halka rehta hai

## Local build (optional)
Android Studio me kholna ho:
```
File → Open → is folder ko select karo → Gradle sync hone do → Run
```

## Notes
- `minSdk 21`, `targetSdk/compileSdk 34`.
- Android 13+ (API 33) par app runtime permission mangega (`READ_MEDIA_VIDEO`).
- Streaming link play karne ke liye internet permission hai, lekin app by default fully offline kaam karti hai — internet tabhi use hota hai jab tum khud koi link play karo.
