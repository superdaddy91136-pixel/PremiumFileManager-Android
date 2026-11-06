package com.aurora.filemanager.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.aurora.filemanager.data.local.AuroraDao
import com.aurora.filemanager.data.local.DiscoveredFileEntity
import com.aurora.filemanager.data.local.FavoriteEntity
import com.aurora.filemanager.data.local.PinnedItemEntity
import com.aurora.filemanager.data.local.OperationHistoryEntity
import com.aurora.filemanager.data.local.RecentOpenedEntity
import com.aurora.filemanager.data.local.RecentSearchEntity
import com.aurora.filemanager.data.local.TrashEntity
import com.aurora.filemanager.data.model.ConflictStrategy
import com.aurora.filemanager.data.model.FileCategory
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.FileOperationProgress
import com.aurora.filemanager.data.model.FileTimestamps
import com.aurora.filemanager.data.model.OperationResult
import com.aurora.filemanager.data.model.TimestampKind
import com.aurora.filemanager.util.FileSorterAndFilter
import com.aurora.filemanager.util.StoragePermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

/**
 * Core repository responsible for:
 * 1. Enumerating real files in the shared storage hierarchy (`Storage` section).
 * 2. Building the unified chronological `Recents` feed across Images, Videos, Audio, PDFs,
 *    Documents, APKs, Archives, and Code/Text files.
 * 3. Accurately resolving and labeling timestamps (`MediaStore.MediaColumns.DATE_ADDED`,
 *    filesystem `BasicFileAttributes.creationTime()`, locally persisted `firstDiscoveredAtMillis`,
 *    `lastModified()`, and `lastOpenedAtMillis`).
 * 4. Maintaining an incremental in-memory and local Room index so searches and tab switches
 *    do not rescan the entire device on every keystroke or navigation event.
 * 5. Executing genuine, verified file operations (Copy, Move/Cut, Rename, Delete, Create Folder,
 *    Share, Open, Install APK) with conflict detection and progress callbacks.
 */
class FileRepository(
    private val context: Context,
    private val dao: AuroraDao
) {
    private val indexMutex = Mutex()
    private var cachedAccessibleIndex: List<FileItem>? = null
    private var lastIndexTimestampMs: Long = 0L

    val favoritesFlow: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val pinnedItemsFlow: Flow<List<PinnedItemEntity>> = dao.observePinnedItems()
    val recentOpenedFlow: Flow<List<RecentOpenedEntity>> = dao.observeRecentOpened()
    val recentSearchesFlow: Flow<List<RecentSearchEntity>> = dao.observeRecentSearches()
    val operationHistoryFlow: Flow<List<OperationHistoryEntity>> = dao.observeOperationHistory()
    val trashItemsFlow: Flow<List<TrashEntity>> = dao.observeTrashFiles()

    fun getPrimaryStorageRoot(): File {
        return Environment.getExternalStorageDirectory()
    }

    private fun getTrashRoot(): File {
        val root = File(getPrimaryStorageRoot(), ".Trash")
        if (!root.exists()) {
            root.mkdirs()
            File(root, ".nomedia").createNewFile()
        }
        return root
    }

    /**
     * Lists standard top-level shared storage directories (DCIM, Download, Documents, Movies,
     * Music, Pictures, Android, Podcasts, Ringtones) plus any other actual directories in root.
     */
    suspend fun listDirectory(
        directory: File,
        showHidden: Boolean
    ): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!directory.exists()) {
                throw IOException("Folder does not exist: ${directory.absolutePath}")
            }
            if (StoragePermissionHelper.isScopedStorageRestrictedDirectory(directory)) {
                throw SecurityException(
                    "Android Scoped Storage restricts direct access to ${directory.name}. " +
                        "System security prevents third-party apps from reading private app data folders."
                )
            }

            val rawChildren = directory.listFiles()
                ?: throw SecurityException("Unable to read directory contents. Check storage permissions.")

            val filtered = rawChildren.filter { file ->
                showHidden || !file.name.startsWith(".")
            }

            val favoritePaths = dao.getAllFavoritePaths().toSet()
            val pinnedPaths = dao.getPinnedPaths().toSet()
            val pathsToLookup = filtered.map { it.absolutePath }
            val discoveredMap = dao.getDiscoveredFiles(pathsToLookup.take(900)).associateBy { it.path }
            val now = System.currentTimeMillis()
            val newlyDiscovered = mutableListOf<DiscoveredFileEntity>()

            val items = filtered.map { file ->
                currentCoroutineContext().ensureActive()
                val existingDiscovered = discoveredMap[file.absolutePath]
                if (existingDiscovered == null && !file.isDirectory) {
                    newlyDiscovered.add(
                        DiscoveredFileEntity(
                            path = file.absolutePath,
                            firstDiscoveredAtMillis = now,
                            lastSeenModifiedMillis = file.lastModified()
                        )
                    )
                }
                mapFileToFileItem(
                    file = file,
                    favoritePaths = favoritePaths,
                    pinnedPaths = pinnedPaths,
                    discoveredEntity = existingDiscovered,
                    mediaStoreDateAddedSec = null,
                    fallbackDiscoveryMillis = now
                )
            }

            if (newlyDiscovered.isNotEmpty()) {
                dao.insertDiscoveredFilesIgnore(newlyDiscovered)
            }

            items
        }
    }

    /**
     * Queries recently added files from all supported file types in a unified chronological feed.
     * Combines MediaStore indexed metadata (which provides authoritative `DATE_ADDED`, duration,
     * dimensions, artist, and album) with a bounded scan of standard user folders (Download, Documents,
     * DCIM, Pictures, Movies, Music) so non-media files (APKs, ZIPs, code, markdown) are always included
     * even if MediaStore has not indexed them yet.
     */
    suspend fun loadRecentsFeed(
        showHidden: Boolean,
        limit: Int = 250,
        forceRefresh: Boolean = false
    ): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val allFiles = getOrBuildIncrementalIndex(showHidden = showHidden, forceRefresh = forceRefresh)
            allFiles
                .filter { !it.isDirectory }
                .sortedByDescending { it.timestamps.primaryAddedOrDiscoveredMillis }
                .take(limit)
        }
    }

    /**
     * Returns or incrementally refreshes the local file index across accessible storage.
     * Avoids full-storage rescans on every keystroke or tab switch.
     */
    suspend fun getOrBuildIncrementalIndex(
        showHidden: Boolean,
        forceRefresh: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            val now = System.currentTimeMillis()
            val currentCache = cachedAccessibleIndex
            if (!forceRefresh && currentCache != null && (now - lastIndexTimestampMs) < 45_000L) {
                return@withLock if (showHidden) {
                    currentCache
                } else {
                    currentCache.filter { !it.isHidden }
                }
            }

            val favoritePaths = dao.getAllFavoritePaths().toSet()
            val pinnedPaths = dao.getPinnedPaths().toSet()
            val byPath = LinkedHashMap<String, FileItem>(512)

            // 1. Query MediaStore.Files for fast, system-indexed metadata & DATE_ADDED
            queryMediaStoreFiles(favoritePaths, pinnedPaths).forEach { item ->
                byPath[item.path] = item
            }

            // 2. Walk accessible user directories (Download, Documents, DCIM, Pictures, Music, Movies, root)
            // up to a sensible depth to capture APKs, ZIPs, text/code files, and newly created files.
            val rootDir = getPrimaryStorageRoot()
            if (rootDir.exists() && rootDir.canRead()) {
                walkAccessibleStorageBounded(
                    rootDir = rootDir,
                    maxDepth = 5,
                    favoritePaths = favoritePaths,
                    pinnedPaths = pinnedPaths,
                    outMap = byPath
                )
            }

            // 3. Reconcile with Room `discovered_files` so first-discovered and last-opened timestamps persist
            val allPaths = byPath.keys.toList()
            val discoveredMap = HashMap<String, DiscoveredFileEntity>(allPaths.size)
            allPaths.chunked(800).forEach { chunk ->
                dao.getDiscoveredFiles(chunk).forEach { entity ->
                    discoveredMap[entity.path] = entity
                }
            }

            val newDiscoveredEntities = mutableListOf<DiscoveredFileEntity>()
            val reconciled = byPath.values.map { item ->
                val disc = discoveredMap[item.path]
                if (disc == null && !item.isDirectory) {
                    newDiscoveredEntities.add(
                        DiscoveredFileEntity(
                            path = item.path,
                            firstDiscoveredAtMillis = item.timestamps.primaryAddedOrDiscoveredMillis,
                            lastSeenModifiedMillis = item.timestamps.lastModifiedMillis
                        )
                    )
                    item
                } else if (disc != null) {
                    val resolvedAddedMillis = when (item.timestamps.primaryAddedSource) {
                        TimestampKind.MEDIASTORE_DATE_ADDED,
                        TimestampKind.FILESYSTEM_CREATION -> item.timestamps.primaryAddedOrDiscoveredMillis
                        else -> disc.firstDiscoveredAtMillis
                    }
                    val resolvedSource = when (item.timestamps.primaryAddedSource) {
                        TimestampKind.MEDIASTORE_DATE_ADDED,
                        TimestampKind.FILESYSTEM_CREATION -> item.timestamps.primaryAddedSource
                        else -> TimestampKind.FIRST_DISCOVERED_LOCAL
                    }
                    item.copy(
                        isFavorite = item.path in favoritePaths,
                        isPinned = item.path in pinnedPaths,
                        timestamps = item.timestamps.copy(
                            primaryAddedOrDiscoveredMillis = resolvedAddedMillis,
                            primaryAddedSource = resolvedSource,
                            lastOpenedMillis = disc.lastOpenedAtMillis
                        )
                    )
                } else {
                    item
                }
            }

            if (newDiscoveredEntities.isNotEmpty()) {
                dao.insertDiscoveredFilesIgnore(newDiscoveredEntities)
            }

            cachedAccessibleIndex = reconciled
            lastIndexTimestampMs = now

            if (showHidden) reconciled else reconciled.filter { !it.isHidden }
        }
    }

    fun invalidateIndexCache() {
        lastIndexTimestampMs = 0L
    }

    private fun queryMediaStoreFiles(favoritePaths: Set<String>, pinnedPaths: Set<String>): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val volumeUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.ALBUM_ID
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                volumeUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (path.isNullOrBlank()) continue
                    val file = File(path)
                    if (!file.exists() || file.isDirectory) continue

                    val name = (if (nameCol >= 0) cursor.getString(nameCol) else null) ?: file.name
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else file.length()
                    val dateAddedSec = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val dateModSec = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null)
                        ?: resolveMimeType(file)
                    val mediaType = if (mediaTypeCol >= 0) cursor.getInt(mediaTypeCol) else 0
                    val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else null
                    val width = if (widthCol >= 0 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else null
                    val height = if (heightCol >= 0 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else null
                    val artist = if (artistCol >= 0 && !cursor.isNull(artistCol)) cursor.getString(artistCol) else null
                    val album = if (albumCol >= 0 && !cursor.isNull(albumCol)) cursor.getString(albumCol) else null
                    val albumId = if (albumIdCol >= 0 && !cursor.isNull(albumIdCol)) cursor.getLong(albumIdCol) else null

                    val contentUri = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        else -> ContentUris.withAppendedId(volumeUri, id)
                    }

                    val ext = file.extension.lowercase(Locale.ROOT)
                    val category = FileCategory.fromFile(false, ext, mime)
                    val modMillis = if (dateModSec > 0L) dateModSec * 1000L else file.lastModified()
                    val addedMillis = if (dateAddedSec > 0L) dateAddedSec * 1000L else modMillis
                    val addedKind = if (dateAddedSec > 0L) {
                        TimestampKind.MEDIASTORE_DATE_ADDED
                    } else {
                        TimestampKind.LAST_MODIFIED_FALLBACK
                    }

                    results.add(
                        FileItem(
                            id = file.absolutePath,
                            name = name,
                            path = file.absolutePath,
                            contentUri = contentUri,
                            isDirectory = false,
                            sizeBytes = size,
                            extension = ext,
                            mimeType = mime,
                            category = category,
                            timestamps = FileTimestamps(
                                primaryAddedOrDiscoveredMillis = addedMillis,
                                primaryAddedSource = addedKind,
                                creationTimeMillis = extractFilesystemCreationMillis(file),
                                lastModifiedMillis = modMillis
                            ),
                            isHidden = name.startsWith("."),
                            isFavorite = file.absolutePath in favoritePaths,
                            isPinned = file.absolutePath in pinnedPaths,
                            durationMillis = duration?.takeIf { it > 0L },
                            width = width?.takeIf { it > 0 },
                            height = height?.takeIf { it > 0 },
                            artist = artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
                            album = album?.takeIf { it.isNotBlank() && it != "<unknown>" },
                            mediaStoreAlbumId = albumId?.takeIf { it > 0L }
                        )
                    )
                    count++
                }
            }
        }
        return results
    }

    private suspend fun walkAccessibleStorageBounded(
        rootDir: File,
        maxDepth: Int,
        favoritePaths: Set<String>,
        pinnedPaths: Set<String>,
        outMap: MutableMap<String, FileItem>
    ) {
        val now = System.currentTimeMillis()

        fun traverse(dir: File, depth: Int) {
            if (depth > maxDepth) return
            if (StoragePermissionHelper.isScopedStorageRestrictedDirectory(dir)) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.equals("Android", ignoreCase = true) && depth == 0) {
                        // Include top-level Android folder in storage browser, but skip deep walk of Android/data
                        outMap.putIfAbsent(
                            child.absolutePath,
                            mapFileToFileItem(child, favoritePaths, pinnedPaths, null, null, now)
                        )
                        continue
                    }
                    outMap.putIfAbsent(
                        child.absolutePath,
                        mapFileToFileItem(child, favoritePaths, pinnedPaths, null, null, now)
                    )
                    if (!child.name.startsWith(".")) {
                        traverse(child, depth + 1)
                    }
                } else {
                    if (!outMap.containsKey(child.absolutePath)) {
                        outMap[child.absolutePath] = mapFileToFileItem(
                            file = child,
                            favoritePaths = favoritePaths,
                            pinnedPaths = pinnedPaths,
                            discoveredEntity = null,
                            mediaStoreDateAddedSec = null,
                            fallbackDiscoveryMillis = now
                        )
                    }
                }
            }
        }

        traverse(rootDir, 0)
    }

    fun mapFileToFileItem(
        file: File,
        favoritePaths: Set<String>,
        pinnedPaths: Set<String>,
        discoveredEntity: DiscoveredFileEntity?,
        mediaStoreDateAddedSec: Long?,
        fallbackDiscoveryMillis: Long = System.currentTimeMillis()
    ): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
        val mime = if (isDir) "vnd.android.document/directory" else resolveMimeType(file)
        val category = FileCategory.fromFile(isDir, ext, mime)
        val lastModified = file.lastModified()
        val fsCreation = extractFilesystemCreationMillis(file)

        val (primaryAddedMillis, primarySource) = when {
            mediaStoreDateAddedSec != null && mediaStoreDateAddedSec > 0L ->
                (mediaStoreDateAddedSec * 1000L) to TimestampKind.MEDIASTORE_DATE_ADDED
            fsCreation != null && fsCreation > 0L && fsCreation != lastModified ->
                fsCreation to TimestampKind.FILESYSTEM_CREATION
            discoveredEntity != null ->
                discoveredEntity.firstDiscoveredAtMillis to TimestampKind.FIRST_DISCOVERED_LOCAL
            lastModified > 0L ->
                lastModified to TimestampKind.LAST_MODIFIED_FALLBACK
            else ->
                fallbackDiscoveryMillis to TimestampKind.FIRST_DISCOVERED_LOCAL
        }

        val childCount = if (isDir && !StoragePermissionHelper.isScopedStorageRestrictedDirectory(file)) {
            runCatching { file.list()?.size }.getOrNull()
        } else {
            null
        }

        return FileItem(
            id = file.absolutePath,
            name = file.name.ifEmpty { file.absolutePath },
            path = file.absolutePath,
            contentUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull(),
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            extension = ext,
            mimeType = mime,
            category = category,
            timestamps = FileTimestamps(
                primaryAddedOrDiscoveredMillis = primaryAddedMillis,
                primaryAddedSource = primarySource,
                creationTimeMillis = fsCreation,
                lastModifiedMillis = lastModified,
                lastOpenedMillis = discoveredEntity?.lastOpenedAtMillis
            ),
            isHidden = file.name.startsWith("."),
            isFavorite = file.absolutePath in favoritePaths,
            isPinned = file.absolutePath in pinnedPaths,
            childCount = childCount,
            isRestrictedSystemFolder = StoragePermissionHelper.isScopedStorageRestrictedDirectory(file)
        )
    }

    private fun extractFilesystemCreationMillis(file: File): Long? {
        return try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            val creationMillis = attrs.creationTime().toMillis()
            if (creationMillis > 0L) creationMillis else null
        } catch (_: Throwable) {
            null
        }
    }

    fun resolveMimeType(file: File): String {
        if (file.isDirectory) return "vnd.android.document/directory"
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext.isEmpty()) return "application/octet-stream"
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
            "md", "kt", "kts", "java", "py", "js", "ts", "json", "xml", "yaml", "yml", "sh", "log", "ini", "conf" -> "text/plain"
            "mkv" -> "video/x-matroska"
            "flac" -> "audio/flac"
            "opus" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    // =========================================================================
    // GENUINE FILE OPERATIONS (Copy, Move/Cut, Rename, Delete, Create Folder)
    // =========================================================================

    /**
     * Checks whether pasting [sourceItems] into [destinationDir] will collide with existing files.
     */
    suspend fun detectConflicts(
        sourceItems: List<FileItem>,
        destinationDir: File
    ): List<String> = withContext(Dispatchers.IO) {
        if (!destinationDir.exists() || !destinationDir.isDirectory) return@withContext emptyList()
        val existingLower = (destinationDir.list() ?: emptyArray())
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        sourceItems.mapNotNull { item ->
            if (item.name.lowercase(Locale.ROOT) in existingLower) item.name else null
        }
    }

    /**
     * Copies or Moves [sourceItems] into [destinationDir] with conflict handling, free-space verification,
     * progress reporting, cancellation support, and MediaScanner notification.
     */
    suspend fun copyOrMoveFiles(
        sourceItems: List<FileItem>,
        destinationDir: File,
        isMove: Boolean,
        conflictStrategy: ConflictStrategy,
        onProgress: suspend (FileOperationProgress) -> Unit
    ): OperationResult = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                return@withContext OperationResult.Error(
                    "Destination directory is unavailable or cannot be created: ${destinationDir.absolutePath}"
                )
            }
            if (!destinationDir.canWrite()) {
                return@withContext OperationResult.Error(
                    "Permission denied: Cannot write to ${destinationDir.name}."
                )
            }

            // Prevent copying/moving a folder into itself or its own subfolder
            val destCanonical = destinationDir.canonicalPath
            for (item in sourceItems) {
                val srcFile = File(item.path)
                if (!srcFile.exists()) {
                    return@withContext OperationResult.Error("Source file no longer exists: ${item.name}")
                }
                if (srcFile.isDirectory) {
                    val srcCanonical = srcFile.canonicalPath
                    if (destCanonical == srcCanonical || destCanonical.startsWith(srcCanonical + File.separator)) {
                        return@withContext OperationResult.Error(
                            "Cannot ${if (isMove) "move" else "copy"} folder '${item.name}' into itself."
                        )
                    }
                }
            }

            // Verify sufficient storage space for copy (or cross-volume move)
            val requiredBytes = sourceItems.sumOf { item ->
                val f = File(item.path)
                if (f.isDirectory) computeDirectorySizeBounded(f) else f.length()
            }
            val availableBytes = runCatching { StatFs(destinationDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (!isMove && requiredBytes > availableBytes) {
                return@withContext OperationResult.Error(
                    "Insufficient storage space. Required: ${FileItem.formatFileSize(requiredBytes)}, " +
                        "Available: ${FileItem.formatFileSize(availableBytes)}."
                )
            }

            var processedCount = 0
            var skippedCount = 0
            val totalCount = sourceItems.size.coerceAtLeast(1)
            val scannedPaths = mutableListOf<String>()

            for ((index, item) in sourceItems.withIndex()) {
                currentCoroutineContext().ensureActive()
                val srcFile = File(item.path)
                if (!srcFile.exists()) {
                    skippedCount++
                    continue
                }

                onProgress(
                    FileOperationProgress(
                        operationTitle = if (isMove) "Moving files…" else "Copying files…",
                        currentFileName = srcFile.name,
                        processedCount = index,
                        totalCount = totalCount
                    )
                )

                val initialTarget = File(destinationDir, srcFile.name)
                if (initialTarget.canonicalPath == srcFile.canonicalPath) {
                    if (isMove) {
                        skippedCount++
                        continue
                    }
                }

                val targetFile = if (initialTarget.exists()) {
                    when (conflictStrategy) {
                        ConflictStrategy.SKIP -> {
                            skippedCount++
                            continue
                        }
                        ConflictStrategy.REPLACE -> {
                            if (initialTarget.canonicalPath != srcFile.canonicalPath) {
                                deleteRecursivelyInternal(initialTarget)
                            }
                            initialTarget
                        }
                        ConflictStrategy.KEEP_BOTH_RENAME -> {
                            val siblings = destinationDir.list()?.toSet() ?: emptySet()
                            val uniqueName = FileSorterAndFilter.resolveUniqueFileName(
                                originalName = srcFile.name,
                                isDirectory = srcFile.isDirectory,
                                existingNames = siblings
                            )
                            File(destinationDir, uniqueName)
                        }
                    }
                } else {
                    initialTarget
                }

                val itemSucceeded = if (isMove) {
                    // Attempt atomic rename first; fall back to copy + delete
                    if (srcFile.renameTo(targetFile)) {
                        true
                    } else {
                        val copied = copyRecursivelyCancellable(srcFile, targetFile)
                        if (copied) {
                            deleteRecursivelyInternal(srcFile)
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    copyRecursivelyCancellable(srcFile, targetFile)
                }

                if (itemSucceeded && targetFile.exists()) {
                    processedCount++
                    scannedPaths.add(targetFile.absolutePath)
                    if (isMove) {
                        scannedPaths.add(srcFile.absolutePath)
                    }
                } else {
                    return@withContext OperationResult.Error(
                        "Failed to ${if (isMove) "move" else "copy"} '${srcFile.name}'."
                    )
                }
            }

            triggerMediaScan(scannedPaths)
            invalidateIndexCache()

            val actionVerb = if (isMove) "Moved" else "Copied"
            val summary = "$actionVerb $processedCount item${if (processedCount == 1) "" else "s"} to ${destinationDir.name}"
            dao.insertOperationLog(
                OperationHistoryEntity(
                    actionType = if (isMove) "MOVE" else "COPY",
                    summary = summary,
                    targetPath = destinationDir.absolutePath,
                    timestampMillis = System.currentTimeMillis(),
                    succeeded = true
                )
            )

            OperationResult.Success(
                message = summary + if (skippedCount > 0) " ($skippedCount skipped)" else "",
                affectedCount = processedCount,
                skippedCount = skippedCount
            )
        } catch (ce: CancellationException) {
            invalidateIndexCache()
            throw ce
        } catch (t: Throwable) {
            OperationResult.Error(
                userMessage = t.localizedMessage ?: "File operation failed unexpectedly.",
                technicalDetail = t.toString()
            )
        }
    }

    private suspend fun copyRecursivelyCancellable(src: File, dst: File): Boolean {
        currentCoroutineContext().ensureActive()
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) return false
            val children = src.listFiles() ?: return false
            for (child in children) {
                val childTarget = File(dst, child.name)
                if (!copyRecursivelyCancellable(child, childTarget)) return false
            }
            dst.setLastModified(src.lastModified())
            return true
        } else {
            val parent = dst.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val buffer = ByteArray(32 * 1024)
            try {
                BufferedInputStream(FileInputStream(src), buffer.size).use { input ->
                    BufferedOutputStream(FileOutputStream(dst), buffer.size).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                        }
                    }
                }
                dst.setLastModified(src.lastModified())
                return dst.exists() && dst.length() == src.length()
            } catch (ce: CancellationException) {
                runCatching { dst.delete() }
                throw ce
            } catch (_: Exception) {
                runCatching { dst.delete() }
                return false
            }
        }
    }

    /**
     * Renames a single file or folder, preventing silent overwrites of existing sibling files.
     */
    suspend fun renameFile(item: FileItem, newName: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            return@withContext OperationResult.Error("Please enter a valid filename without path separators.")
        }
        val srcFile = File(item.path)
        if (!srcFile.exists()) {
            return@withContext OperationResult.Error("File no longer exists: ${item.name}")
        }
        val parent = srcFile.parentFile
            ?: return@withContext OperationResult.Error("Cannot rename root directory.")
        val targetFile = File(parent, trimmed)
        if (targetFile.exists() && !targetFile.absolutePath.equals(srcFile.absolutePath, ignoreCase = false)) {
            return@withContext OperationResult.Error("A file or folder named '$trimmed' already exists in this location.")
        }

        val renamed = srcFile.renameTo(targetFile)
        if (!renamed || !targetFile.exists()) {
            return@withContext OperationResult.Error("Could not rename '${item.name}'. Check storage permissions.")
        }

        // Keep Favorites and Recent Opened in sync if this path was favorited
        val favPaths = dao.getAllFavoritePaths()
        if (item.path in favPaths) {
            dao.removeFavorite(item.path)
            dao.upsertFavorite(
                FavoriteEntity(
                    path = targetFile.absolutePath,
                    name = targetFile.name,
                    isDirectory = targetFile.isDirectory,
                    addedAtMillis = System.currentTimeMillis()
                )
            )
        }

        triggerMediaScan(listOf(srcFile.absolutePath, targetFile.absolutePath))
        invalidateIndexCache()

        dao.insertOperationLog(
            OperationHistoryEntity(
                actionType = "RENAME",
                summary = "Renamed '${item.name}' to '${targetFile.name}'",
                targetPath = targetFile.absolutePath,
                timestampMillis = System.currentTimeMillis(),
                succeeded = true
            )
        )

        OperationResult.Success("Renamed to '${targetFile.name}'", affectedCount = 1)
    }

    /**
     * Deletes one or more files/folders after explicit user confirmation and cleans up stale Favorite/Recent entries.
     */
    suspend fun emptyTrash(): OperationResult = withContext(Dispatchers.IO) {
        val trashItems = dao.observeTrashFiles().firstOrNull() ?: emptyList()
        var deletedCount = 0
        for (item in trashItems) {
            val target = File(item.trashPath)
            if (!target.exists() || deleteRecursivelyInternal(target)) {
                deletedCount++
            }
        }
        dao.clearTrash()
        OperationResult.Success("Emptied Recycle Bin", affectedCount = deletedCount)
    }

    suspend fun permanentlyDeleteTrash(items: List<TrashEntity>): OperationResult = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for (item in items) {
            val target = File(item.trashPath)
            if (!target.exists() || deleteRecursivelyInternal(target)) {
                dao.deleteTrashItem(item)
                deletedCount++
            }
        }
        OperationResult.Success("Deleted $deletedCount item(s) permanently", affectedCount = deletedCount)
    }

    suspend fun cleanupExpiredTrash() = withContext(Dispatchers.IO) {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val threshold = System.currentTimeMillis() - thirtyDaysMs
        val expired = dao.getExpiredTrashItems(threshold)
        permanentlyDeleteTrash(expired)
    }

    suspend fun restoreFromTrash(items: List<TrashEntity>, onProgress: (suspend (FileOperationProgress) -> Unit)? = null): OperationResult = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val total = items.size.coerceAtLeast(1)

        for ((index, item) in items.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(
                FileOperationProgress("Restoring items…", item.originalName, index, total)
            )
            val trashFile = File(item.trashPath)
            if (!trashFile.exists()) {
                dao.deleteTrashItem(item)
                continue
            }
            val originalParent = File(item.originalParentPath)
            if (!originalParent.exists()) {
                originalParent.mkdirs()
            }
            val target = File(item.originalPath)
            // Rename back to original, handle conflict if original exists by creating a new name
            var restoredFile = target
            var suffix = 1
            while (restoredFile.exists()) {
                val dotIndex = item.originalName.lastIndexOf('.')
                val name = if (dotIndex != -1 && !item.isDirectory) item.originalName.substring(0, dotIndex) else item.originalName
                val ext = if (dotIndex != -1 && !item.isDirectory) item.originalName.substring(dotIndex) else ""
                restoredFile = File(originalParent, "${name}_$suffix$ext")
                suffix++
            }

            if (trashFile.renameTo(restoredFile) || copyRecursivelyCancellable(trashFile, restoredFile)) {
                if (trashFile.exists()) deleteRecursivelyInternal(trashFile)
                dao.deleteTrashItem(item)
                restoredCount++
                triggerMediaScan(listOf(restoredFile.absolutePath))
            }
        }
        invalidateIndexCache()
        OperationResult.Success("Restored $restoredCount item(s)", affectedCount = restoredCount)
    }

    suspend fun deleteFiles(
        items: List<FileItem>,
        permanently: Boolean = false,
        onProgress: (suspend (FileOperationProgress) -> Unit)? = null
    ): OperationResult = withContext(Dispatchers.IO) {
        var deletedCount = 0
        val deletedPaths = mutableListOf<String>()
        val total = items.size.coerceAtLeast(1)
        val trashRoot = getTrashRoot()

        for ((index, item) in items.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(
                FileOperationProgress(
                    operationTitle = if (permanently) "Deleting permanently…" else "Moving to Recycle Bin…",
                    currentFileName = item.name,
                    processedCount = index,
                    totalCount = total
                )
            )
            val target = File(item.path)
            if (!target.exists()) {
                deletedPaths.add(item.path)
                continue
            }
            
            if (permanently) {
                if (deleteRecursivelyInternal(target) && !target.exists()) {
                    deletedCount++
                    deletedPaths.add(item.path)
                }
            } else {
                val uuid = UUID.randomUUID().toString()
                val trashDest = File(trashRoot, uuid)
                val size = if (item.isDirectory) computeDirectorySizeBounded(target) else target.length()
                
                val moved = if (target.renameTo(trashDest)) {
                    true
                } else {
                    copyRecursivelyCancellable(target, trashDest) && deleteRecursivelyInternal(target)
                }

                if (moved) {
                    deletedCount++
                    deletedPaths.add(item.path)
                    dao.insertTrashItem(
                        TrashEntity(
                            originalPath = item.path,
                            trashPath = trashDest.absolutePath,
                            originalName = item.name,
                            originalParentPath = target.parentFile?.absolutePath ?: getPrimaryStorageRoot().absolutePath,
                            fileSizeBytes = size,
                            deletedAtMillis = System.currentTimeMillis(),
                            isDirectory = item.isDirectory
                        )
                    )
                }
            }
        }

        if (deletedPaths.isNotEmpty()) {
            dao.removeFavorites(deletedPaths)
            dao.removePinnedItems(deletedPaths)
            deletedPaths.forEach { dao.removeRecentOpened(it) }
            triggerMediaScan(deletedPaths)
            invalidateIndexCache()
        }

        if (deletedCount == 0 && items.isNotEmpty()) {
            return@withContext OperationResult.Error("Unable to delete the selected item(s). Check storage permissions.")
        }

        val summary = if (permanently) "Deleted $deletedCount item(s) permanently" else "Moved $deletedCount item(s) to Recycle Bin"
        dao.insertOperationLog(
            OperationHistoryEntity(
                actionType = if (permanently) "DELETE_PERMANENT" else "TRASH",
                summary = summary,
                targetPath = items.firstOrNull()?.path ?: "",
                timestampMillis = System.currentTimeMillis(),
                succeeded = true
            )
        )

        OperationResult.Success(summary, affectedCount = deletedCount)
    }

    private fun deleteRecursivelyInternal(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursivelyInternal(child)
            }
        }
        return file.delete()
    }

    /**
     * Creates a new directory inside [parentDir].
     */
    suspend fun createFolder(parentDir: File, folderName: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            return@withContext OperationResult.Error("Please enter a valid folder name.")
        }
        val target = File(parentDir, trimmed)
        if (target.exists()) {
            return@withContext OperationResult.Error("A folder or file named '$trimmed' already exists.")
        }
        if (!target.mkdirs() || !target.isDirectory) {
            return@withContext OperationResult.Error("Could not create folder '$trimmed'. Check storage permissions.")
        }
        invalidateIndexCache()
        dao.insertOperationLog(
            OperationHistoryEntity(
                actionType = "CREATE_FOLDER",
                summary = "Created folder '$trimmed' in ${parentDir.name}",
                targetPath = target.absolutePath,
                timestampMillis = System.currentTimeMillis(),
                succeeded = true
            )
        )
        OperationResult.Success("Created folder '$trimmed'", affectedCount = 1)
    }

    /**
     * Toggles pinned status for a file or folder.
     */
    suspend fun togglePinned(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        val currentPinned = dao.getPinnedPaths().toSet()
        val nowPinned = if (item.path in currentPinned) {
            dao.removePinnedItem(item.path)
            false
        } else {
            dao.upsertPinnedItem(
                PinnedItemEntity(
                    path = item.path,
                    isDirectory = item.isDirectory,
                    pinnedAtMillis = System.currentTimeMillis()
                )
            )
            true
        }
        
        // Invalidate index so changes reflect quickly
        invalidateIndexCache()
        nowPinned
    }

    /**
     * Toggles favorite status for a file or folder.
     * Removing a favorite strictly removes the database bookmark—never the underlying file.
     */
    suspend fun toggleFavorite(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        val currentFavs = dao.getAllFavoritePaths().toSet()
        val nowFavorited = if (item.path in currentFavs) {
            dao.removeFavorite(item.path)
            false
        } else {
            dao.upsertFavorite(
                FavoriteEntity(
                    path = item.path,
                    name = item.name,
                    isDirectory = item.isDirectory,
                    addedAtMillis = System.currentTimeMillis()
                )
            )
            true
        }
        invalidateIndexCache()
        nowFavorited
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        dao.removeFavorite(path)
        invalidateIndexCache()
    }

    /**
     * Records that a file was opened by the user so `Recently Opened` and `Last Opened` timestamps stay accurate.
     */
    suspend fun recordFileOpened(item: FileItem) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existingPlayback = dao.getPlaybackPosition(item.path) ?: 0L
        dao.upsertRecentOpened(
            RecentOpenedEntity(
                path = item.path,
                name = item.name,
                categoryName = item.category.name,
                sizeBytes = item.sizeBytes,
                openedAtMillis = now,
                lastPlaybackPositionMs = existingPlayback
            )
        )
        dao.updateLastOpened(item.path, now)
    }

    suspend fun savePlaybackPosition(path: String, positionMs: Long) = withContext(Dispatchers.IO) {
        dao.updatePlaybackPosition(path, positionMs)
    }

    suspend fun getSavedPlaybackPosition(path: String): Long = withContext(Dispatchers.IO) {
        dao.getPlaybackPosition(path) ?: 0L
    }

    suspend fun recordSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            dao.upsertRecentSearch(RecentSearchEntity(query = trimmed, searchedAtMillis = System.currentTimeMillis()))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearRecentSearches()
    }

    suspend fun clearOperationHistory() = withContext(Dispatchers.IO) {
        dao.clearOperationHistory()
    }

    /**
     * Builds a safe Share Intent using [FileProvider] for one or multiple files.
     */
    fun buildShareIntent(items: List<FileItem>): Intent? {
        val files = items.filter { !it.isDirectory }.map { File(it.path) }.filter { it.exists() }
        if (files.isEmpty()) return null

        val uris = ArrayList<Uri>(files.size)
        files.forEach { file ->
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return null

        return if (uris.size == 1) {
            val mime = items.first().mimeType.ifEmpty { "*/*" }
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /**
     * Builds an Android system `Open With` intent for external format handling.
     */
    fun buildOpenWithIntent(item: FileItem): Intent? {
        val file = File(item.path)
        if (!file.exists() || file.isDirectory) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Builds Android's official user-confirmed package installation flow for an APK file.
     * Never silently installs packages.
     */
    fun buildApkInstallIntent(item: FileItem): Intent? {
        val file = File(item.path)
        if (!file.exists()) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun computeDirectorySizeBounded(dir: File, maxDepth: Int = 5): Long {
        var total = 0L
        fun walk(current: File, depth: Int) {
            if (depth > maxDepth) return
            val list = current.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) walk(f, depth + 1) else total += f.length()
            }
        }
        walk(dir, 0)
        return total
    }

    private fun triggerMediaScan(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                paths.toTypedArray(),
                null,
                null
            )
        }
    }

    suspend fun createNewFolder(target: File): OperationResult = createFolder(target.parentFile ?: target, target.name)
    suspend fun renameItem(source: File, target: File): OperationResult {
        val dummyItem = FileItem(source.absolutePath, source.name, source.absolutePath, null, source.isDirectory, source.length(), source.extension, "", FileCategory.OTHER, FileTimestamps(0L, TimestampKind.LAST_MODIFIED_FALLBACK, null, 0L))
        return renameFile(dummyItem, target.name)
    }
    suspend fun deleteItems(items: List<FileItem>): OperationResult = deleteFiles(items)
    suspend fun getSavedVideoPlaybackPosition(path: String): Long = getSavedPlaybackPosition(path)
    suspend fun recordMediaOpened(item: FileItem) = recordFileOpened(item)

    suspend fun searchFiles(
        query: String,
        scopeDirectory: File?,
        categories: Set<FileCategory>,
        largeFilesOnly: Boolean,
        showHidden: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val allFiles = getOrBuildIncrementalIndex(showHidden = showHidden)
        val scopedFiles = if (scopeDirectory != null) {
            allFiles.filter { it.path.startsWith(scopeDirectory.absolutePath) }
        } else {
            allFiles
        }
        FileSorterAndFilter.filterFiles(
            items = scopedFiles,
            query = query,
            selectedCategories = categories,
            largeFilesOnly = largeFilesOnly
        )
    }
}
