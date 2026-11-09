# File Recovery (Android, Jetpack Compose + Material 3)

Deleted photos, videos, audio, PDFs aur doosri files ko Android ke Trash/Recently-deleted
folders se dhund kar restore karta hai.

## Kaise kaam karta hai
Android 11+ par jab file trash mein jaati hai, to uska naam `.trashed-<expiry>-<naam>`
ho jaata hai aur file wahi rehti hai. All-files access ke saath app ye files dhundh kar
original naam par wapas rename kar deti hai.

## Limitations (zaroori)
- Jo file permanently delete ho chuki hai (trash expire ho gaya ya `rm` jaisa delete),
  uske liye root ya forensic tools chahiye. Ye app woh nahi kar sakti.
- Google Photos ka apna trash alag hota hai, uske liye Google Photos app mein restore karo.
- Sirf apne device ki files ke liye kaam karta hai.

## Build (GitHub Actions se, bina PC ke)
1. Is folder ko ek naye GitHub repo mein upload karo (web se "Add file > Upload files" ya zip extract karke).
2. Repo mein **Actions** tab kholo, "Build APK" workflow apne aap chalega (ya "Run workflow" dabao).
3. Run complete hone par neeche **Artifacts** se `FileRecovery-debug-apk` download karo.
4. APK ko phone mein install karo (Unknown apps install ki permission dene ko kaha jaayega).
5. App mein "All files access kholo" dabao aur permission do.

## Structure
- `MainActivity.kt` - entry point, permission gate
- `data/TrashScanner.kt` - scan + restore logic
- `ui/RecoveryViewModel.kt` - state management
- `ui/Screens.kt` - Compose screens
- `ui/components/AnimatedButtons.kt` - fancy animated buttons (gradient, shine, press spring, pulse)
- `ui/theme/Theme.kt` - Material 3 colors
