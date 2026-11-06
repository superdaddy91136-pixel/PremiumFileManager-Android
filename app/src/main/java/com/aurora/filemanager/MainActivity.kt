package com.aurora.filemanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.ui.components.ConfirmDeleteDialog
import com.aurora.filemanager.ui.components.ConflictResolutionDialog
import com.aurora.filemanager.ui.components.FileContextualMenuSheet
import com.aurora.filemanager.ui.components.FileDetailsBottomSheet
import com.aurora.filemanager.ui.components.OperationProgressDialog
import com.aurora.filemanager.ui.components.SingleInputGlassDialog
import com.aurora.filemanager.ui.components.ViewAndSortBottomSheet
import com.aurora.filemanager.ui.screens.analysis.StorageAnalysisScreen
import com.aurora.filemanager.ui.screens.archive.ZipBrowserScreen
import com.aurora.filemanager.ui.screens.home.HomeScreen
import com.aurora.filemanager.ui.screens.hub.QuickHubBottomSheet
import com.aurora.filemanager.ui.screens.trash.RecycleBinSheet
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.aurora.filemanager.ui.screens.viewers.AudioPlayerScreen
import com.aurora.filemanager.ui.screens.viewers.ImageViewerScreen
import com.aurora.filemanager.ui.screens.viewers.PdfViewerScreen
import com.aurora.filemanager.ui.screens.viewers.TextViewerScreen
import com.aurora.filemanager.ui.screens.viewers.VideoPlayerScreen
import com.aurora.filemanager.ui.theme.AuroraFileManagerTheme
import com.aurora.filemanager.ui.viewmodel.ActiveViewerRoute
import com.aurora.filemanager.ui.viewmodel.FileManagerViewModel
import com.aurora.filemanager.ui.viewmodel.HomeSection
import com.aurora.filemanager.util.StoragePermissionHelper
import java.io.File

data class ContextMenuTarget(
    val item: FileItem,
    val anchorRect: Rect? = null
)

class MainActivity : ComponentActivity() {

    private val viewModel: FileManagerViewModel by viewModels { FileManagerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences by viewModel.preferencesFlow.collectAsState()
            AuroraFileManagerTheme(themeMode = preferences.themeMode) {
                AuroraFileManagerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AuroraFileManagerApp(viewModel: FileManagerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val preferences by viewModel.preferencesFlow.collectAsState()
    val favorites by viewModel.favoritesFlow.collectAsState()
    val recentOpened by viewModel.recentOpenedFlow.collectAsState()
    val recentSearches by viewModel.recentSearchesFlow.collectAsState()
    val operationHistory by viewModel.operationHistoryFlow.collectAsState()

    var accessStatus by remember {
        mutableStateOf(StoragePermissionHelper.checkAccessStatus(context))
    }

    // Modal & Dialog States
    var showViewSortSheet by remember { mutableStateOf(false) }
    var showQuickHubSheet by remember { mutableStateOf(false) }
    var showRecycleBinSheet by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var zipCreationTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var deleteConfirmationTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var isPermanentDeleteRequest by remember { mutableStateOf(false) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val updated = StoragePermissionHelper.checkAccessStatus(context)
        accessStatus = updated
        viewModel.initializeAfterPermissionCheck(updated.canReadAnySharedFiles)
    }

    val manageAllFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val updated = StoragePermissionHelper.checkAccessStatus(context)
        accessStatus = updated
        viewModel.initializeAfterPermissionCheck(updated.canReadAnySharedFiles)
    }

    // Re-check permissions when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = StoragePermissionHelper.checkAccessStatus(context)
                val changed = updated != accessStatus
                accessStatus = updated
                if (changed || uiState.recentsRawItems.isEmpty()) {
                    viewModel.initializeAfterPermissionCheck(updated.canReadAnySharedFiles)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Toast event collector
    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Predictable System Back Navigation Handler
    BackHandler(
        enabled = uiState.activeViewer !is ActiveViewerRoute.None ||
            uiState.isSelectionMode ||
            uiState.isSearchActive ||
            (uiState.currentSection == HomeSection.STORAGE && uiState.directoryStack.size > 1)
    ) {
        when {
            uiState.activeViewer !is ActiveViewerRoute.None -> viewModel.closeActiveViewer()
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.isSearchActive -> viewModel.setSearchActive(false)
            uiState.currentSection == HomeSection.STORAGE && uiState.directoryStack.size > 1 -> {
                viewModel.navigateUpInStorage()
            }
        }
    }

    fun launchSafeExternalIntent(intent: Intent?, errorFallback: String) {
        if (intent == null) {
            Toast.makeText(context, errorFallback, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        }.onFailure {
            Toast.makeText(context, errorFallback, Toast.LENGTH_SHORT).show()
        }
    }

    val isAnyDialogOrSheetOpen = contextMenuTarget != null ||
        detailsTarget != null ||
        renameTarget != null ||
        deleteConfirmationTargets != null ||
        showViewSortSheet ||
        showQuickHubSheet ||
        showRecycleBinSheet ||
        zipCreationTargets != null ||
        showCreateFolderDialog

    val currentPlayingPath by viewModel.currentPlayingPath.collectAsState()
    val isAudioPlaying by viewModel.isPlaying.collectAsState()

    // Top-level Crossfade between Home Screen and Built-in Full-Screen Viewers
    // (Home Screen state is preserved inside ViewModel & LazyListState)
    Crossfade(targetState = uiState.activeViewer, label = "viewerTransition") { route ->
        when (route) {
            is ActiveViewerRoute.None -> {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    HomeScreen(
                        uiState = uiState,
                        preferences = preferences,
                        accessStatus = accessStatus,
                        recentSearches = recentSearches,
                        currentPlayingPath = currentPlayingPath,
                        isAudioPlaying = isAudioPlaying,
                        isMenuOpen = isAnyDialogOrSheetOpen,
                        recentsListState = viewModel.recentsListState,
                        recentsGridState = viewModel.recentsGridState,
                        storageListState = viewModel.getStorageListState(uiState.currentStorageDir.absolutePath),
                        storageGridState = viewModel.getStorageGridState(uiState.currentStorageDir.absolutePath),
                        onRequestFullStoragePermission = {
                            runCatching {
                                manageAllFilesLauncher.launch(StoragePermissionHelper.buildManageAllFilesIntent(context))
                            }.onFailure {
                                runtimePermissionLauncher.launch(StoragePermissionHelper.runtimePermissionsToRequest())
                            }
                        },
                        onRequestMediaPermission = {
                            runtimePermissionLauncher.launch(StoragePermissionHelper.runtimePermissionsToRequest())
                        },
                        onSelectSection = viewModel::selectHomeSection,
                        onSelectRecentsCategory = viewModel::setRecentsCategoryFilter,
                        onRefreshRecents = { viewModel.refreshRecents(forceRefresh = true) },
                        onRefreshStorage = { viewModel.loadDirectory(forceRefresh = true) },
                        onNavigateToDirectory = { dir -> viewModel.navigateToDirectory(dir, pushToStack = true) },
                        onOpenCreateFolderDialog = { showCreateFolderDialog = true },
                        onOpenViewSortSheet = { showViewSortSheet = true },
                        onOpenQuickHubSheet = { showQuickHubSheet = true },
                        onOpenRecycleBinSheet = { showRecycleBinSheet = true },
                        onOpenStorageAnalysis = viewModel::openStorageAnalysisScreen,
                        onViewModeChange = viewModel::setViewMode,
                        onGridColumnsChange = viewModel::setGridColumns,
                        onSortChange = viewModel::setSort,
                        onShowExtensionsChange = viewModel::setShowFileExtensions,
                        onCleanerLookChange = viewModel::setCleanerLook,
                        onThemeModeChange = viewModel::setThemeMode,
                    onActivateSearch = viewModel::setSearchActive,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onToggleSearchCurrentFolder = viewModel::toggleSearchScopeCurrentFolder,
                    onToggleSearchCategory = viewModel::toggleSearchCategoryFilter,
                    onToggleSearchLargeFiles = viewModel::toggleSearchLargeFilesOnly,
                    onClearSearchHistory = viewModel::clearSearchHistory,
                    onFileClick = { item, siblings ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(item)
                        } else if (item.isDirectory) {
                            viewModel.navigateToDirectory(File(item.path), pushToStack = true)
                        } else {
                            viewModel.openFileItem(
                                item = item,
                                contextSiblingList = siblings,
                                onExternalOpenFallback = { target ->
                                    launchSafeExternalIntent(
                                        viewModel.buildOpenWithIntent(target),
                                        "No installed app found to open '${target.name}'"
                                    )
                                }
                            )
                        }
                    },
                    onFileLongClick = { item -> viewModel.toggleSelection(item) },
                    onFileMoreClick = { item, rect -> contextMenuTarget = ContextMenuTarget(item, rect) },
                    onSelectAll = viewModel::selectAllVisible,
                    onClearSelection = viewModel::clearSelection,
                    onBatchCopy = {
                        viewModel.stageToClipboard(viewModel.getSelectedFileItems(), isMove = false)
                    },
                    onBatchCut = {
                        viewModel.stageToClipboard(viewModel.getSelectedFileItems(), isMove = true)
                    },
                    onBatchShare = {
                        val selected = viewModel.getSelectedFileItems()
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(selected),
                            "Select at least one regular file to share"
                        )
                    },
                    onBatchCompressZip = {
                        val selected = viewModel.getSelectedFileItems()
                        if (selected.isNotEmpty()) {
                            zipCreationTargets = selected
                        }
                    },
                    onBatchDelete = {
                        val selected = viewModel.getSelectedFileItems()
                        if (selected.isNotEmpty()) {
                            isPermanentDeleteRequest = false
                            deleteConfirmationTargets = selected
                        }
                    },
                    onPasteHere = viewModel::initiatePasteToCurrentDirectory,
                    onClearClipboard = viewModel::clearClipboard
                )
            }
            }

            is ActiveViewerRoute.ImageViewer -> {
                ImageViewerScreen(
                    images = route.images,
                    initialIndex = route.initialIndex,
                    onBack = viewModel::closeActiveViewer,
                    onShare = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(listOf(item)),
                            "Unable to share image"
                        )
                    },
                    onCopy = { item -> viewModel.stageToClipboard(listOf(item), isMove = false) },
                    onMove = { item -> viewModel.stageToClipboard(listOf(item), isMove = true) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = { item -> 
                        isPermanentDeleteRequest = false
                        deleteConfirmationTargets = listOf(item) 
                    },
                    onShowDetails = { item -> detailsTarget = item },
                    onShowMore = { item -> contextMenuTarget = ContextMenuTarget(item, null) },
                    onImageShown = viewModel::recordMediaOpened
                )
            }

            is ActiveViewerRoute.VideoPlayer -> {
                VideoPlayerScreen(
                    item = route.item,
                    initialPositionMs = route.savedPositionMs,
                    resumePlaybackEnabled = preferences.resumeMediaPlayback,
                    onSavePosition = { posMs ->
                        viewModel.saveVideoPlaybackPosition(route.item.path, posMs)
                    },
                    onBack = viewModel::closeActiveViewer,
                    onShowDetails = { item -> detailsTarget = item },
                    onShare = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(listOf(item)),
                            "Unable to share video"
                        )
                    }
                )
            }

            is ActiveViewerRoute.AudioPlayer -> {
                val player by viewModel.mediaController.collectAsState()
                AudioPlayerScreen(
                    queue = route.queue,
                    initialIndex = route.initialIndex,
                    player = player,
                    backgroundPlaybackEnabled = preferences.backgroundAudioPlayback,
                    onToggleBackgroundPlayback = viewModel::setBackgroundAudioPlayback,
                    onTrackShown = viewModel::recordMediaOpened,
                    onShare = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(listOf(item)),
                            "Unable to share audio"
                        )
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = { item -> 
                        isPermanentDeleteRequest = false
                        deleteConfirmationTargets = listOf(item) 
                    },
                    onCopy = { item -> viewModel.stageToClipboard(listOf(item), isMove = false) },
                    onMove = { item -> viewModel.stageToClipboard(listOf(item), isMove = true) },
                    onShowDetails = { item -> detailsTarget = item },
                    onOpenWithExternal = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildOpenWithIntent(item),
                            "No installed app found"
                        )
                    },
                    onBack = viewModel::closeActiveViewer
                )
            }

            is ActiveViewerRoute.PdfViewer -> {
                PdfViewerScreen(
                    item = route.item,
                    onShare = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(listOf(item)),
                            "Unable to share PDF"
                        )
                    },
                    onBack = viewModel::closeActiveViewer
                )
            }

            is ActiveViewerRoute.TextViewer -> {
                TextViewerScreen(
                    item = route.item,
                    onShare = { item ->
                        launchSafeExternalIntent(
                            viewModel.buildShareIntent(listOf(item)),
                            "Unable to share text file"
                        )
                    },
                    onBack = viewModel::closeActiveViewer
                )
            }

            is ActiveViewerRoute.ZipBrowser -> {
                ZipBrowserScreen(
                    zipItem = route.item,
                    onExtractEntries = { selectedEntries ->
                        viewModel.extractZipArchive(route.item, selectedEntries)
                    },
                    onBack = viewModel::closeActiveViewer
                )
            }

            is ActiveViewerRoute.StorageAnalysis -> {
                StorageAnalysisScreen(
                    report = uiState.storageAnalysisReport,
                    isScanning = uiState.isAnalyzingStorage,
                    onRefreshScan = viewModel::runStorageAnalysis,
                    onOpenFile = { item ->
                        viewModel.openFileItem(
                            item = item,
                            contextSiblingList = listOf(item),
                            onExternalOpenFallback = { target ->
                                launchSafeExternalIntent(
                                    viewModel.buildOpenWithIntent(target),
                                    "Cannot open '${target.name}'"
                                )
                            }
                        )
                    },
                    onOpenDirectoryPath = { path ->
                        viewModel.closeActiveViewer()
                        viewModel.navigateToDirectory(File(path), pushToStack = true)
                    },
                    onRequestDeleteFile = { item ->
                        isPermanentDeleteRequest = false
                        deleteConfirmationTargets = listOf(item)
                    },
                    onBack = viewModel::closeActiveViewer
                )
            }
        }
    }

    // =========================================================================
    // BOTTOM SHEETS & DIALOGS
    // =========================================================================

    if (showViewSortSheet) {
        val isRecents = uiState.currentSection == HomeSection.RECENTS
        ViewAndSortBottomSheet(
            isRecentsSection = isRecents,
            currentViewMode = if (isRecents) preferences.recentsViewMode else (preferences.folderViewModes[uiState.currentStorageDir.absolutePath] ?: ViewMode.LIST),
            gridColumns = preferences.gridColumns,
            currentSortField = if (isRecents) preferences.recentsSortField else preferences.storageSortField,
            currentSortOrder = if (isRecents) preferences.recentsSortOrder else preferences.storageSortOrder,
            currentGrouping = if (isRecents) preferences.recentsGrouping else preferences.storageGrouping,
            showFileExtensions = preferences.showFileExtensions,
            showHiddenFiles = preferences.showHiddenFiles,
            cleanerLook = preferences.cleanerLook,
            currentThemeMode = preferences.themeMode,
            onViewModeChange = viewModel::setViewMode,
            onGridColumnsChange = viewModel::setGridColumns,
            onSortChange = viewModel::setSort,
            onGroupingChange = viewModel::setGrouping,
            onShowExtensionsChange = viewModel::setShowFileExtensions,
            onShowHiddenChange = viewModel::setShowHiddenFiles,
            onCleanerLookChange = viewModel::setCleanerLook,
            onThemeModeChange = viewModel::setThemeMode,
            onDismiss = { showViewSortSheet = false }
        )
    }

    if (showQuickHubSheet) {
        QuickHubBottomSheet(
            favorites = favorites,
            recentOpened = recentOpened,
            operationHistory = operationHistory,
            onOpenPath = { path, isDir ->
                val f = File(path)
                if (isDir && f.exists()) {
                    viewModel.navigateToDirectory(f, pushToStack = true)
                } else if (f.exists()) {
                    val mapped = (uiState.recentsRawItems + uiState.storageRawItems)
                        .firstOrNull { it.path == path }
                    if (mapped != null) {
                        viewModel.openFileItem(mapped, listOf(mapped)) { target ->
                            launchSafeExternalIntent(
                                viewModel.buildOpenWithIntent(target),
                                "Cannot open '${target.name}'"
                            )
                        }
                    } else {
                        val parent = f.parentFile
                        if (parent != null) viewModel.navigateToDirectory(parent, pushToStack = true)
                    }
                }
            },
            onRemoveFavorite = viewModel::removeFavoriteByPath,
            onClearHistory = viewModel::clearOperationHistory,
            onDismiss = { showQuickHubSheet = false }
        )
    }

    contextMenuTarget?.let { target ->
        val targetItem = target.item
        FileContextualMenuSheet(
            item = targetItem,
            anchorRect = target.anchorRect,
            onOpen = {
                viewModel.openFileItem(
                    item = targetItem,
                    contextSiblingList = uiState.storageRawItems.ifEmpty { uiState.recentsRawItems },
                    onExternalOpenFallback = { extTarget ->
                        launchSafeExternalIntent(
                            viewModel.buildOpenWithIntent(extTarget),
                            "No app available to open '${extTarget.name}'"
                        )
                    }
                )
            },
            onOpenWithExternal = {
                launchSafeExternalIntent(
                    viewModel.buildOpenWithIntent(targetItem),
                    "No external app found for '${targetItem.name}'"
                )
            },
            onInspectArchive = if (targetItem.extension.equals("zip", ignoreCase = true)) {
                { viewModel.openFileItem(targetItem, listOf(targetItem)) {} }
            } else null,
            onCompressToZip = { zipCreationTargets = listOf(targetItem) },
            onInstallApk = if (targetItem.extension.equals("apk", ignoreCase = true)) {
                {
                    val installIntent = viewModel.buildApkInstallIntent(targetItem)
                    if (installIntent != null) {
                        runCatching { context.startActivity(installIntent) }
                            .onFailure {
                                Toast.makeText(context, "Unable to launch package installer", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else null,
            onToggleFavorite = { viewModel.toggleFavorite(targetItem) },
            onTogglePinned = { viewModel.togglePinned(targetItem) },
            onShare = {
                launchSafeExternalIntent(
                    viewModel.buildShareIntent(listOf(targetItem)),
                    "Unable to share '${targetItem.name}'"
                )
            },
            onRename = { renameTarget = targetItem },
            onCopy = { viewModel.stageToClipboard(listOf(targetItem), isMove = false) },
            onCutMove = { viewModel.stageToClipboard(listOf(targetItem), isMove = true) },
            onOpenContainingFolder = if (uiState.currentSection == HomeSection.RECENTS || uiState.isSearchActive) {
                {
                    val parent = File(targetItem.path).parentFile
                    if (parent != null && parent.exists()) {
                        viewModel.setSearchActive(false)
                        viewModel.navigateToDirectory(parent, pushToStack = true)
                    }
                }
            } else null,
            onViewDetails = { detailsTarget = targetItem },
            onDelete = { 
                isPermanentDeleteRequest = false
                deleteConfirmationTargets = listOf(targetItem) 
            },
            onDeletePermanently = {
                isPermanentDeleteRequest = true
                deleteConfirmationTargets = listOf(targetItem)
            },
            onDismiss = { contextMenuTarget = null }
        )
    }

    detailsTarget?.let { target ->
        FileDetailsBottomSheet(
            item = target,
            onRename = { renameTarget = target },
            onShare = {
                launchSafeExternalIntent(
                    viewModel.buildShareIntent(listOf(target)),
                    "Unable to share '${target.name}'"
                )
            },
            onOpenContainingFolder = {
                val parent = File(target.path).parentFile
                if (parent != null && parent.exists()) {
                    viewModel.closeActiveViewer()
                    viewModel.setSearchActive(false)
                    viewModel.navigateToDirectory(parent, pushToStack = true)
                }
            },
            onDismiss = { detailsTarget = null }
        )
    }

    renameTarget?.let { target ->
        SingleInputGlassDialog(
            title = "Rename",
            initialValue = target.name,
            placeholder = "Enter new name",
            confirmLabel = "Rename",
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameItem(target, newName)
            },
            onDismiss = { renameTarget = null }
        )
    }

    if (showCreateFolderDialog) {
        SingleInputGlassDialog(
            title = "Create New Folder",
            initialValue = "",
            placeholder = "Folder name",
            confirmLabel = "Create",
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                viewModel.createNewFolder(folderName)
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    zipCreationTargets?.let { targets ->
        val defaultName = if (targets.size == 1) {
            "${targets.first().displayNameWithoutExtension}.zip"
        } else {
            "Archive_${targets.size}_items.zip"
        }
        SingleInputGlassDialog(
            title = "Compress to ZIP Archive",
            initialValue = defaultName,
            placeholder = "Archive filename (.zip)",
            confirmLabel = "Create ZIP",
            onConfirm = { archiveName ->
                zipCreationTargets = null
                viewModel.compressItemsToZip(targets, archiveName)
            },
            onDismiss = { zipCreationTargets = null }
        )
    }

    if (showRecycleBinSheet) {
        val trashItems by viewModel.trashItemsFlow.collectAsState()
        val trashViewMode = preferences.folderViewModes["trash"] ?: ViewMode.LIST
        RecycleBinSheet(
            trashItems = trashItems,
            viewMode = trashViewMode,
            gridColumns = preferences.gridColumns,
            showExtension = preferences.showFileExtensions,
            enableThumbnails = preferences.enableThumbnails,
            cleanerLook = preferences.cleanerLook,
            onViewModeChange = viewModel::setTrashViewMode,
            onRestore = { items -> viewModel.restoreTrashItems(items) },
            onDeletePermanently = { items -> viewModel.permanentlyDeleteTrashItems(items) },
            onEmptyTrash = { viewModel.emptyTrash() },
            onDismiss = { showRecycleBinSheet = false }
        )
    }

    deleteConfirmationTargets?.let { targets ->
        val isOversized = targets.sumOf { if (it.isDirectory) viewModel.computeSize(it) else it.sizeBytes } > 1024L * 1024 * 1024 // 1GB
        ConfirmDeleteDialog(
            items = targets,
            isPermanent = isPermanentDeleteRequest,
            isOversized = isOversized,
            onConfirmDelete = { permanently ->
                val toDelete = targets
                deleteConfirmationTargets = null
                if (uiState.activeViewer is ActiveViewerRoute.ImageViewer) {
                    viewModel.closeActiveViewer()
                }
                viewModel.deleteItems(toDelete, permanently)
            },
            onDismiss = { deleteConfirmationTargets = null }
        )
    }

    uiState.pendingConflict?.let { conflict ->
        ConflictResolutionDialog(
            conflictingNames = conflict.conflictingNames,
            onSelectStrategy = viewModel::resolvePendingConflict,
            onDismiss = viewModel::dismissPendingConflict
        )
    }

    uiState.operationProgress?.let { progress ->
        OperationProgressDialog(
            progress = progress,
            onCancel = viewModel::cancelActiveOperation
        )
    }
}
