# Link Player

Link paste karo → video play karo. Single-page Android app (Kotlin + Jetpack Compose + Media3 ExoPlayer).

## GitHub Actions se APK build
1. GitHub par naya repo banao, is zip ki saari files upload karo (`.github` folder samet).
2. `main` branch par push hote hi build chalu. Ya Actions tab → "Build APK" → Run workflow.
3. Build khatam hone par run page ke neeche **Artifacts → LinkPlayer-APK** download karo.
   `release/app-release.apk` install karo (debug key se signed).
