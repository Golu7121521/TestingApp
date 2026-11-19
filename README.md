# FileForge

Kotlin + Jetpack Compose file manager (MT Manager jaisa, shuruaati version).

**Features:** dual-pane browsing, copy / move / delete / rename / new folder, zip + unzip (zip4j),
root mode (libsu), text editor (syntax highlighting, search/replace + regex, undo/redo).
**Target:** Android 8+ (tested design for Redmi 9 Prime, Android 10), sirf arm64-v8a APK.

## GitHub Actions se APK banana (phone se)

1. GitHub par naya repo banao (e.g. `FileForge`). **Private** rakhna better hai kyunki `keystore/release.jks` repo mein hai.
2. Phone par Termux install karo (F-Droid se), phir:
   ```
   pkg install git unzip
   termux-setup-storage
   cd ~ && unzip ~/storage/downloads/FileForge.zip && cd FileForge
   git init && git add -A && git commit -m "first commit"
   git branch -M main
   git remote add origin https://github.com/<USERNAME>/FileForge.git
   git push -u origin main
   ```
   Password ki jagah **Personal Access Token** (scopes: `repo` + `workflow`) use karo.
3. Push hote hi **Actions** tab mein build start hoga (~5-8 min).
4. Build khatam hone par repo ke **Releases** section mein `FileForge-v1.0.N-arm64-v8a.apk` milegi. Download karke install karo.

Artifacts use nahi hote; APK seedha Release mein jaati hai. Dobara build: Actions -> "Build and Release APK" -> Run workflow.

## Notes
- Android 10: pehli baar storage permission dega. Android 11+: "All files access" settings screen khulegi.
- Root mode: menu (⋮) -> Root mode. Root file ops shell commands se hote hain.
- Signing keystore password: `fileforge123` (app/build.gradle.kts mein). Apni keystore chahiye to `keystore/release.jks` replace karo.
