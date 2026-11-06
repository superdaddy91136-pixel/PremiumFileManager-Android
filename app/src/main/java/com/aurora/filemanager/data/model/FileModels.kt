package com.aurora.filemanager.data.model

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import com.aurora.filemanager.AuroraApplication
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * High-level semantic category for files.
 */
enum class FileCategory(val displayName: String) {
    FOLDER("Folder"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    PDF("PDFs"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    APK("Packages (APK)"),
    CODE("Code & Text"),
    OTHER("Other");

    companion object {
        private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "avif")
        private val videoExts = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "flv")
        private val audioExts = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")
        private val docExts = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "csv", "epub")
        private val codeAndTextExts = setOf("txt", "md", "json", "xml", "yaml", "yml", "html", "css", "js", "ts", "kt", "kts", "java", "py", "c", "cpp", "h", "sh", "ini", "conf", "log", "properties", "sql")
        private val archiveExts = setOf("zip", "jar", "tar", "gz", "7z", "rar", "bz2")

        fun fromFile(isDirectory: Boolean, extension: String, mimeType: String?): FileCategory {
            if (isDirectory) return FOLDER
            val ext = extension.lowercase(Locale.ROOT)
            return when {
                ext == "pdf" || mimeType == "application/pdf" -> PDF
                ext == "apk" || ext == "apks" || ext == "xapk" || mimeType == "application/vnd.android.package-archive" -> APK
                ext in archiveExts || mimeType == "application/zip" -> ARCHIVE
                ext in imageExts || mimeType?.startsWith("image/") == true -> IMAGE
                ext in videoExts || mimeType?.startsWith("video/") == true -> VIDEO
                ext in audioExts || mimeType?.startsWith("audio/") == true -> AUDIO
                ext in codeAndTextExts || mimeType == "text/plain" -> CODE
                ext in docExts || mimeType?.startsWith("text/") == true -> DOCUMENT
                else -> OTHER
            }
        }
    }
}

/**
 * Explicitly distinguishes the provenance of a file's primary chronological timestamp.
 * Never claims an estimated or fallback date is an exact creation date.
 */
enum class TimestampKind(val label: String) {
    MEDIASTORE_DATE_ADDED("Date Added (MediaStore)"),
    FILESYSTEM_CREATION("Created (Filesystem)"),
    FIRST_DISCOVERED_LOCAL("First Discovered by App"),
    LAST_MODIFIED_FALLBACK("Modified (Added Fallback)"),
    LAST_MODIFIED("Last Modified"),
    LAST_OPENED("Last Opened")
}

/**
 * Rich chronological record for a file item, keeping distinct concepts separate:
 * - Date added or first discovered
 * - Filesystem creation time (when supported by the OS/filesystem)
 * - Last modified time
 * - Last opened time (tracked locally when opened in Files)
 */
@Immutable
data class FileTimestamps(
    val primaryAddedOrDiscoveredMillis: Long,
    val primaryAddedSource: TimestampKind,
    val creationTimeMillis: Long?,       // Null when filesystem does not provide a genuine birth time
    val lastModifiedMillis: Long,
    val lastOpenedMillis: Long? = null
)

/**
 * Unified, immutable representation of an actual accessible file or directory.
 */
@Immutable
data class FileItem(
    val id: String,                      // Canonical path or content URI string
    val name: String,
    val path: String,                    // Actual accessible path or human-readable URI location
    val contentUri: Uri?,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val extension: String,
    val mimeType: String,
    val category: FileCategory,
    val timestamps: FileTimestamps,
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val childCount: Int? = null,         // For directories when enumerated
    // Optional pre-populated media metadata from MediaStore or cache
    val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val artist: String? = null,
    val album: String? = null,
    val mediaStoreAlbumId: Long? = null,
    val isRestrictedSystemFolder: Boolean = false
) {
    val displayNameWithoutExtension: String
        get() = if (isDirectory || extension.isEmpty() || !name.contains('.')) {
            name
        } else {
            name.substringBeforeLast('.')
        }

    fun displayName(showExtension: Boolean): String {
        return if (showExtension || isDirectory) name else displayNameWithoutExtension
    }

    val formattedSize: String
        get() = if (isDirectory) {
            childCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Folder"
        } else {
            formatFileSize(sizeBytes)
        }

    val formattedModifiedDate: String
        get() = formatTimestamp(timestamps.lastModifiedMillis)

    val formattedAddedDateWithLabel: String
        get() = "${timestamps.primaryAddedSource.label}: ${formatTimestamp(timestamps.primaryAddedOrDiscoveredMillis)}"

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
            val value = bytes / 1024.0.pow(digitGroups.toDouble())
            val pattern = if (digitGroups == 0) "#,##0" else "#,##0.#"
            return "${DecimalFormat(pattern).format(value)} ${units[digitGroups]}"
        }

        fun formatTimestamp(millis: Long, context: Context? = null): String {
            if (millis <= 0L) return "Unknown date"
            val ctx = context ?: runCatching { AuroraApplication.instance }.getOrNull()
            val is24 = if (ctx != null) DateFormat.is24HourFormat(ctx) else false
            val timePattern = if (is24) "HH:mm" else "h:mm a"
            val sdf = SimpleDateFormat("MMM d, yyyy • $timePattern", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatShortDate(millis: Long): String {
            if (millis <= 0L) return "Unknown"
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatDuration(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Detailed on-demand metadata extracted off the main thread for the File Details sheet and viewers.
 */
@Immutable
data class DetailedFileMetadata(
    val fileItem: FileItem,
    val readable: Boolean,
    val writable: Boolean,
    val imageDimensions: String? = null,
    val cameraMakeModel: String? = null,
    val videoResolution: String? = null,
    val videoDurationFormatted: String? = null,
    val audioArtist: String? = null,
    val audioAlbum: String? = null,
    val audioTitle: String? = null,
    val audioBitrateKbps: Int? = null,
    val audioDurationFormatted: String? = null,
    val pdfPageCount: Int? = null,
    val apkAppName: String? = null,
    val apkPackageName: String? = null,
    val apkVersionName: String? = null,
    val apkVersionCode: Long? = null,
    val apkMinSdk: Int? = null,
    val apkTargetSdk: Int? = null,
    val zipEntryCount: Int? = null,
    val zipUncompressedBytes: Long? = null,
    val sha256Hash: String? = null,
    val md5Hash: String? = null
)

/**
 * Sorting fields and directions.
 */
enum class SortField(val displayName: String) {
    DATE_ADDED("Date Added"),
    DATE_MODIFIED("Date Modified"),
    NAME("Name"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortOrder(val displayName: String) {
    DESCENDING("Descending (Newest / Largest / Z–A)"),
    ASCENDING("Ascending (Oldest / Smallest / A–Z)")
}

enum class ViewMode(val displayName: String) {
    LIST("List"),
    GRID("Grid")
}

enum class GroupingMode(val displayName: String) {
    NONE("No Grouping"),
    BY_DATE("Group by Date"),
    BY_CATEGORY("Group by Category")
}

/**
 * Conflict resolution strategy when copying, moving, or extracting files.
 */
enum class ConflictStrategy(val displayName: String, val description: String) {
    KEEP_BOTH_RENAME("Keep Both (Auto-Rename)", "Appends (1), (2), etc. so no existing file is overwritten"),
    REPLACE("Replace Existing", "Overwrites the destination file after confirmation"),
    SKIP("Skip Conflicting Files", "Leaves existing files untouched and skips duplicates")
}

/**
 * Clipboard state for Copy / Cut & Paste operations.
 */
@Immutable
data class FileClipboard(
    val items: List<FileItem>,
    val isMoveOperation: Boolean
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Progress state for long-running file or archive operations.
 */
@Immutable
data class FileOperationProgress(
    val operationTitle: String,
    val currentFileName: String,
    val processedCount: Int,
    val totalCount: Int,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isCancellable: Boolean = true
) {
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalCount > 0 -> (processedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * Result of a file management operation.
 */
sealed class OperationResult {
    data class Success(
        val message: String,
        val affectedCount: Int,
        val skippedCount: Int = 0
    ) : OperationResult()

    data class ConflictDetected(
        val conflictingFileNames: List<String>,
        val totalFiles: Int
    ) : OperationResult()

    data class Error(
        val userMessage: String,
        val technicalDetail: String? = null
    ) : OperationResult()
}
