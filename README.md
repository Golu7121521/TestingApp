# Game Booster (Android)

## Features
- Installed games add karo aur list me rakho
- Game select karke **BOOST & PLAY** dabao:
  - Game aur app ko chhod kar baaki background apps kill hote hain (RAM clear)
  - Game automatically launch hota hai
- Top par live RAM (free / total) dikhta hai

## GitHub par build karne ke steps
1. GitHub par ek naya repository banao (public/private).
2. Is folder ki saari files usme upload karo (`.github` folder bhi zaroor).
3. Upload ke baad **Actions** tab khol kar "Build Release APK" workflow chalega.
4. Build complete hone par:
   - **Artifacts** section me `GameBooster-release` se APK download karo, ya
   - Release banane ke liye tag push karo: `v1.0` (Releases section me APK milega).

## Important limitations
- Android normal apps ko force-stop nahi kar sakta. Ye app `killBackgroundProcesses` use karta hai, jo sirf background/cached processes ko hatata hai. Kuch phones ise block kar sakte hain.
- Bina root ke FPS ya lag ko guaranteed "zero lag" nahi kiya ja sakta. Boost ka asar phone ke hisaab se alag hoga.
- Release APK debug key se sign hoti hai, isliye install ho jaati hai. Play Store ke liye alag keystore chahiye.
- Install se pehle phone me "Unknown sources" se install allow karna hoga.
