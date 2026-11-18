# Game Recorder (arm64)

Low-FPS-drop screen recorder: hardware MediaCodec (H.264/HEVC), 480p-1080p, 24-60 FPS,
sirf device internal audio (mic nahi). Third-party library nahi, sirf Android ka apna hardware pipeline.

## GitHub se APK banana (phone se)
1. GitHub par naya repo banao (Add file > Upload files se is zip ke andar ki saari files upload karo,
   `.github` folder aur `keystore` folder bhi - hidden folder ke liye zip ko pehle extract karna padega).
2. Commit/push hote hi Actions chalu: tab "Actions" mein progress dekho (~4-6 min).
3. Khatam hone par repo ke **Releases** mein `GameRecorder-arm64-v8a.apk` milega. Artifacts use nahi hote.

Agar Release na bane: Settings > Actions > General > Workflow permissions > "Read and write permissions".

## Use
- Android 10+ chahiye. Settings choose karo > "Recording shuru karo" > permission Allow.
- Game kholo. Rokne ke liye notification mein **Stop**. Video: Movies/GameRecorder.

## Notes
- Internal audio sirf un apps ki aati hai jo capture allow karte hain. Kuch games (aur calls) block karte hain, tab silent audio aayega.
- Landscape game ke liye "Landscape" orientation rakho.
- Agar encoder 1080p60 support na kare to app apne aap 30 FPS / 720p par fallback karta hai.
- FPS drop kam rakhne ke liye: H.264 + 12-20 Mbps rakho.
- APK debug-style keystore se sign hota hai (keystore/release.jks), isliye har build update ki tarah install hota hai.
