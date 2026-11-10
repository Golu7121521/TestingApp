# NewTube (YouTube Clone powered by TeamNewPipe Extractor)

Yeh Android project khaas **Redmi 9 Prime (Android 10 / API 29)** aur upar ke sabhi Android devices ke liye banaya gaya hai. Ismein **NewPipeExtractor (`com.github.TeamNewPipe:NewPipeExtractor`)** aur **Media3 ExoPlayer** ka use kiya gaya hai.

## Features Included
1. **Original YouTube Dark UI (`#0F0F0F`)**: YouTube logo, search bar, 16:9 video cards, duration badge, aur channel avatars.
2. **YouTube Video Player Controls**: Custom ExoPlayer overlay jisme Center Play/Pause, 10-second Rewind/Forward, Red Seekbar (`DefaultTimeBar`), Top-left Minimize button (`v`), aur Fullscreen toggle hai.
3. **YouTube-Style Bottom Mini Player**: Jab aap video ko minimize karte hain ya Back button dabate hain, toh video band nahi hota balki bottom navigation bar ke theek upar **Mini Player** mein chalta rehta hai (Thumbnail + Title + Channel + Play/Pause + Close button + Red progress bar ke saath).
4. **Slim 4-Tab Bottom Navigation Bar (`46dp`)**:
   - **Home**: Trending videos load karta hai.
   - **Shorts**: Short-form vertical videos load karta hai.
   - **Subscriptions**: Aapke subscribed channels ke videos dikhata hai.
   - **Library**: Aapki local Watch History dikhata hai.
5. **No PC Required (GitHub Actions Ready)**: Ismein `.github/workflows/android_build.yml` pre-configured hai jo bina PC ke cloud par APK build kar dega.

---

## Mobile Se GitHub Actions Par APK Kaise Build Karein? (Step-by-Step)

1. Apne Redmi 9 Prime phone mein **GitHub.com** (browser mein Desktop Mode on karke) kholein aur ek **New Repository** banayein (e.g., `NewTube-App`).
2. Is ZIP file (`NewTube_YouTube_Clone_Redmi9Prime.zip`) ko apne phone ke File Manager mein **Extract (Unzip)** karein.
3. Apne GitHub repository mein **Add file -> Upload files** par tap karein aur extract kiye huye saare folders (`.github`, `app`, `build.gradle`, `settings.gradle`, `gradle.properties`) upload karke **Commit changes** par tap karein.
   *(Tip: Agar mobile browser se `.github` hidden folder upload karne mein dikkat aaye, toh GitHub repo mein **Add file -> Create new file** par tap karein, file ka naam `.github/workflows/android_build.yml` likhein, aur uske andar workflow ka code paste karke commit kar dein).*
4. Commit hote hi upar **Actions** tab mein jayein. Wahan `Build NewTube APK (Redmi 9 Prime / Android 10)` workflow apne aap chalna shuru ho jayega.
5. 3 se 5 minute mein build green tick (`Success`) ho jayega. Workflow run par tap karein, neeche **Artifacts** section mein **`NewTube-Redmi9Prime-Debug-APK`** par tap karke APK download karein aur apne Redmi 9 Prime mein install kar lein!

## Version 1.2.0 – Naye features
- **Subscriptions save hote hain**: Subscribe karne par channel phone mein save hota hai (app band karne ke baad bhi).
- **Subscriptions tab**: subscribed channels ki latest videos dikhata hai. Khali ho to message aata hai.
- **Manage subscriptions**: Subscriptions tab ke header mein "Manage" se channel unsubscribe kar sakte hain.
- **Watch history save hoti hai**: Library tab mein history dikhti hai, "Clear" se khali ho jati hai.
- **Shorts grid**: Shorts tab mein 2-column portrait grid (sirf short videos).
- **Section header aur empty state**: Har tab ke liye message aur search results ka title.
- UI, colors, bottom nav, mini player aur player controls pehle jaise hi hain.
