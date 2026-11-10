# StreamX

A YouTube-inspired Android video streaming app, built with Kotlin and Jetpack Compose.
This project follows a phased plan. **This build contains Phase 1 only.**

> StreamX is not affiliated with YouTube or Google. It does not use YouTube's logo,
> branding, or the official YouTube API. Content will be retrieved through
> NewPipeExtractor in a later phase, subject to the source platform's terms.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Project setup, Gradle, Material 3 theme (dark/light), 5-tab bottom navigation, Home screen shell with category chips, pull-to-refresh, loading / empty / error states, ViewModel tests | **Done** |
| 2 | NewPipeExtractor integration, search, live video feed | Not started |
| 3 | Video details, ExoPlayer, fullscreen, quality selection | Not started |
| 4 | Shorts, channel screen, related videos | Not started |
| 5 | History, playlists, Watch Later, subscriptions (Room) | Not started |
| 6 | Background playback, Picture-in-Picture, Settings | Not started |
| 7 | Testing, performance, release APK | Not started |

In Phase 1 the Home feed shows its **empty state**, because no real data source is wired yet.
No fake videos are generated. The Shorts, Subscriptions, Library and Profile tabs are
placeholder screens with a short description.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (dependency injection)
- Navigation Compose
- Coil (image loading)
- Planned: Room, DataStore, NewPipeExtractor, Media3 ExoPlayer, MediaSessionService

Minimum SDK 26 (Android 8.0), target SDK 35.

## Project structure

```
app/src/main/java/com/streamx/app/
├── StreamXApplication.kt        Hilt entry point
├── MainActivity.kt              Hosts the Compose app and theme
├── core/
│   ├── common/                  Shared utilities (UiState)
│   └── designsystem/            Colors, typography, theme
├── navigation/                  Bottom-nav destinations and root scaffold
├── domain/
│   ├── model/                   Pure Kotlin models (Video)
│   └── repository/              Repository interfaces (VideoRepository)
├── data/
│   ├── repository/              Implementations (Phase 1: empty stand-in)
│   └── di/                      Hilt bindings
└── feature/
    ├── home/                    Home screen + ViewModel
    ├── components/              Reusable UI (VideoCard, skeleton, empty/error states)
    ├── shorts/ subscriptions/ library/ profile/   Placeholder tabs
app/src/test/                    ViewModel unit tests
```

## Build and run

Requirements: Android Studio (Ladybug or newer), JDK 17, Android SDK 35.

1. Unzip the project and open the `StreamX` folder in Android Studio.
2. Android Studio will offer to sync Gradle. Accept it. The Gradle wrapper files
   (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`) are not included in this archive.
   If they are missing, run `gradle wrapper --gradle-version 8.9` once from the project
   root, or let Android Studio generate them.
3. Run the `app` configuration on a device or emulator (API 26+).

Command line, after the wrapper exists:

```
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
```

## Notes on the spec

- **Not verified by compiling.** Phase 1 was written without an Android SDK in the
  environment. Run the build above first and report any errors.
- **Library versions** are pinned in `app/build.gradle.kts`. Check them against
  current stable releases before release.
- **Terms and copyright.** Extraction and playback will respect the source platform's
  terms. DRM, paywalls and access controls will not be bypassed.
