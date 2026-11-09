# LiteTube

YouTube jaisa dark UI, NewPipeExtractor se video data (API key ki zaroorat nahi).

## Features
- Dark YouTube-style UI
- Bottom navigation: Home, Shorts, Subscriptions, Library
- Home: search + trending
- Video player (ExoPlayer) with YouTube-style controls
- Mini player bottom mein (tap karke full player kholo)
- Related videos list player ke neeche

## GitHub Actions se APK banana
1. GitHub par naya repository banao (e.g. LiteTube).
2. Is zip ka content repo mein upload karo (folder structure same rahe, `.github` folder bhi).
3. Push hote hi "Actions" tab mein "Build APK" chalega.
4. Complete hone par run page ke neeche "Artifacts" se `LiteTube-debug-apk` download karo.
5. zip kholo, `app-debug.apk` ko phone mein install karo (Settings > Install unknown apps allow karo).

## Notes
- Subscriptions aur Library abhi placeholder hain.
- Muxed (video+audio) streams use hote hain, isliye quality ~360p tak milegi. 720p+ ke liye DASH merge next step hai.
- NewPipeExtractor ka version (app/build.gradle.kts) latest tag se check karo: https://github.com/TeamNewPipe/NewPipeExtractor/releases
- YouTube ke changes se kabhi-kabhi extractor break ho sakta hai; tab extractor update karna padega.
- Personal use ke liye. Play Store par YouTube branding ke saath publish karna YouTube ToS ke against hai.
- NewPipeExtractor GPLv3 license mein hai; isliye project bhi GPLv3 ke terms follow karega agar distribute karo.
