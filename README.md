# Aurora Files (`PremiumFileManager-Android`)

A production-quality, privacy-first Android File Manager featuring a custom **Glassmorphism Design System**, unified chronological **Recents**, full shared **Storage** hierarchy navigation, built-in media & document viewers, streaming **ZIP** archive management, and content-hash **Storage Analysis & Duplicate Detection**.

---

## 1. Privacy & Security Architecture

- **Zero Advertisements & Zero Telemetry**: No ad SDKs, analytics libraries, crash reporters, or tracking hooks are included in the dependencies or source tree.
- **Zero Network Permissions**: `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are intentionally omitted from `AndroidManifest.xml`. The application is 100% offline by design.
- **Scoped Storage & Permission Honesty**:
  - Supports Android 11+ (`API 30+`) `MANAGE_EXTERNAL_STORAGE` via an explicit user-initiated permission flow (`Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) as well as granular Android 13/14+ media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`).
  - Accurately detects Android 11+ Scoped Storage restrictions on `/Android/data` and `/Android/obb` and explains system security restrictions clearly rather than faking folder contents.
- **Zip-Slip Protection**: `ZipManager.validateZipEntryTarget` verifies canonical destination paths before extracting any archive entry, blocking path traversal payloads (`../../`).
- **Safe APK Installation**: Inspects `.apk` package icons, labels, version names, and SDK targets via `PackageManager` and delegates installation strictly to Android's system package installer (`Intent.ACTION_VIEW` with `FileProvider`).

---

## 2. Feature Highlights

### Design System (`ui/theme/` & `ui/components/`)
- **Dark Glass Primary Theme** (`MatteObsidian #080A0F` and `DeepCharcoal #0E121B` backgrounds with `DeepIndigo #4F46E5` and `OceanBlue #0EA5E9` accents) plus a **Light Glass** mode (`AlabasterBg #F3F6FA`).
- **Layered Translucent Surfaces**: `GlassSurface`, `GlassSegmentedControl`, `GlassFilterChip`, and `GlassModalBottomSheet` use subtle specular gradient highlights and hairline `1dp` borders on navigation controls, toolbars, and sheets without blurring individual file rows—preserving 60/120fps scrolling performance.
- **Accessibility & Motion**: Respects system `ANIMATOR_DURATION_SCALE`, provides semantic `contentDescription` labels, and triggers lightweight clock-tick haptic feedback on meaningful interactions.

### Home Screen: `Recents | Storage` (`ui/screens/home/HomeScreen.kt`)
- **Recents**: Unified chronological feed across Images, Videos, Audio, PDFs, Documents, APKs, Archives, and Code/Text files.
  - Clearly distinguishes **Date Added (MediaStore)**, **Created (Filesystem)**, **First Discovered by App** (persisted in Room `discovered_files`), **Last Modified**, and **Last Opened**.
- **Storage**: Real shared-storage folder browser displaying actual directories (`Android`, `DCIM`, `Download`, `Documents`, `Movies`, `Music`, `Pictures`, etc.) with interactive breadcrumbs, folder item counts, storage usage bar, and full scroll/navigation stack preservation.

### Built-In Preview Viewers (`ui/screens/viewers/`)
1. **Image Viewer**: Full-screen `HorizontalPager` swipe between sibling images, pinch-to-zoom, double-tap zoom (`1x` ↔ `2.5x`), EXIF resolution overlay, Share, Favorite, and Delete.
2. **Video Player**: Powered by **AndroidX Media3 (ExoPlayer)** with Play/Pause, seek bar, playback speed selector (`0.5x` – `2.0x`), aspect ratio toggle, subtitle toggle (with sibling `.srt` discovery), saved playback position resume, and Picture-in-Picture (PiP).
3. **Audio Player**: Embedded album artwork extraction, artist/album/duration metadata, interactive track queue, repeat toggle, audio-focus management, and headphone disconnect pause (`setHandleAudioBecomingNoisy(true)`).
4. **PDF Viewer**: Native `android.graphics.pdf.PdfRenderer` multi-page viewer with mutex-synchronized high-DPI rendering, pinch-to-zoom, fit-to-width reset, and interactive bottom page thumbnail strip.
5. **Text & Code Viewer**: Safe read-only preview capped at 256 KB per chunk with UTF-8 / UTF-16 BOM detection and ISO-8859-1 fallback, line numbers, and line-wrap toggle.

### Archives, Storage Analysis & Duplicates
- **ZIP Browser & Extractor** (`ZipBrowserScreen.kt` & `ZipManager.kt`): Browse entries inside `.zip` archives, view compressed vs. uncompressed sizes, extract selected or all entries, and compress any multi-selection of files/folders into a new `.zip` archive with progress and cancellation.
- **Storage Analysis & Duplicate Detector** (`StorageAnalysisScreen.kt` & `StorageAnalyzer.kt`): Category breakdown bar, storage-heavy directories, largest files, large videos, large downloads, and a 3-stage exact duplicate detector (file size pre-filter → 16 KB head/tail sample hash → full streaming SHA-256 content hash).

---

## 3. Opening & Building in Android Studio

1. Extract `PremiumFileManager-Android.zip`.
2. Open **Android Studio Ladybug (2024.2.1) or newer**.
3. Select **Open** and choose the extracted `PremiumFileManager-Android` directory.
4. Allow **Gradle Sync** to complete (configured with AGP `8.7.2`, Kotlin `2.0.21`, KSP `2.0.21-1.0.27`, and Gradle `8.9`).
5. Run on an Android device or emulator (`minSdk 26`, `targetSdk 35`), or build an APK from the terminal:
   ```bash
   ./gradlew assembleDebug
   ./gradlew testDebugUnitTest
   ```
