# PDF Toolkit (Android)

A lightweight, offline PDF utility. Kotlin + XML Views, minSdk 21 (Android 5.0), no account, no analytics, no internet use.

## What works in this version (v1 "core")

- **Home** with Open PDF, Create PDF (images to PDF), quick tools, Recent Files and Starred.
- **Files**: Recent / All PDFs / Starred / Created by me, search by name, sort, favorites, long-press menu (open, share, favorite, rename/delete for files you created). Files are opened with Android's file picker (no broad storage permission). "Scan device" lists PDFs visible to the app through MediaStore.
- **Reader**: continuous vertical scrolling with page-by-page rendering and a small bitmap cache (safe on low-RAM phones), zoom 100–200%, go to page, page indicator, dark reader mode, reading-position restore, share, document info, errors for damaged/unreadable files. Opens PDFs from other apps ("Open with").
- **Tools**: Merge, Split / Extract pages, Compress, Images to PDF (A4 / Letter / fit), PDF to Images (PNG / JPEG). Each shows progress, validates its output, and offers Open / Share / Save a copy.
- **Settings**: theme (system/light/dark), default sort, default compression, clear history / temp files / starred, privacy, about, reset.

## Known limits (shown honestly in the app)

- **Compress** re-renders pages as JPEG images, so text is no longer selectable, and it saves nothing if the result is not smaller.
- **Password-protected PDFs** cannot be opened or processed in this version.
- **Not included yet**: text search, bookmarks / table of contents, annotation and editing, scanning, OCR, forms, password protection, watermark, page rotate/reorder, Office conversions. They are not shown as buttons that do nothing; the Tools screen says they are planned.
- Zoom uses menu buttons, not pinch.
- On Android 10+ output files live in the app's own folder (`Android/data/com.advancedpdf.toolkit/files/PDFs`). Use **Save a copy** on a result to put a file anywhere else.

## Build the APK with GitHub Actions (no PC needed)

1. Push this project to your GitHub repo (the `.github/workflows/release.yml` file must be included).
2. In the repo go to **Actions → Build and Release APK → Run workflow**, enter a version such as `1.0.0`, and run it.
   (Or push a tag named `v1.0.0`.)
3. When the run is green, open the repo's **Releases** page. `PDFToolkit-v1.0.0.apk` is attached. Download it on your phone and install it.

Each version creates its own release, so older downloads stay available.

### Which APK?

One universal APK is produced (the app has no native code), and it is the right one for the Redmi 9 Prime (arm64) and for older 32-bit phones.

### Signing (optional but recommended)

Without secrets, each build is signed with a freshly generated key, so installing a newer build over an older one requires uninstalling first. To keep updates working, add these repository secrets (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. No keys or passwords are stored in the repository.

## If the build fails

Open the failed run, expand the **Build release APK** step and send the last ~40 lines of the log. A `build-reports` artifact is also attached to failed runs.
