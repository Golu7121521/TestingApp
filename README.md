# TubeClone

YouTube jaisa dikhne wala Android app. Data NewPipe Extractor library se aata hai.

## GitHub par build karna (PC ki zarurat nahi)
1. GitHub par naya repository banao (Private/Public).
2. Is folder ki saari files upload karo (zip extract karke, ya "Add file > Upload files").
3. Repo ke "Actions" tab mein jao, "Build APK" workflow chalega.
4. Workflow khatam hone par "Artifacts" mein `TubeClone-debug-apk` download karo.
5. Zip khol kar `app-debug.apk` ko phone mein install karo
   (Settings > Apps > Special access > Install unknown apps, allow karna hoga).

## Features
- Bottom navigation: Home, Shorts, Subscriptions, Library
- Search, trending/search results
- Video player (ExoPlayer, YouTube jaise controls)
- Neeche mini player, tap karke full player
- Local subscriptions aur watch history

## Known limits
- Subscriptions sirf is phone mein save hote hain (Google login nahi).
- Shorts tab abhi grid view hai, full vertical swipe reels baad mein add ho sakte hain.
- YouTube ke changes se NewPipe Extractor toot sakta hai; tab library ka version update karna hoga.
