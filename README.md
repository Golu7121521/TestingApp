# MyTune

Personal music streaming app (Kotlin, Jetpack Compose, Media3, Room) built entirely on GitHub Actions. Uses an unofficial JioSaavn API. For personal use only.

## Build and release (GitHub Actions only)

1. Create a new GitHub repository and push this whole folder to it.
2. Create a keystore (skip if you already have one):

   ```bash
   keytool -genkeypair -v -keystore mytune.jks -alias mytune \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Remember the keystore password, the alias (`mytune` above) and the key password.
   Do not commit the `.jks` file.
3. Convert it to base64 (one line):

   ```bash
   base64 -w0 mytune.jks      # macOS: base64 -i mytune.jks | tr -d '\n'
   ```
4. In the repo go to **Settings → Secrets and variables → Actions → New repository secret** and add:

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | output of the base64 command |
   | `KEYSTORE_PASSWORD` | keystore password |
   | `KEY_ALIAS` | key alias (e.g. `mytune`) |
   | `KEY_PASSWORD` | key password |
5. Release: either push a tag (`git tag v1.0.0 && git push origin v1.0.0`) or run the workflow manually from the **Actions** tab (Build Release APK → Run workflow).
6. The workflow publishes `MyTune-arm64-v8a-<version>.apk` straight to **Releases**. It never uses `actions/upload-artifact`.

The version is defined once in `version.properties` (`VERSION_NAME`, `VERSION_CODE`). Bump it before each release.

## Notes

* **Gradle wrapper:** the wrapper jar is a binary and was not included. The workflow generates `gradlew` and the jar (`gradle wrapper --gradle-version 8.9`). To build locally run `gradle wrapper --gradle-version 8.9` once.
* **API:** the base URL is a single constant in `data/api/ApiConfig.kt`. The default targets a saavn.dev-style host. Unofficial hosts come and go, so if requests fail, point it at a working instance (or self-host one). Home rows are built from searches because that API has no home endpoint; the queries are also in `ApiConfig`.
* **Downloads** are stored in app-specific external storage (no storage permission) and survive app restarts. Interrupted downloads resume from the partial file.
* Release builds are minified with R8 (rules in `app/proguard-rules.pro`).

## Project layout

```
app/src/main/java/com/mytune/app/
  data/api         Retrofit service, DTOs, JioSaavnApi, URL decryption helper
  data/db          Room entities and DAO
  data/repository  Library (favorites, playlists, recents) and DataStore settings
  domain           Models and the MusicRepository interface
  player           PlaybackService (Media3), PlayerController, StreamResolver
  download         DownloadRepository and DownloadWorker (WorkManager)
  di               AppContainer (manual DI)
  ui               Compose screens, components, theme, navigation
```

## What is new in 1.1.0

* First launch: onboarding page (country, 3+ languages, 3+ artists). Re-open it from Settings -> "Languages & artists".
* Personalised home feed (Just For You, Last Played, Your Artists, Trending, charts, artist playlists, albums, language rows). It follows the listening (artist weights in DataStore) and silently rebuilds after a few new plays, every 10 minutes and on pull-to-refresh.
* Podcasts tab is built by `HomeFeed.podcasts()` (the API has no podcast endpoint, so shows/episodes are searched as songs, albums and playlists). There is no separate server in this project: all backend logic lives in `data/api`.
* Equalizer (presets, bass, surround, 5 bands) in `audio/EqualizerManager` + `ui/components/EqualizerDialog`.
* Launcher icon colour follows the accent colour (one `activity-alias` per accent, see `IconManager`).
* Notification has Like and Close buttons. Close and swiping the app from Recents stop playback and end the process.
