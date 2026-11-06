package com.aurora.filemanager.util

import androidx.compose.runtime.Immutable
import com.aurora.filemanager.data.model.ConflictStrategy
import com.aurora.filemanager.data.model.FileOperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Immutable
data class ZipEntryInfo(
    val entryPath: String,
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val lastModifiedMillis: Long
)

/**
 * Streaming ZIP archive inspector, extractor, and creator.
 *
 * Security & Memory Guarantees:
 * - Strictly validates every entry path against Zip-Slip path traversal attacks.
 * - Uses streaming 32 KB buffers rather than loading entire archives into memory.
 * - Supports coroutine cancellation and cleans up partial output files when cancelled.
 */
object ZipManager {

    private const val BUFFER_SIZE = 32 * 1024

    /**
     * Validates that extracting [entryName] into [destinationDir] will never escape [destinationDir].
     * Throws [SecurityException] if a Zip-Slip path traversal attempt is detected.
     */
    fun validateZipEntryTarget(destinationDir: File, entryName: String): File {
        val destCanonicalDirPath = destinationDir.canonicalPath
        val targetFile = File(destinationDir, entryName)
        val targetCanonicalPath = targetFile.canonicalPath
        val expectedPrefix = if (destCanonicalDirPath.endsWith(File.separator)) {
            destCanonicalDirPath
        } else {
            destCanonicalDirPath + File.separator
        }
        if (targetCanonicalPath != destCanonicalDirPath && !targetCanonicalPath.startsWith(expectedPrefix)) {
            throw SecurityException("Blocked Zip-Slip path traversal entry: $entryName")
        }
        return targetFile
    }

    /**
     * Lists entries inside a ZIP file without extracting them.
     */
    suspend fun listEntries(zipFile: File): Result<List<ZipEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = mutableListOf<ZipEntryInfo>()
            ZipFile(zipFile).use { zf ->
                val enumeration = zf.entries()
                while (enumeration.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = enumeration.nextElement()
                    val cleanName = entry.name.trimEnd('/')
                    val displayName = cleanName.substringAfterLast('/')
                    entries.add(
                        ZipEntryInfo(
                            entryPath = entry.name,
                            name = displayName.ifEmpty { entry.name },
                            isDirectory = entry.isDirectory,
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            uncompressedSize = entry.size.coerceAtLeast(0L),
                            lastModifiedMillis = entry.time.coerceAtLeast(0L)
                        )
                    )
                }
            }
            entries.sortedWith(
                compareByDescending<ZipEntryInfo> { it.isDirectory }
                    .thenBy { it.entryPath.lowercase() }
            )
        }
    }

    /**
     * Extracts either all entries or [selectedEntryPaths] from [zipFile] into [destinationDir].
     */
    suspend fun extractArchive(
        zipFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        conflictStrategy: ConflictStrategy = ConflictStrategy.KEEP_BOTH_RENAME,
        onProgress: suspend (FileOperationProgress) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        val createdFilesThisRun = mutableListOf<File>()
        try {
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                return@withContext Result.failure(IOException("Unable to create destination folder: ${destinationDir.absolutePath}"))
            }

            var extractedCount = 0
            ZipFile(zipFile).use { zf ->
                val allEntries = zf.entries().asSequence().filter { entry ->
                    selectedEntryPaths == null || entry.name in selectedEntryPaths
                }.toList()

                val totalCount = allEntries.size.coerceAtLeast(1)
                val buffer = ByteArray(BUFFER_SIZE)

                allEntries.forEachIndexed { index, entry ->
                    currentCoroutineContext().ensureActive()
                    val rawTarget = validateZipEntryTarget(destinationDir, entry.name)

                    onProgress(
                        FileOperationProgress(
                            operationTitle = "Extracting ${zipFile.name}",
                            currentFileName = rawTarget.name,
                            processedCount = index,
                            totalCount = totalCount
                        )
                    )

                    if (entry.isDirectory) {
                        rawTarget.mkdirs()
                    } else {
                        val parent = rawTarget.parentFile ?: destinationDir
                        if (!parent.exists()) parent.mkdirs()

                        val finalTarget = if (rawTarget.exists()) {
                            when (conflictStrategy) {
                                ConflictStrategy.SKIP -> return@forEachIndexed
                                ConflictStrategy.REPLACE -> rawTarget
                                ConflictStrategy.KEEP_BOTH_RENAME -> {
                                    val siblings = parent.list()?.toSet() ?: emptySet()
                                    val uniqueName = FileSorterAndFilter.resolveUniqueFileName(
                                        originalName = rawTarget.name,
                                        isDirectory = false,
                                        existingNames = siblings
                                    )
                                    File(parent, uniqueName)
                                }
                            }
                        } else {
                            rawTarget
                        }

                        createdFilesThisRun.add(finalTarget)
                        zf.getInputStream(entry).use { input ->
                            BufferedOutputStream(FileOutputStream(finalTarget), BUFFER_SIZE).use { output ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    currentCoroutineContext().ensureActive()
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        if (entry.time > 0) {
                            finalTarget.setLastModified(entry.time)
                        }
                        extractedCount++
                    }
                }
            }
            Result.success(extractedCount)
        } catch (ce: CancellationException) {
            createdFilesThisRun.forEach { runCatching { if (it.isFile) it.delete() } }
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Compresses [sourceFiles] into [targetZipFile] with progress reporting and safe cancellation cleanup.
     */
    suspend fun createArchive(
        sourceFiles: List<File>,
        targetZipFile: File,
        onProgress: suspend (FileOperationProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val allFilesToCompress = mutableListOf<Pair<File, String>>()
            sourceFiles.forEach { root ->
                collectFilesForArchive(root, root.parentFile ?: root, allFilesToCompress)
            }
            val totalFiles = allFilesToCompress.size.coerceAtLeast(1)
            val buffer = ByteArray(BUFFER_SIZE)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipFile), BUFFER_SIZE)).use { zos ->
                allFilesToCompress.forEachIndexed { index, (file, relativePath) ->
                    currentCoroutineContext().ensureActive()
                    onProgress(
                        FileOperationProgress(
                            operationTitle = "Creating ${targetZipFile.name}",
                            currentFileName = file.name,
                            processedCount = index,
                            totalCount = totalFiles
                        )
                    )
                    if (file.isDirectory) {
                        val dirEntry = ZipEntry(if (relativePath.endsWith("/")) relativePath else "$relativePath/")
                        dirEntry.time = file.lastModified()
                        zos.putNextEntry(dirEntry)
                        zos.closeEntry()
                    } else {
                        val entry = ZipEntry(relativePath)
                        entry.time = file.lastModified()
                        entry.size = file.length()
                        zos.putNextEntry(entry)
                        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { bis ->
                            var count: Int
                            while (bis.read(buffer).also { count = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                zos.write(buffer, 0, count)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            Result.success(targetZipFile)
        } catch (ce: CancellationException) {
            runCatching { targetZipFile.delete() }
            throw ce
        } catch (t: Throwable) {
            runCatching { targetZipFile.delete() }
            Result.failure(t)
        }
    }

    private fun collectFilesForArchive(
        current: File,
        baseDir: File,
        out: MutableList<Pair<File, String>>
    ) {
        val relPath = current.relativeTo(baseDir).path.replace('\\', '/')
        if (current.isDirectory) {
            val children = current.listFiles()
            if (children.isNullOrEmpty()) {
                out.add(current to "$relPath/")
            } else {
                children.forEach { child ->
                    collectFilesForArchive(child, baseDir, out)
                }
            }
        } else if (current.isFile) {
            out.add(current to relPath)
        }
    }
}
