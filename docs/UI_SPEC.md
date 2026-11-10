# PlayVid UI specification

**Principles:** minimal, dark-first, one subtle accent (#7F9CF5), near-black background (#0B0B0D),
white/light-gray text, muted-gray secondary text, 8/12/16 dp corners, 20 dp screen padding,
no gradients, no glass, no shadows, no decorative animation. Only motion: 150 ms fades
(screen change, player controls). Advanced features live in the player's "More" menu.

## Navigation (3 tabs only)
`Home` | `Files` | `Settings`  (Player is full-screen, no tab bar)
Bottom bar: slim (52 dp), blue-tinted surface, hairline on top, 22 dp icon + 11 sp label, accent colour = selected.
Mini player (above the bar, 48 dp): title, play/pause, close. Shown when a video is still loaded but the player screen is closed; tap to reopen.

## Home
```
LazyColumn
├─ "PlayVid" (headline)
├─ TextField  "Paste video URL"  [clear ✕ when text present]  (error text under field)
├─ Button "Play Video"  → "Loading..." while opening
├─ helper: "Supports M3U8, MP4 and direct video links"
├─ "Recent"
└─ rows: title / "M3U8 • 65% watched • 2 hours ago" / 2 dp progress line
   empty: "No videos yet" + "Paste a video URL to start watching."   loading: spinner
```
Messages: empty/invalid -> "Please enter a valid video URL"; wrong type -> "Unsupported video format".

## Files
```
LazyColumn
├─ "Files" (headline)  + search icon (filters the list)
├─ Button "Choose Video"  → system file picker
└─ rows: file name / "24.3 MB • 45:10"      empty: "No videos selected" + "Choose a video from your device."
```

## Player
```
Box (black)
├─ Video surface (Fit by default, never stretched)
├─ Tap layer: tap = show/hide controls, double-tap left/right half = -/+ 10 s (feedback "10s")
├─ Loading: spinner + "Loading video..."      Buffering: spinner only (controls stay usable)
├─ Error: "Can't play this video" / hint / [Retry] / [Change URL]
├─ Controls (fade): 
│   top    : Back | title | More
│   centre : -10 s | Play/Pause (plain white icon, no background) | +10 s
│   bottom : one slim row: Mute | time ─ 2 dp seek bar ─ duration | Fullscreen
├─ More sheet (only available items): Quality, Audio Track, Subtitles, Playback Speed,
│   Picture in Picture, Copy URL (links only), Player Settings (Fit / Fill)
│   (drawn inside the player, not as a dialog, so the status bar never comes back)
└─ Prompt "Continue from mm:ss?"  [Start Over] [Resume]  (also in-window)
```
System bars: status + navigation bars stay hidden for the whole playback (re-hidden after the file picker / focus changes).
Snackbars: "Video added to history", "URL copied", "Playback speed: 1.5x", "Quality: 720p", "Subtitles: Off", "History cleared".

## Settings (plain list)
Playback: Default Playback Speed, Auto Quality, Resume Playback ·
Appearance: Theme (System / Light / Dark) · History: Clear History (confirm) · About: App Version

## Accessibility
48 dp+ touch targets, content descriptions on all icon buttons, seek bar exposes "position of duration",
selected menu option shown with a check mark (not colour only).

## v2.2.0 changes
- No on-screen toast/snackbar messages inside the player (Fit/Fill, quality, audio, speed, etc.).
- Three-dot menu removed. Small icons sit directly above the seek bar: Quality, Audio track (URL/stream videos only), Subtitles (if available), Speed, Fit/Fill, PiP, Copy URL (URL only).
- Brightness (left half) and volume (right half) vertical swipe gestures. Toggle: Settings > Playback > "Brightness & Volume Gestures".

## v2.3.0 - YouTube links (personal use)
- Paste a youtube.com / youtu.be link on Home > Play Video. It is resolved with NewPipeExtractor (JitPack, v0.26.5) to an HLS manifest (adaptive, quality/audio pickers work) or, as fallback, separate video+audio / muxed streams.
- Not an official YouTube API. It can stop working when YouTube changes; then bump the NewPipeExtractor version in app/build.gradle.kts.
- Requires core library desugaring (already enabled) and the JitPack repo (settings.gradle.kts).
- NewPipeExtractor is GPLv3: fine for personal use, matters only if the app is distributed.

## v2.3.6 - YouTube quality fix + status bar fix
- YouTube quality/audio no longer use YouTube's HLS adaptive track overriding (unreliable for 144p/1440p). Each quality/audio track is now its own direct file (via NewPipeExtractor); switching reopens the video+audio pair at the same playback position.
- Live YouTube broadcasts still use YouTube's HLS manifest (no progressive alternative exists for live), with no per-quality picker.
- First time opening the player, the app now retries hiding the system bars a couple of times over ~250ms to avoid an early hide() call being dropped by a not-yet-focused window.

## v2.4.0 - Settings rework
- Removed: Default Playback Speed, the old combined "Auto Quality" toggle, the single combined gesture toggle.
- Added: "Starting Quality" picker (Auto or a fixed 144p-1440p) applied the moment any link is opened, closest-match if the exact height isn't available.
- Added: Data Saver toggle - always starts at the lowest available quality (overrides Starting Quality while on).
- Brightness and volume swipe gestures now have separate on/off switches.
- Gesture swipes starting within ~48dp of the top of the screen are ignored, so pulling down the notification/status bar over the player no longer changes brightness/volume.

## v2.5.0 - Download
- New Download icon in the player toolbar (URL/stream playback only, not local files). Opens a quality picker.
- Direct MP4/video links: downloaded as-is.
- YouTube links: each quality's video-only stream is merged with the best audio into one MP4 using Media3 Transformer (on-device, no re-upload). Live YouTube streams aren't downloadable (no fixed file exists).
- Files save to Movies/PlayVid via MediaStore (Android 10+) or the public Movies folder (Android 9 and below, needs the storage permission already declared for that OS range).
- Runs as a foreground service with a progress notification; tapping the finished notification opens the video. This is unverified end-to-end (couldn't be compiled/tested here) - report the exact notification text if something goes wrong.
