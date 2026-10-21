# PDF Pro — All-in-One PDF Studio

React + TypeScript + Vite + Tailwind app, wrapped as an Android APK with Capacitor.
All PDF processing runs **on the device** (pdf-lib + PDF.js). Nothing is uploaded.

## Build the APK with GitHub Actions (no PC needed)
1. Push this project to a GitHub repository (the `.github/workflows/build-apk.yml` file must be included).
2. Open **Actions → "Build APK and publish to Releases" → Run workflow**, enter a version such as `v1.0.0`.
   (Or push a tag starting with `v`.)
3. When it finishes, the APK is attached to **Releases** automatically. No Actions artifacts are used, so the artifact storage limit does not matter.
4. Download `PDF-Pro-<version>.apk` from Releases and install it (allow "Install unknown apps").

The APK is a **debug-signed** build, which installs fine for personal use. It is not suitable for Play Store.

## Run locally
```bash
npm install
npm run dev        # http://localhost:5173
npm test           # unit tests for PDF operations and validation
npm run typecheck
npm run build
```

## Implemented
View PDF (zoom, fit, rotate view, thumbnails, keyboard nav) · Merge · Split (ranges / every N) · Extract pages · Delete pages ·
Rotate / reorder / duplicate / delete pages with undo · Compress (lossless structure optimize, or re-render as JPEG) ·
PDF → PNG/JPG (+ZIP) · Images → PDF · Watermark · Page numbers · Visual signature (draw/upload, drag to place) ·
Dark mode · Settings · Recent exports list (names and sizes only).

## Not available (clearly disabled in the UI)
Annotate, Protect (encrypt), Unlock, OCR, PDF → Word/Excel/PowerPoint, text search/selection in viewer,
Starred/Trash/Shared (files are not stored between sessions).

## Notes
- Signatures are visual images, **not** cryptographic digital signatures.
- Compression presets that re-render pages remove selectable text; savings are not guaranteed.
- Encrypted PDFs are rejected with a clear message.
- On Android, "Save / Share" opens the share sheet (Save to Files, Drive, WhatsApp…).
- Local storage only keeps settings and export names/sizes; clearing app data removes them.
