# YouTube NewPipe Real Android App (v2 - Fixed Gradle 8.7 for GitHub Actions)

## What was fixed in v2:
GitHub Actions `ubuntu-latest` (Ubuntu 26.04) runner par default **Gradle 9.8.2** chal raha tha, jo Android Gradle Plugin (AGP 8.x) ke sath `Cannot mutate the dependencies of configuration ':app:releaseCompileClasspath'` error deta hai.
- `.github/workflows/android-release.yml` mein `gradle-version: '8.7'` lock kar diya gaya hai.
- `cache-disabled: true` set kar diya gaya hai taki GitHub cache 400 warnings na aayein.
