# CLAUDE.md — Rs Enhancify

Offline Android app: AI image upscaler (Real-ESRGAN), JPG→PDF, PDF→JPG, PDF→Word. Native Kotlin + Jetpack Compose, glassmorphism UI. All processing is on-device. Network is used ONLY by the in-app updater (`update/`) — never send user files or analytics anywhere.

## Working with the owner
- Explain in **Bengali (বাংলা)**; code, commands, paths and technical terms in English. Code comments in English.
- Fix first, then a short "why". Non-trivial changes: outline + list files, wait for go-ahead.
- Check for bugs/regressions before delivering. Warn before risky changes (package rename, signing change).

## Stack
- Kotlin 2.0.21, AGP 8.7.3, Gradle 9.3.0 wrapper, JDK 17; compileSdk/targetSdk 35, minSdk 29
- Compose BOM 2024.12.01 / Material3
- TFLite 2.16.1 (+GPU delegate, CPU/XNNPACK fallback) — model `app/src/main/assets/realesr_x4_fp16.tflite`
- pdfbox-android 2.0.27.0 (PDF text), ML Kit text-recognition 16.0.1 (bundled, offline OCR)
- Android `PdfRenderer` (PDF→image) and `PdfDocument` (image→PDF); own `.docx` writer (no POI)

## Layout
```
app/src/main/java/com/aribs/rsenhancify/
  MainActivity.kt            # theme + simple screen navigation
  engine/Upscaler.kt         # tiled Real-ESRGAN inference (256px tiles, 16px overlap), GPU crash-guard
  engine/PdfTools.kt         # ImageToPdf, PdfPages (PdfRenderer wrapper), PageRange parser
  engine/PdfToWord.kt        # Editable (PDFBox + OCR fallback) / Exact (page images) modes
  engine/DocxWriter.kt       # minimal OOXML writer (paragraphs, page breaks, full-page images)
  engine/TextReflow.kt       # joins hard-wrapped PDF lines into paragraphs (pure Kotlin)
  ui/components/Glass.kt     # glass design system (background, cards, buttons, segmented, progress)
  ui/screens/*.kt            # Home, Enhance, ImageToPdf, PdfToImage, PdfToWord
  util/Files.kt              # decode (EXIF-aware), MediaStore save, open/share
  update/AppUpdater.kt       # latest.json -> in-app download -> sha256 -> system installer (GitHub hosts only, redirects checked)
  update/UpdateDialog.kt     # glass update prompt; checks every launch, "Later" = ask again next launch
scripts/convert_model.py     # .pth -> .tflite (no torch needed): python3 convert_model.py realesr-general-x4v3.pth out.tflite 256
```
Outputs: images → `Pictures/Rs Enhancify`, PDF/DOCX → `Download/Rs Enhancify`.

## Rules
- Model input is fixed at 1x256x256x3 float32 [0,1] RGB, output 1x1024x1024x3. If you re-export with another tile size, update `Upscaler.TILE`.
- GPU delegate must be created/used/closed on one thread (all inside `Upscaler.upscale`). Keep the `gpu_init_pending` crash guard.
- Prefs file: `rs_enhancify_prefs` (`gpu_disabled`, `gpu_init_pending`).

## Build & release
- Every push to `main` builds an APK (`.github/workflows/release.yml`); versionCode = 100 + run number.
- Signed (secrets present) → public `aribspeat-Rawahi/rs-enhancify-releases` + `latest.json` (read by the in-app updater via `BuildConfig.UPDATE_MANIFEST_URL`). Commit subject = "What's new" text users see. No secrets → debug-signed test pre-release in this repo.
- Build log + status are force-pushed to branch `ci-logs` after every run.
- Never commit keystores, local.properties, APKs. Never change the signing key once users installed signed builds.

## Logo
Adaptive icon = `drawable/ic_launcher_background.xml` (gradient) + `ic_launcher_foreground.xml` (glass photo card + AI sparkle). `AppLogo()` in HomeScreen draws the same in-app.

## Licenses
Real-ESRGAN weights: BSD-3-Clause (xinntao/Real-ESRGAN). PDFBox: Apache-2.0. ML Kit: Google APIs ToS.
