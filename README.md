# CrashLens

Ye project do kaam karta hai:

1. **Crash Reporter (`CrashReporter.kt`)**: Ek chhota sa module hai jo aapke app mein crash hone par report file save karta hai. Report mein ye hota hai:
   - Exception ka naam aur message
   - Hindi/English mein simple reason (jaise "null value use hui")
   - "Aapke code mein" wali line, yaani aapke apne code ki woh exact line jahan crash hua
   - Device, Android version, thread aur poora stack trace

2. **Demo App**: Ye app khud crash karke dikhata hai ki report kaise aati hai. Aap ise test karke samajh sakte hain.

> Note: Doosre apps (jaise Play Store wale apps) ka crash ye app nahi padh sakta. Apne hi app mein ye module lagana padta hai.

---

## Project structure

```
CrashLens/
├── .github/workflows/build-release.yml   <- GitHub Actions (arm64 APK -> Release)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/crashlens/
        │   ├── CrashApp.kt             <- Application class (yahan handler lagta hai)
        │   ├── CrashReporter.kt        <- MAIN MODULE: apne app mein copy karein
        │   ├── MainActivity.kt
        │   └── CrashReportActivity.kt  <- Report dikhane wali screen
        └── res/
            ├── layout/activity_main.xml
            ├── layout/activity_crash_report.xml
            └── values/strings.xml
```

---

## Step 1: GitHub par upload karna

1. GitHub par ek naya repository banayein (jaise `CrashLens`).
2. Zip ko phone par unzip karke saari files repo mein upload karein.
   - Mobile browser se `.github` folder upload karna mushkil ho sakta hai. Isliye GitHub ka web editor use karein: **Add file -> Create new file**, aur file name ki jagah `.github/workflows/build-release.yml` likhein. Slash (`/`) se folders apne aap ban jaate hain. Phir `build-release.yml` ka content paste karein.
3. Repo mein **Settings -> Actions -> General -> Workflow permissions** mein **Read and write permissions** select karein aur save karein.
4. Code `main` branch par ho. Workflow ko `Actions` tab se **Run workflow** karke bhi chala sakte hain.

## Step 2: APK kaise milega

- Build complete hone ke baad **Releases** section mein `CrashLens-arm64-v8a.apk` milega.
- Ye sirf **arm64-v8a** ke liye bana hai, jo aaj ke lagbhag sabhi Android phones mein hota hai.
- Artifacts ka use nahi hota, isliye aapka artifacts limit wala issue nahi aayega.
- Phone par APK install karne ke liye browser se download karke install karein. "Unknown apps" ki permission dena padega.

## Step 3: Apne app mein Crash Reporter kaise lagayein

1. `CrashReporter.kt` ko apne project mein copy karein. Pehli line ka `package` apne package name se badal dein.
2. Apne Application class mein ye line add karein (agar Application class nahi hai to ek banayein):

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)   // <-- bas ye ek line
    }
}
```

3. `AndroidManifest.xml` mein `<application android:name=".MyApp" ...>` set karein.
4. Crash report dekhne ke liye `CrashReportActivity.kt` aur uska layout bhi copy karein, ya bas `CrashReporter.listReports(this).firstOrNull()?.readText()` se report text lekar kahin bhi dikha dein.

Ab jab bhi app crash hoga, report `filesDir/crash_reports/` mein save ho jayegi. Agli baar app kholne par report dekh sakte hain.

## Step 4: Report kaise padhein

Report ke sabse upar **SUMMARY** hota hai:

- **Reason**: crash ka simple matlab
- **Aapke code mein**: `MainActivity.onCreate(MainActivity.kt:42)` jaisi line. Yahi aapka bug hai. Us file ki us line ko dekhiye.

Phir **STACK TRACE** aata hai, jo detail mein batata hai ki kaunsa function kisne call kiya.

Report ko **Copy** ya **Share** karke AI ko bhi bhej sakte hain, jisse fix jaldi mil jaata hai.

---

## Common crash ke reasons

| Exception | Matlab |
|---|---|
| NullPointerException | Null value par kaam kiya gaya |
| ClassCastException | Galat type mein cast kiya |
| IndexOutOfBoundsException | List/array ki size se bahar index |
| IllegalStateException | Object galat state mein tha (jaise destroy hone ke baad use) |
| NetworkOnMainThreadException | UI thread par network call |
| ActivityNotFoundException | Intent handle karne wali app nahi mili |
| SecurityException | Permission nahi mili |
| OutOfMemoryError | Memory khatam (bade bitmap, leaks) |

## Limitations

- Ye sirf **Java/Kotlin exceptions** pakdta hai. Native (C/C++) crash aur ANR (app freeze) nahi pakdta.
- Crash ke baad process band hota hai, isliye report save karne ka kaam ek hi baar hota hai.
- Release build mein `isMinifyEnabled = false` rakha gaya hai, taaki stack trace saaf rahe.

## Release build ke baare mein

Is project mein release build **debug key se sign** hoti hai taaki APK bina keystore ke install ho jaaye. Play Store par upload ke liye apna keystore banana padega.
