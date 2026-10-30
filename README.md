# YouTube NewPipe Real Android App (ARM64 + GitHub Releases Workflow)

Is project mein **NewPipeExtractor (`v0.24.2`)**, **Media3 ExoPlayer**, aur **Coil** add kiya gaya hai jisse yeh app real YouTube videos search, browse aur stream karta hai (bina किसी API key ke).

## Special GitHub Actions Configuration (Bina PC/Laptop ke build karne ke liye):
1. **Zero Artifact Storage Used**: `.github/workflows/android-release.yml` mein `actions/upload-artifact` ka use **nahi** kiya gaya hai. APK seedha aapke repo ke **Releases** section mein upload hoga.
2. **Only `arm64-v8a` APK**: `app/build.gradle.kts` mein ABI split lagaya gaya hai jo sirf `arm64-v8a` architecture ka APK build karta hai.
3. **Pre-Signed Release APK**: Release build par debug signing key auto-configured hai taki aap GitHub Releases se download karke seedha apne phone mein install kar sakein.

## Mobile se GitHub par kaise build karein:
1. Is ZIP file ko apne phone mein extract karein.
2. Ek nayi GitHub Repository banayein aur saari files (including `.github/workflows/android-release.yml` hidden folder) upload/push kar dein.
3. Repo ke **Actions** tab mein jayein — build apne aap start ho jayega.
4. Build pura hone par repo ke **Releases** section mein jayein aur `YouTubeNewPipe-arm64-v8a.apk` download karke install kar lein!
