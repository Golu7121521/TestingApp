# PlayNova

Native Android video player (Kotlin, Jetpack Compose, Media3 ExoPlayer, Room).

- minSdk 24, targetSdk/compileSdk 34
- Android Studio: File > Open > is folder ko chuno, Gradle sync hone do, Run.
- Command line: `./gradlew assembleRelease` (APK: `app/build/outputs/apk/release/app-release.apk`, debug key se signed)
- GitHub: poora folder repo mein upload karo, Actions > Build APK > Run workflow. APK Releases mein aayega.

Structure: `data/` (Room, MediaStore, settings), `player/` (PlayerHolder), `ui/` (Compose screens).
Kya implemented hai aur kya nahi: app mein Settings > Features.
