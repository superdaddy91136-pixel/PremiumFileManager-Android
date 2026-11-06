package com.aurora.filemanager.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import androidx.exifinterface.media.ExifInterface
import com.aurora.filemanager.data.model.DetailedFileMetadata
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Lightweight inline metadata summary displayed directly on Recents and Storage cards
 * (e.g., PDF page count, APK app name/package/version, video duration, audio artist/album).
 */
@Immutable
data class QuickCardBadgeInfo(
    val primaryTag: String? = null,
    val secondaryTag: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val thumbnailBitmap: Bitmap? = null
)

/**
 * Memory-aware, bounded-concurrency thumbnail and metadata extraction engine.
 *
 * Key Performance & Stability Features:
 * - Bounded `Semaphore(4)` prevents thread-pool saturation when scrolling fast through large folders.
 * - Memory-sized `LruCache<String, Bitmap>` (capped at 1/8th of max heap) avoids repeated decoding.
 * - Extracts real image thumbnails (respecting EXIF orientation), real video frames, embedded audio
 *   album artwork, first-page PDF renders via `PdfRenderer`, and APK application icons via `PackageManager`.
 * - Gracefully handles corrupt or truncated files without crashing.
 */
object ThumbnailAndMetadataManager {

    private val decodeSemaphore = Semaphore(permits = 4)

    private val maxCacheKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4096)

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val badgeCache = ConcurrentHashMap<String, QuickCardBadgeInfo>()

    fun getCachedThumbnail(cacheKey: String): Bitmap? = bitmapCache.get(cacheKey)

    fun getCachedBadge(path: String): QuickCardBadgeInfo? = badgeCache[path]

    fun clearMemoryCaches() {
        bitmapCache.evictAll()
        badgeCache.clear()
    }

    /**
     * Loads or generates a real thumbnail bitmap for [item] off the main thread.
     */
    suspend fun loadThumbnailAndBadge(
        context: Context,
        item: FileItem,
        targetSizePx: Int = 256
    ): Pair<Bitmap?, QuickCardBadgeInfo?> = withContext(Dispatchers.IO) {
        if (item.isDirectory) return@withContext null to null

        val cacheKey = "${item.path}:${item.timestamps.lastModifiedMillis}:$targetSizePx"
        val cachedBmp = bitmapCache.get(cacheKey)
        val cachedBadge = badgeCache[item.path]
        if (cachedBmp != null && cachedBadge != null) {
            return@withContext cachedBmp to cachedBadge
        }

        decodeSemaphore.withPermit {
            // Double-check after acquiring permit
            val secondBmp = bitmapCache.get(cacheKey)
            val secondBadge = badgeCache[item.path]
            if (secondBmp != null && secondBadge != null) {
                return@withPermit secondBmp to secondBadge
            }

            val file = File(item.path)
            if (!file.exists() || !file.canRead()) {
                return@withPermit null to null
            }

            var bitmap: Bitmap? = secondBmp
            var badge: QuickCardBadgeInfo? = secondBadge

            try {
                when (item.category) {
                    FileCategory.IMAGE -> {
                        if (bitmap == null) {
                            bitmap = decodeSampledBitmap(file, targetSizePx, targetSizePx)
                        }
                        if (badge == null) {
                            val dims = if (item.width != null && item.height != null) {
                                "${item.width}×${item.height}"
                            } else {
                                readImageDimensionsOnly(file)
                            }
                            badge = QuickCardBadgeInfo(primaryTag = dims)
                        }
                    }

                    FileCategory.VIDEO -> {
                        if (bitmap == null) {
                            bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                runCatching {
                                    ThumbnailUtils.createVideoThumbnail(
                                        file,
                                        Size(targetSizePx, targetSizePx),
                                        null
                                    )
                                }.getOrNull()
                            } else {
                                null
                            } ?: extractVideoFrameFallback(file)
                        }
                        if (badge == null) {
                            val durMs = item.durationMillis ?: extractMediaDurationMs(file)
                            val durLabel = durMs?.takeIf { it > 0 }?.let { FileItem.formatDuration(it) }
                            badge = QuickCardBadgeInfo(primaryTag = durLabel)
                        }
                    }

                    FileCategory.AUDIO -> {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            if (bitmap == null) {
                                val rawArt = retriever.embeddedPicture
                                if (rawArt != null && rawArt.isNotEmpty()) {
                                    bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size)
                                }
                            }
                            if (badge == null) {
                                val artist = item.artist
                                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                val album = item.album
                                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                val durMs = item.durationMillis
                                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                val durFormatted = durMs?.takeIf { it > 0 }?.let { FileItem.formatDuration(it) }
                                val artistAlbum = listOfNotNull(
                                    artist?.takeIf { it.isNotBlank() },
                                    album?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifEmpty { null }
                                badge = QuickCardBadgeInfo(
                                    primaryTag = durFormatted,
                                    secondaryTag = artistAlbum
                                )
                            }
                        } catch (_: Throwable) {
                        } finally {
                            runCatching { retriever.release() }
                        }
                    }

                    FileCategory.PDF -> {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                val pageCount = renderer.pageCount
                                badge = QuickCardBadgeInfo(
                                    primaryTag = "$pageCount page${if (pageCount == 1) "" else "s"}"
                                )
                                if (bitmap == null && pageCount > 0) {
                                    renderer.openPage(0).use { page ->
                                        val aspect = page.width.toFloat().coerceAtLeast(1f) /
                                            page.height.toFloat().coerceAtLeast(1f)
                                        val bmpW = targetSizePx.coerceAtLeast(120)
                                        val bmpH = (bmpW / aspect).toInt().coerceAtLeast(120)
                                        val rendered = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                        rendered.eraseColor(android.graphics.Color.WHITE)
                                        page.render(
                                            rendered,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )
                                        bitmap = rendered
                                    }
                                }
                            }
                        }
                    }

                    FileCategory.APK -> {
                        val pm = context.packageManager
                        val pkgInfo = getPackageArchiveInfoCompat(pm, file.absolutePath)
                        val appInfo = pkgInfo?.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = file.absolutePath
                            appInfo.publicSourceDir = file.absolutePath
                            if (bitmap == null) {
                                val drawable = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                                if (drawable != null) {
                                    bitmap = drawableToBitmap(drawable, targetSizePx)
                                }
                            }
                            if (badge == null) {
                                val appLabel = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
                                val verName = pkgInfo.versionName ?: "1.0"
                                val pkgName = pkgInfo.packageName
                                badge = QuickCardBadgeInfo(
                                    primaryTag = "v$verName",
                                    secondaryTag = listOfNotNull(appLabel, pkgName).joinToString(" • ")
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            } catch (_: Throwable) {
                // Gracefully ignore corrupt file errors
            }

            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap)
            }
            badge?.let {
                badgeCache[item.path] = it
            }
            bitmap to badge
        }
    }

    suspend fun extractAudioMetadata(context: Context, item: FileItem): QuickCardBadgeInfo = withContext(Dispatchers.IO) {
        val file = File(item.path)
        var title: String? = null
        var artist: String? = item.artist
        var artworkBitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = artist ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                artworkBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        QuickCardBadgeInfo(
            title = title,
            artist = artist,
            thumbnailBitmap = artworkBitmap
        )
    }

    /**
     * Extracts comprehensive metadata for the File Details Bottom Sheet off the main thread.
     * When [computeHashes] is true, calculates SHA-256 and MD5 checksums.
     */
    suspend fun extractDetailedMetadata(
        context: Context,
        item: FileItem,
        computeHashes: Boolean = false
    ): DetailedFileMetadata = withContext(Dispatchers.IO) {
        val file = File(item.path)
        val readable = file.canRead()
        val writable = file.canWrite()

        var imageDimensions: String? = null
        var cameraMakeModel: String? = null
        var videoResolution: String? = null
        var videoDuration: String? = null
        var audioArtist: String? = item.artist
        var audioAlbum: String? = item.album
        var audioTitle: String? = null
        var audioBitrateKbps: Int? = null
        var audioDuration: String? = null
        var pdfPageCount: Int? = null
        var apkAppName: String? = null
        var apkPackageName: String? = null
        var apkVersionName: String? = null
        var apkVersionCode: Long? = null
        var apkMinSdk: Int? = null
        var apkTargetSdk: Int? = null
        var zipEntryCount: Int? = null
        var zipUncompressedBytes: Long? = null

        if (file.exists() && readable && !item.isDirectory) {
            when (item.category) {
                FileCategory.IMAGE -> {
                    imageDimensions = readImageDimensionsOnly(file)
                    runCatching {
                        val exif = ExifInterface(file)
                        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                        val combined = listOfNotNull(make, model).joinToString(" ").trim()
                        if (combined.isNotEmpty()) cameraMakeModel = combined
                    }
                }

                FileCategory.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                        if (w != null && h != null && w > 0 && h > 0) {
                            videoResolution = "${w}×${h} px"
                        }
                        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        if (dur != null && dur > 0) {
                            videoDuration = FileItem.formatDuration(dur)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        runCatching { retriever.release() }
                    }
                }

                FileCategory.AUDIO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        audioArtist = audioArtist ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        audioAlbum = audioAlbum ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        audioTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                        if (bitrate != null && bitrate > 0) {
                            audioBitrateKbps = bitrate / 1000
                        }
                        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        if (dur != null && dur > 0) {
                            audioDuration = FileItem.formatDuration(dur)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        runCatching { retriever.release() }
                    }
                }

                FileCategory.PDF -> {
                    runCatching {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                pdfPageCount = renderer.pageCount
                            }
                        }
                    }
                }

                FileCategory.APK -> {
                    runCatching {
                        val pm = context.packageManager
                        val pkgInfo = getPackageArchiveInfoCompat(pm, file.absolutePath)
                        val appInfo = pkgInfo?.applicationInfo
                        if (pkgInfo != null && appInfo != null) {
                            appInfo.sourceDir = file.absolutePath
                            appInfo.publicSourceDir = file.absolutePath
                            apkAppName = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
                            apkPackageName = pkgInfo.packageName
                            apkVersionName = pkgInfo.versionName
                            apkVersionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
                            apkMinSdk = appInfo.minSdkVersion
                            apkTargetSdk = appInfo.targetSdkVersion
                        }
                    }
                }

                FileCategory.ARCHIVE -> {
                    if (item.extension.equals("zip", ignoreCase = true) || item.extension.equals("jar", ignoreCase = true)) {
                        runCatching {
                            ZipFile(file).use { zf ->
                                var count = 0
                                var totalUncompressed = 0L
                                val entries = zf.entries()
                                while (entries.hasMoreElements()) {
                                    val e = entries.nextElement()
                                    count++
                                    if (e.size > 0) totalUncompressed += e.size
                                }
                                zipEntryCount = count
                                zipUncompressedBytes = totalUncompressed
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        val sha256 = if (computeHashes && !item.isDirectory) {
            StorageAnalyzer.computeFileHash(file, "SHA-256")
        } else null
        val md5 = if (computeHashes && !item.isDirectory) {
            StorageAnalyzer.computeFileHash(file, "MD5")
        } else null

        DetailedFileMetadata(
            fileItem = item,
            readable = readable,
            writable = writable,
            imageDimensions = imageDimensions,
            cameraMakeModel = cameraMakeModel,
            videoResolution = videoResolution,
            videoDurationFormatted = videoDuration,
            audioArtist = audioArtist,
            audioAlbum = audioAlbum,
            audioTitle = audioTitle,
            audioBitrateKbps = audioBitrateKbps,
            audioDurationFormatted = audioDuration,
            pdfPageCount = pdfPageCount,
            apkAppName = apkAppName,
            apkPackageName = apkPackageName,
            apkVersionName = apkVersionName,
            apkVersionCode = apkVersionCode,
            apkMinSdk = apkMinSdk,
            apkTargetSdk = apkTargetSdk,
            zipEntryCount = zipEntryCount,
            zipUncompressedBytes = zipUncompressedBytes,
            sha256Hash = sha256,
            md5Hash = md5
        )
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    private fun readImageDimensionsOnly(file: File): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            "${options.outWidth}×${options.outHeight} px"
        } else null
    }

    private fun extractVideoFrameFallback(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractMediaDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun drawableToBitmap(drawable: Drawable, maxSizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceIn(64, maxSizePx)
        val h = drawable.intrinsicHeight.coerceIn(64, maxSizePx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfoCompat(pm: PackageManager, archivePath: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(archivePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageArchiveInfo(archivePath, 0)
        }
}
