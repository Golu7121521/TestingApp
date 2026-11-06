# Device Health (Redmi 9 Prime / Android 10)

Phone ki health details dikhane wala app: battery (level, temp, voltage, health, charging, cycles),
RAM, storage, CPU, display, device info, uptime. Premium dark UI + Lottie animated buttons.

## GitHub Actions se APK banana
1. GitHub par ek naya repository banaiye (Private ya Public).
2. Is folder ki saari files (`.github` folder sahit) repository mein upload kariye.
   - Mobile par Desktop site mode on karke upload karein, ya Termux mein `git push` karein.
   - `.github/workflows/build.yml` ka path bilkul aisa hona chahiye.
3. Code push hote hi "Actions" tab mein "Build Android APK" chalega.
4. Workflow green hone ke baad uske page ke neeche "DeviceHealth-debug-apk" artifact download kariye.
5. ZIP extract karke `app-debug.apk` ko phone mein install karein
   (Settings > Install unknown apps / Security mein permission dein).

## Notes
- Battery "Health estimate", "Cycle count" aur CPU thermal kuch MIUI builds par system restrict karta hai,
  tab woh "N/A" dikhayega. Yeh app ki galti nahi hai.
- Lottie animation `app/src/main/res/raw/pulse.json` mein hai. LottieFiles.com se koi bhi
  JSON download karke isi naam se replace kar sakte hain.
