package com.aurora.filemanager.util

import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

@Immutable
data class CategoryUsage(
    val category: FileCategory,
    val totalBytes: Long,
    val fileCount: Int
)

@Immutable
data class DirectoryUsage(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val fileCount: Int
)

@Immutable
data class DuplicateGroup(
    val sha256Hash: String,
    val fileSizeEach: Long,
    val files: List<FileItem>
) {
    val reclaimableBytes: Long
        get() = if (files.size > 1) fileSizeEach * (files.size - 1) else 0L
}

@Immutable
data class StorageAnalysisReport(
    val totalStorageBytes: Long,
    val usedStorageBytes: Long,
    val availableStorageBytes: Long,
    val accessibleUserFilesBytes: Long,
    val categoryBreakdown: List<CategoryUsage>,
    val largestFiles: List<FileItem>,
    val largeVideos: List<FileItem>,
    val largeDownloads: List<FileItem>,
    val storageHeavyDirectories: List<DirectoryUsage>,
    val exactDuplicateGroups: List<DuplicateGroup>
) {
    val systemAndOtherInaccessibleBytes: Long
        get() = (usedStorageBytes - accessibleUserFilesBytes).coerceAtLeast(0L)
}

/**
 * Performs on-demand storage analysis and content-hash duplicate detection.
 *
 * Duplicate Detection Algorithm:
 * 1. Groups candidate files by exact byte size (`sizeBytes > 0`) as a fast O(N) initial filter.
 * 2. For size groups with 2+ files, computes a fast 16 KB partial fingerprint (head + tail).
 * 3. For matching fingerprints, computes full streaming SHA-256 content hashes.
 * Never claims files are duplicates solely because they share the same filename, and never auto-deletes.
 */
object StorageAnalyzer {

    fun queryVolumeMetrics(rootDir: File = Environment.getExternalStorageDirectory()): Triple<Long, Long, Long> {
        return try {
            val stat = StatFs(rootDir.absolutePath)
            val total = stat.totalBytes
            val avail = stat.availableBytes
            val used = (total - avail).coerceAtLeast(0L)
            Triple(total, used, avail)
        } catch (_: Exception) {
            Triple(0L, 0L, 0L)
        }
    }

    suspend fun analyzeAccessibleFiles(
        allAccessibleFiles: List<FileItem>,
        includeDuplicateScan: Boolean = true
    ): StorageAnalysisReport = withContext(Dispatchers.Default) {
        val (totalBytes, usedBytes, availBytes) = queryVolumeMetrics()
        val regularFiles = allAccessibleFiles.filter { !it.isDirectory }
        val accessibleSum = regularFiles.sumOf { it.sizeBytes }

        val categoryBreakdown = regularFiles
            .groupBy { it.category }
            .map { (category, list) ->
                CategoryUsage(
                    category = category,
                    totalBytes = list.sumOf { it.sizeBytes },
                    fileCount = list.size
                )
            }
            .sortedByDescending { it.totalBytes }

        val largestFiles = regularFiles
            .sortedByDescending { it.sizeBytes }
            .take(25)

        val largeVideos = regularFiles
            .filter { it.category == FileCategory.VIDEO && it.sizeBytes >= 15L * 1024L * 1024L }
            .sortedByDescending { it.sizeBytes }
            .take(20)

        val downloadPrefix = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.absolutePath ?: "/storage/emulated/0/Download"

        val largeDownloads = regularFiles
            .filter { it.path.startsWith(downloadPrefix, ignoreCase = true) && it.sizeBytes >= 5L * 1024L * 1024L }
            .sortedByDescending { it.sizeBytes }
            .take(20)

        val storageHeavyDirectories = regularFiles
            .groupBy { File(it.path).parent ?: "/" }
            .map { (parentPath, list) ->
                val folderFile = File(parentPath)
                DirectoryUsage(
                    name = folderFile.name.ifEmpty { parentPath },
                    path = parentPath,
                    totalBytes = list.sumOf { it.sizeBytes },
                    fileCount = list.size
                )
            }
            .sortedByDescending { it.totalBytes }
            .take(15)

        val duplicateGroups = if (includeDuplicateScan) {
            findExactDuplicates(regularFiles)
        } else {
            emptyList()
        }

        StorageAnalysisReport(
            totalStorageBytes = totalBytes,
            usedStorageBytes = usedBytes,
            availableStorageBytes = availBytes,
            accessibleUserFilesBytes = accessibleSum,
            categoryBreakdown = categoryBreakdown,
            largestFiles = largestFiles,
            largeVideos = largeVideos,
            largeDownloads = largeDownloads,
            storageHeavyDirectories = storageHeavyDirectories,
            exactDuplicateGroups = duplicateGroups
        )
    }

    /**
     * Exact duplicate detection using file size pre-filtering followed by SHA-256 content hashing.
     */
    suspend fun findExactDuplicates(files: List<FileItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        // Step 1: Filter by identical non-zero byte size (ignore tiny < 4KB files to avoid noise)
        val sizeCandidates = files
            .filter { !it.isDirectory && it.sizeBytes >= 4096L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size >= 2 }

        val confirmedGroups = mutableListOf<DuplicateGroup>()

        for ((sizeBytes, sameSizeItems) in sizeCandidates) {
            currentCoroutineContext().ensureActive()

            // Step 2: Fast partial sample hash for files > 64 KB
            val partialMatchedGroups = if (sizeBytes > 65_536L) {
                sameSizeItems.groupBy { item ->
                    computePartialSampleHash(File(item.path)) ?: ""
                }.filter { (key, list) -> key.isNotEmpty() && list.size >= 2 }.values
            } else {
                listOf(sameSizeItems)
            }

            // Step 3: Full SHA-256 content hash
            for (candidateGroup in partialMatchedGroups) {
                currentCoroutineContext().ensureActive()
                val byFullHash = candidateGroup.groupBy { item ->
                    computeFileHash(File(item.path), "SHA-256") ?: ""
                }
                for ((sha256, identicalFiles) in byFullHash) {
                    if (sha256.isNotEmpty() && identicalFiles.size >= 2) {
                        confirmedGroups.add(
                            DuplicateGroup(
                                sha256Hash = sha256,
                                fileSizeEach = sizeBytes,
                                files = identicalFiles.sortedBy { it.timestamps.primaryAddedOrDiscoveredMillis }
                            )
                        )
                    }
                }
            }
        }

        confirmedGroups.sortedByDescending { it.reclaimableBytes }
    }

    private fun computePartialSampleHash(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val sampleSize = 8192
            val buffer = ByteArray(sampleSize)
            FileInputStream(file).use { fis ->
                val readHead = fis.read(buffer)
                if (readHead > 0) digest.update(buffer, 0, readHead)
                val skipBytes = (file.length() - sampleSize * 2).coerceAtLeast(0L)
                if (skipBytes > 0) {
                    fis.skip(skipBytes)
                    val readTail = fis.read(buffer)
                    if (readTail > 0) digest.update(buffer, 0, readTail)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun computeFileHash(file: File, algorithm: String = "SHA-256"): String? {
        if (!file.exists() || !file.canRead() || file.isDirectory) return null
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(32 * 1024)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
