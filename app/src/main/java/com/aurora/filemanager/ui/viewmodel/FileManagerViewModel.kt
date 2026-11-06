package com.aurora.filemanager.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aurora.filemanager.AuroraApplication
import com.aurora.filemanager.data.local.FavoriteEntity
import com.aurora.filemanager.data.local.OperationHistoryEntity
import com.aurora.filemanager.data.local.RecentOpenedEntity
import com.aurora.filemanager.data.local.TrashEntity
import com.aurora.filemanager.data.local.RecentSearchEntity
import android.content.Intent
import com.aurora.filemanager.data.model.ConflictStrategy
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileClipboard
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.FileOperationProgress
import com.aurora.filemanager.data.model.GroupingMode
import com.aurora.filemanager.data.model.OperationResult
import com.aurora.filemanager.data.model.SortField
import com.aurora.filemanager.data.model.SortOrder
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.util.StorageAnalysisReport
import com.aurora.filemanager.util.ZipManager
import com.aurora.filemanager.data.preferences.UserPreferences
import com.aurora.filemanager.ui.theme.ThemeMode
import com.aurora.filemanager.service.AudioPlaybackService
import com.aurora.filemanager.util.FileSectionGroup
import com.aurora.filemanager.util.FileSorterAndFilter
import com.aurora.filemanager.util.StorageAnalyzer
import com.aurora.filemanager.util.StoragePermissionHelper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.FileObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class HomeSection(val title: String) {
    RECENTS("Recents"),
    STORAGE("Storage")
}

sealed class ActiveViewerRoute {
    data object None : ActiveViewerRoute()
    data class ImageViewer(val images: List<FileItem>, val initialIndex: Int) : ActiveViewerRoute()
    data class VideoPlayer(val item: FileItem, val savedPositionMs: Long) : ActiveViewerRoute()
    data class AudioPlayer(val queue: List<FileItem>, val initialIndex: Int) : ActiveViewerRoute()
    data class PdfViewer(val item: FileItem) : ActiveViewerRoute()
    data class TextViewer(val item: FileItem) : ActiveViewerRoute()
    data class ZipBrowser(val item: FileItem) : ActiveViewerRoute()
    data object StorageAnalysis : ActiveViewerRoute()
}

@Immutable
data class PendingPasteConflict(
    val sourceItems: List<FileItem>,
    val destinationDir: File,
    val isMove: Boolean,
    val conflictingNames: List<String>
)

@Immutable
data class FileManagerUiState(
    val currentSection: HomeSection = HomeSection.RECENTS,
    val isLoadingRecents: Boolean = true,
    val isLoadingStorage: Boolean = true,
    val recentsRawItems: List<FileItem> = emptyList(),
    val recentsGroups: List<FileSectionGroup> = emptyList(),
    val recentsFilterCategory: FileCategory? = null,
    val storageRootPath: String = "",
    val currentStorageDir: File = File("/storage/emulated/0"),
    val directoryStack: List<File> = emptyList(),
    val storageRawItems: List<FileItem> = emptyList(),
    val storageGroups: List<FileSectionGroup> = emptyList(),
    val storageErrorMessage: String? = null,
    val totalStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    // Selection & Clipboard
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: FileClipboard = FileClipboard(emptyList(), isMoveOperation = false),
    // Search State
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchInCurrentFolderOnly: Boolean = false,
    val searchCategoryFilters: Set<FileCategory> = emptySet(),
    val searchLargeFilesOnly: Boolean = false,
    val searchResults: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    // Active Modal / Viewer / Progress State
    val activeViewer: ActiveViewerRoute = ActiveViewerRoute.None,
    val operationProgress: FileOperationProgress? = null,
    val pendingConflict: PendingPasteConflict? = null,
    val storageAnalysisReport: StorageAnalysisReport? = null,
    val isAnalyzingStorage: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return FileManagerViewModel(application) as T
            }
        }
    }

    private val auroraApp = application as AuroraApplication
    private val repository = auroraApp.fileRepository
    private val prefsRepo = auroraApp.preferencesRepository

    val preferencesFlow: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val favoritesFlow: StateFlow<List<FavoriteEntity>> = repository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentOpenedFlow: StateFlow<List<RecentOpenedEntity>> = repository.recentOpenedFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentSearchesFlow: StateFlow<List<RecentSearchEntity>> = repository.recentSearchesFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val operationHistoryFlow: StateFlow<List<OperationHistoryEntity>> = repository.operationHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trashItemsFlow: StateFlow<List<TrashEntity>> = repository.trashItemsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentsListState = LazyListState()
    val recentsGridState = LazyGridState()

    private val storageListStates = mutableMapOf<String, LazyListState>()
    private val storageGridStates = mutableMapOf<String, LazyGridState>()
    private val directoryItemsCache = mutableMapOf<String, List<FileItem>>()

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    fun getStorageListState(path: String): LazyListState {
        return storageListStates.getOrPut(path) { LazyListState() }
    }

    fun getStorageGridState(path: String): LazyGridState {
        return storageGridStates.getOrPut(path) { LazyGridState() }
    }

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            storageRootPath = repository.getPrimaryStorageRoot().absolutePath,
            currentStorageDir = repository.getPrimaryStorageRoot(),
            directoryStack = listOf(repository.getPrimaryStorageRoot()),
            currentSection = runCatching {
                runBlocking { prefsRepo.preferencesFlow.first() }.lastSelectedSection
            }.getOrDefault(HomeSection.RECENTS)
        )
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessages = _toastMessages.asSharedFlow()

    private var searchJob: Job? = null
    private var activeOperationJob: Job? = null

    private var externalChangeRefreshJob: Job? = null
    private val refreshDelayMs = 1500L

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scheduleSilentRefresh()
        }
    }

    private var directoryObserver: FileObserver? = null

    private fun scheduleSilentRefresh() {
        externalChangeRefreshJob?.cancel()
        externalChangeRefreshJob = viewModelScope.launch {
            delay(refreshDelayMs)
            performSilentRefresh()
        }
    }

    private fun performSilentRefresh() {
        loadDirectorySilently(_uiState.value.currentStorageDir)
        refreshRecents(forceRefresh = true, silent = true)
        if (_uiState.value.isSearchActive) {
            triggerDebouncedSearch(immediate = true)
        }
    }
    
    private fun loadDirectorySilently(dir: File) {
        viewModelScope.launch {
            val prefs = preferencesFlow.value
            val result = repository.listDirectory(dir, showHidden = prefs.showHiddenFiles)
            result.onSuccess { items ->
                directoryItemsCache[dir.absolutePath] = items
                if (_uiState.value.currentStorageDir.absolutePath == dir.absolutePath) {
                    _uiState.update {
                        it.copy(
                            storageRawItems = items,
                            storageErrorMessage = null
                        )
                    }
                    recomputeSortedAndGroupedLists(prefs)
                }
            }.onFailure { err ->
                if (_uiState.value.currentStorageDir.absolutePath == dir.absolutePath) {
                    _uiState.update {
                        it.copy(
                            storageErrorMessage = err.localizedMessage ?: "Cannot access folder."
                        )
                    }
                }
            }
        }
    }

    private fun observeCurrentDirectory(dir: File) {
        directoryObserver?.stopWatching()
        try {
            val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.MODIFY
            directoryObserver = object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) {
                        scheduleSilentRefresh()
                    }
                }
            }
            directoryObserver?.startWatching()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        auroraApp.contentResolver.unregisterContentObserver(mediaStoreObserver)
        directoryObserver?.stopWatching()
    }

    init {
        auroraApp.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            mediaStoreObserver
        )

        refreshVolumeStats()
        viewModelScope.launch { repository.cleanupExpiredTrash() }
        viewModelScope.launch {
            preferencesFlow.collect { prefs ->
                recomputeSortedAndGroupedLists(prefs)
            }
        }

        val sessionToken = SessionToken(application, ComponentName(application, AudioPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture.addListener({
            runCatching {
                val controller = controllerFuture.get()
                _mediaController.value = controller
                attachControllerListener(controller)
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun attachControllerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentPlayingPath.value = mediaItem?.mediaId ?: mediaItem?.localConfiguration?.uri?.path
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = state
                val currentItem = controller.currentMediaItem
                _currentPlayingPath.value = currentItem?.mediaId ?: currentItem?.localConfiguration?.uri?.path
            }
        })
        val currentItem = controller.currentMediaItem
        _currentPlayingPath.value = currentItem?.mediaId ?: currentItem?.localConfiguration?.uri?.path
        _isPlaying.value = controller.isPlaying
        _playbackState.value = controller.playbackState
    }

    fun initializeAfterPermissionCheck(hasAnyReadAccess: Boolean) {
        refreshVolumeStats()
        if (hasAnyReadAccess) {
            refreshRecents(forceRefresh = false)
            loadDirectory(_uiState.value.currentStorageDir)
        } else {
            _uiState.update {
                it.copy(
                    isLoadingRecents = false,
                    isLoadingStorage = false
                )
            }
        }
    }

    private fun refreshVolumeStats() {
        val (total, used, avail) = StorageAnalyzer.queryVolumeMetrics(repository.getPrimaryStorageRoot())
        _uiState.update {
            it.copy(
                totalStorageBytes = total,
                usedStorageBytes = used,
                availableStorageBytes = avail
            )
        }
    }

    fun selectHomeSection(section: HomeSection) {
        _uiState.update { it.copy(currentSection = section) }
        viewModelScope.launch { prefsRepo.setLastSelectedSection(section) }
    }

    fun setRecentsCategoryFilter(category: FileCategory?) {
        _uiState.update { state ->
            val nextCat = if (state.recentsFilterCategory == category) null else category
            state.copy(recentsFilterCategory = nextCat)
        }
        recomputeSortedAndGroupedLists(preferencesFlow.value)
    }

    fun refreshRecents(forceRefresh: Boolean = true, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingRecents = true) }
            }
            val prefs = preferencesFlow.value
            val result = repository.loadRecentsFeed(
                showHidden = prefs.showHiddenFiles,
                limit = 250,
                forceRefresh = forceRefresh
            )
            result.onSuccess { items ->
                _uiState.update { it.copy(recentsRawItems = items, isLoadingRecents = false) }
                recomputeSortedAndGroupedLists(prefs)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingRecents = false) }
                if (!silent) {
                    _toastMessages.tryEmit(err.localizedMessage ?: "Unable to load recent files")
                }
            }
            if (!silent) {
                refreshVolumeStats()
            }
        }
    }

    fun navigateToDirectory(targetDir: File, pushToStack: Boolean = true) {
        val rootPath = repository.getPrimaryStorageRoot().absolutePath
        val newStack = if (pushToStack) {
            val currentStack = _uiState.value.directoryStack
            val existingIdx = currentStack.indexOfFirst { it.absolutePath == targetDir.absolutePath }
            if (existingIdx >= 0) {
                currentStack.subList(0, existingIdx + 1)
            } else if (targetDir.absolutePath.startsWith(rootPath)) {
                // Build full breadcrumb trail from storage root down to targetDir
                val relativeSegments = targetDir.absolutePath.removePrefix(rootPath).trim('/').split('/')
                    .filter { it.isNotEmpty() }
                val built = mutableListOf(File(rootPath))
                var cursor = File(rootPath)
                for (seg in relativeSegments) {
                    cursor = File(cursor, seg)
                    built.add(cursor)
                }
                built
            } else {
                currentStack + targetDir
            }
        } else {
            _uiState.value.directoryStack
        }

        _uiState.update {
            it.copy(
                currentSection = HomeSection.STORAGE,
                currentStorageDir = targetDir,
                directoryStack = newStack,
                selectedPaths = emptySet()
            )
        }
        loadDirectory(targetDir)
    }

    /**
     * Handles back navigation inside the Storage folder hierarchy without losing state.
     * Returns true if consumed, or false if already at the storage root.
     */
    fun navigateUpInStorage(): Boolean {
        val stack = _uiState.value.directoryStack
        if (stack.size <= 1) return false
        val newStack = stack.dropLast(1)
        val parentDir = newStack.last()
        _uiState.update {
            it.copy(
                currentStorageDir = parentDir,
                directoryStack = newStack,
                selectedPaths = emptySet()
            )
        }
        loadDirectory(parentDir)
        return true
    }

    fun loadDirectory(dir: File = _uiState.value.currentStorageDir, forceRefresh: Boolean = false) {
        observeCurrentDirectory(dir)
        val cached = if (forceRefresh) null else directoryItemsCache[dir.absolutePath]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    storageRawItems = cached,
                    isLoadingStorage = false,
                    storageErrorMessage = null
                )
            }
            recomputeSortedAndGroupedLists(preferencesFlow.value)
        } else {
            _uiState.update { it.copy(isLoadingStorage = true, storageErrorMessage = null) }
        }

        viewModelScope.launch {
            val prefs = preferencesFlow.value
            val result = repository.listDirectory(dir, showHidden = prefs.showHiddenFiles)
            result.onSuccess { items ->
                directoryItemsCache[dir.absolutePath] = items
                if (_uiState.value.currentStorageDir.absolutePath == dir.absolutePath) {
                    _uiState.update {
                        it.copy(
                            storageRawItems = items,
                            isLoadingStorage = false,
                            storageErrorMessage = null
                        )
                    }
                    recomputeSortedAndGroupedLists(prefs)
                }
            }.onFailure { err ->
                if (_uiState.value.currentStorageDir.absolutePath == dir.absolutePath && cached == null) {
                    _uiState.update {
                        it.copy(
                            storageRawItems = emptyList(),
                            storageGroups = emptyList(),
                            isLoadingStorage = false,
                            storageErrorMessage = err.localizedMessage ?: "Cannot access folder."
                        )
                    }
                }
            }
        }
    }

    private fun recomputeSortedAndGroupedLists(prefs: UserPreferences) {
        _uiState.update { state ->
            // 1. Recents
            val filteredRecents = if (state.recentsFilterCategory != null) {
                state.recentsRawItems.filter { it.category == state.recentsFilterCategory }
            } else {
                state.recentsRawItems
            }
            val sortedRecents = FileSorterAndFilter.sortFiles(
                items = filteredRecents,
                sortField = prefs.recentsSortField,
                sortOrder = prefs.recentsSortOrder,
                directoriesFirst = false
            )
            val groupedRecents = FileSorterAndFilter.groupFiles(
                sortedItems = sortedRecents,
                groupingMode = prefs.recentsGrouping,
                sortField = prefs.recentsSortField
            )

            // 2. Storage
            val sortedStorage = FileSorterAndFilter.sortFiles(
                items = state.storageRawItems,
                sortField = prefs.storageSortField,
                sortOrder = prefs.storageSortOrder,
                directoriesFirst = true
            )
            val groupedStorage = FileSorterAndFilter.groupFiles(
                sortedItems = sortedStorage,
                groupingMode = prefs.storageGrouping,
                sortField = prefs.storageSortField
            )

            state.copy(
                recentsGroups = groupedRecents,
                storageGroups = groupedStorage
            )
        }
    }

    // =========================================================================
    // MULTI-SELECTION & CLIPBOARD
    // =========================================================================

    fun toggleSelection(item: FileItem) {
        _uiState.update { state ->
            val next = if (item.path in state.selectedPaths) {
                state.selectedPaths - item.path
            } else {
                state.selectedPaths + item.path
            }
            state.copy(selectedPaths = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun getSelectedFileItems(): List<FileItem> {
        val state = _uiState.value
        val all = state.storageRawItems + state.recentsRawItems + state.searchResults
        return all.filter { it.path in state.selectedPaths }.distinctBy { it.path }
    }

    fun stageToClipboard(items: List<FileItem>, isMove: Boolean) {
        if (items.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = FileClipboard(items = items, isMoveOperation = isMove),
                selectedPaths = emptySet()
            )
        }
        val verb = if (isMove) "Ready to move" else "Copied"
        _toastMessages.tryEmit("$verb ${items.size} item(s) to clipboard")
    }

    fun clearClipboard() {
        _uiState.update {
            it.copy(clipboard = FileClipboard(emptyList(), isMoveOperation = false))
        }
    }

    fun initiatePasteToCurrentDirectory(conflictStrategy: ConflictStrategy? = null) {
        val cb = _uiState.value.clipboard
        if (cb.items.isEmpty()) return
        val destDir = _uiState.value.currentStorageDir

        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val result = repository.copyOrMoveFiles(
                sourceItems = cb.items,
                destinationDir = destDir,
                isMove = cb.isMoveOperation,
                conflictStrategy = conflictStrategy ?: ConflictStrategy.KEEP_BOTH_RENAME
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }

            _uiState.update { it.copy(operationProgress = null, pendingConflict = null) }
            handleOperationResult(result)
            if (result is OperationResult.Success) {
                clearClipboard()
                refreshAfterMutation()
            } else if (result is OperationResult.ConflictDetected) {
                _uiState.update {
                    it.copy(
                        pendingConflict = PendingPasteConflict(
                            sourceItems = cb.items,
                            destinationDir = destDir,
                            isMove = cb.isMoveOperation,
                            conflictingNames = result.conflictingFileNames
                        )
                    )
                }
            }
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(pendingConflict = null, operationProgress = null) }
    }

    fun dismissPendingConflict() {
        dismissConflict()
    }

    private fun handleOperationResult(res: OperationResult) {
        when (res) {
            is OperationResult.Success -> {
                _toastMessages.tryEmit(res.message)
            }
            is OperationResult.Error -> {
                _toastMessages.tryEmit(res.userMessage)
            }
            is OperationResult.ConflictDetected -> Unit
        }
    }

    private fun refreshAfterMutation() {
        performSilentRefresh()
    }

    // =========================================================================
    // SEARCH ENGINE
    // =========================================================================

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery,
                searchResults = if (!active) emptyList() else it.searchResults
            )
        }
        if (!active) {
            searchJob?.cancel()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        triggerDebouncedSearch(immediate = false)
    }

    fun toggleSearchScopeCurrentFolder(currentOnly: Boolean) {
        _uiState.update { it.copy(searchInCurrentFolderOnly = currentOnly) }
        triggerDebouncedSearch(immediate = true)
    }

    fun toggleSearchCategoryFilter(category: FileCategory) {
        _uiState.update { state ->
            val next = if (category in state.searchCategoryFilters) {
                state.searchCategoryFilters - category
            } else {
                state.searchCategoryFilters + category
            }
            state.copy(searchCategoryFilters = next)
        }
        triggerDebouncedSearch(immediate = true)
    }

    fun toggleSearchLargeFilesOnly(largeOnly: Boolean) {
        _uiState.update { it.copy(searchLargeFilesOnly = largeOnly) }
        triggerDebouncedSearch(immediate = true)
    }

    private fun triggerDebouncedSearch(immediate: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) {
                kotlinx.coroutines.delay(220)
            }
            val state = _uiState.value
            val query = state.searchQuery
            if (query.isBlank() && state.searchCategoryFilters.isEmpty() && !state.searchLargeFilesOnly) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }

            _uiState.update { it.copy(isSearching = true) }
            val prefs = preferencesFlow.value
            val scopeDir = if (state.searchInCurrentFolderOnly) state.currentStorageDir else null

            val results = repository.searchFiles(
                query = query,
                scopeDirectory = scopeDir,
                categories = state.searchCategoryFilters,
                largeFilesOnly = state.searchLargeFilesOnly,
                showHidden = prefs.showHiddenFiles
            )
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false
                )
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _toastMessages.tryEmit("Search history cleared")
        }
    }

    // =========================================================================
    // FILE ACTIONS & VIEWERS
    // =========================================================================

    fun recordFileOpened(item: FileItem) {
        viewModelScope.launch {
            repository.recordFileOpened(item)
        }
    }

    fun recordMediaOpened(item: FileItem) {
        viewModelScope.launch {
            repository.recordMediaOpened(item)
        }
    }

    fun saveVideoPlaybackPosition(path: String, positionMs: Long) {
        viewModelScope.launch {
            repository.savePlaybackPosition(path, positionMs)
        }
    }

    fun openFileItem(
        item: FileItem,
        contextSiblingList: List<FileItem>,
        onExternalOpenFallback: (FileItem) -> Unit
    ) {
        when (item.category) {
            FileCategory.FOLDER -> {
                closeActiveViewer()
                navigateToDirectory(File(item.path), pushToStack = true)
            }
            FileCategory.IMAGE -> {
                val siblingImages = contextSiblingList.filter { it.category == FileCategory.IMAGE }
                    .ifEmpty { listOf(item) }
                val index = siblingImages.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
                _uiState.update {
                    it.copy(activeViewer = ActiveViewerRoute.ImageViewer(siblingImages, index))
                }
            }
            FileCategory.VIDEO -> {
                val savedPos = runBlocking { repository.getSavedVideoPlaybackPosition(item.path) }
                _uiState.update {
                    it.copy(activeViewer = ActiveViewerRoute.VideoPlayer(item, savedPos))
                }
            }
            FileCategory.AUDIO -> {
                val siblingAudio = contextSiblingList.filter { it.category == FileCategory.AUDIO }
                    .ifEmpty { listOf(item) }
                val index = siblingAudio.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
                
                val controller = _mediaController.value
                val currentItemPath = controller?.currentMediaItem?.mediaId ?: controller?.currentMediaItem?.localConfiguration?.uri?.path
                val isSameTrackAlreadyPlaying = currentItemPath != null && File(currentItemPath).absolutePath == File(item.path).absolutePath && controller?.playbackState != Player.STATE_IDLE

                if (!isSameTrackAlreadyPlaying) {
                    if (controller != null) {
                        val mediaItems = siblingAudio.map { track ->
                            MediaItem.Builder()
                                .setMediaId(track.path)
                                .setUri(Uri.fromFile(File(track.path)))
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(track.name)
                                        .build()
                                )
                                .build()
                        }
                        controller.setMediaItems(mediaItems, index, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }

                _uiState.update {
                    it.copy(activeViewer = ActiveViewerRoute.AudioPlayer(siblingAudio, index))
                }
            }
            FileCategory.PDF -> {
                _uiState.update {
                    it.copy(activeViewer = ActiveViewerRoute.PdfViewer(item))
                }
            }
            FileCategory.CODE -> {
                _uiState.update {
                    it.copy(activeViewer = ActiveViewerRoute.TextViewer(item))
                }
            }
            FileCategory.ARCHIVE -> {
                if (item.extension.equals("zip", ignoreCase = true) || item.extension.equals("jar", ignoreCase = true)) {
                    _uiState.update {
                        it.copy(activeViewer = ActiveViewerRoute.ZipBrowser(item))
                    }
                } else {
                    onExternalOpenFallback(item)
                }
            }
            else -> {
                onExternalOpenFallback(item)
            }
        }
    }

    fun closeActiveViewer() {
        if (_uiState.value.activeViewer is ActiveViewerRoute.AudioPlayer) {
            // Keep background playback running when backing out of AudioPlayerScreen.
        }
        _uiState.update { it.copy(activeViewer = ActiveViewerRoute.None) }
    }

    fun openAudioPlayer(queue: List<FileItem>, index: Int) {
        _uiState.update { it.copy(activeViewer = ActiveViewerRoute.AudioPlayer(queue, index)) }
    }

    fun createNewFolder(folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty()) return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val parentDir = _uiState.value.currentStorageDir
            val target = File(parentDir, trimmed)
            val res = repository.createNewFolder(target)
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                refreshAfterMutation()
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val source = File(item.path)
            val target = File(source.parentFile, trimmed)
            val res = repository.renameItem(source, target)
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                refreshAfterMutation()
            }
        }
    }

    fun deleteFileItems(items: List<FileItem>, permanently: Boolean = false) {
        if (items.isEmpty()) return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val res = repository.deleteFiles(items, permanently)
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                clearSelection()
                refreshAfterMutation()
            }
        }
    }

    fun emptyTrash() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val res = repository.emptyTrash()
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                refreshAfterMutation()
            }
        }
    }

    fun permanentlyDeleteTrashItems(items: List<TrashEntity>) {
        if (items.isEmpty()) return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val res = repository.permanentlyDeleteTrash(items)
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                refreshAfterMutation()
            }
        }
    }

    fun restoreTrashItems(items: List<TrashEntity>) {
        if (items.isEmpty()) return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val res = repository.restoreFromTrash(items)
            handleOperationResult(res)
            if (res is OperationResult.Success) {
                refreshAfterMutation()
            }
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            val nowFav = repository.toggleFavorite(item)
            _toastMessages.tryEmit(
                if (nowFav) "Added '${item.name}' to Favorites" else "Removed '${item.name}' from Favorites"
            )
            refreshAfterMutation()
        }
    }

    fun togglePinned(item: FileItem) {
        viewModelScope.launch {
            val nowPinned = repository.togglePinned(item)
            _toastMessages.tryEmit(
                if (nowPinned) "Pinned '${item.name}'" else "Unpinned '${item.name}'"
            )
            refreshAfterMutation()
        }
    }

    fun removeFavoriteByPath(path: String) {
        viewModelScope.launch {
            repository.removeFavorite(path)
        }
    }

    // =========================================================================
    // STORAGE ANALYSIS
    // =========================================================================

    fun openStorageAnalysisScreen() {
        _uiState.update { it.copy(activeViewer = ActiveViewerRoute.StorageAnalysis) }
    }

    // =========================================================================
    // Audio Player Actions
    // =========================================================================

    fun setBackgroundAudioPlayback(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setBackgroundAudioPlayback(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun selectAllVisible() {
        val state = _uiState.value
        val visibleItems = when {
            state.isSearchActive -> state.searchResults
            state.currentSection == HomeSection.RECENTS -> state.recentsRawItems
            else -> state.storageRawItems
        }
        _uiState.update { it.copy(selectedPaths = visibleItems.map { it.path }.toSet()) }
    }

    fun extractZipArchive(zipItem: FileItem, selectedEntries: Set<String>?) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val zipFile = File(zipItem.path)
            val destDir = zipFile.parentFile ?: _uiState.value.currentStorageDir
            val result = ZipManager.extractArchive(
                zipFile = zipFile,
                destinationDir = destDir,
                selectedEntryPaths = selectedEntries,
                conflictStrategy = ConflictStrategy.KEEP_BOTH_RENAME
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { it.copy(operationProgress = null) }
            result.onSuccess { count ->
                _toastMessages.tryEmit("Successfully extracted $count items")
                refreshAfterMutation()
            }.onFailure { err ->
                _toastMessages.tryEmit("Extraction failed: ${err.localizedMessage}")
            }
        }
    }

    fun runStorageAnalysis() {
        if (_uiState.value.isAnalyzingStorage) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingStorage = true) }
            val filesResult = repository.loadRecentsFeed(showHidden = true, limit = Int.MAX_VALUE, forceRefresh = false)
            val files = filesResult.getOrDefault(emptyList())
            val report = StorageAnalyzer.analyzeAccessibleFiles(files, includeDuplicateScan = true)
            _uiState.update { it.copy(isAnalyzingStorage = false, storageAnalysisReport = report) }
        }
    }

    fun compressItemsToZip(items: List<FileItem>, archiveName: String) {
        if (items.isEmpty()) return
        val trimmed = archiveName.trim().let { if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip" }
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            val parentFile = File(items.first().path)
            val parentDir = if (parentFile.isDirectory) parentFile else (parentFile.parentFile ?: _uiState.value.currentStorageDir)
            val targetZip = File(parentDir, trimmed)
            val sourceFiles = items.map { File(it.path) }
            val result = ZipManager.createArchive(
                sourceFiles = sourceFiles,
                targetZipFile = targetZip
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { it.copy(operationProgress = null) }
            result.onSuccess {
                _toastMessages.tryEmit("Created ZIP: ${targetZip.name}")
                refreshAfterMutation()
            }.onFailure { err ->
                _toastMessages.tryEmit("Compression failed: ${err.localizedMessage}")
            }
        }
    }

    fun deleteItems(items: List<FileItem>, permanently: Boolean = false) {
        deleteFileItems(items, permanently)
    }

    fun computeSize(item: FileItem): Long {
        return if (item.isDirectory) repository.computeDirectorySizeBounded(File(item.path)) else item.sizeBytes
    }

    fun resolvePendingConflict(strategy: ConflictStrategy) {
        initiatePasteToCurrentDirectory(strategy)
    }

    fun cancelActiveOperation() {
        activeOperationJob?.cancel()
        _uiState.update { it.copy(operationProgress = null, pendingConflict = null) }
        _toastMessages.tryEmit("Operation cancelled")
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            val isRecents = _uiState.value.currentSection == HomeSection.RECENTS
            if (isRecents) {
                prefsRepo.setViewMode(true, mode)
            } else {
                prefsRepo.setFolderViewMode(_uiState.value.currentStorageDir.absolutePath, mode)
            }
        }
    }

    fun setTrashViewMode(mode: ViewMode) {
        viewModelScope.launch {
            prefsRepo.setFolderViewMode("trash", mode)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { prefsRepo.setGridColumns(columns) }
    }

    fun setSort(field: SortField, order: SortOrder) {
        viewModelScope.launch {
            val isRecents = _uiState.value.currentSection == HomeSection.RECENTS
            prefsRepo.setSort(isRecents, field, order)
        }
    }

    fun setGrouping(grouping: GroupingMode) {
        viewModelScope.launch {
            val isRecents = _uiState.value.currentSection == HomeSection.RECENTS
            prefsRepo.setGrouping(isRecents, grouping)
        }
    }

    fun setShowFileExtensions(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowFileExtensions(show) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowHiddenFiles(show) }
    }

    fun setCleanerLook(cleaner: Boolean) {
        viewModelScope.launch { prefsRepo.setCleanerLook(cleaner) }
    }

    fun clearOperationHistory() {
        viewModelScope.launch { repository.clearOperationHistory() }
    }

    fun buildOpenWithIntent(item: FileItem): Intent? = repository.buildOpenWithIntent(item)
    fun buildShareIntent(items: List<FileItem>): Intent? = repository.buildShareIntent(items)
    fun buildApkInstallIntent(item: FileItem): Intent? = repository.buildApkInstallIntent(item)

    fun toggleSearchScopeCurrentFolder() {
        toggleSearchScopeCurrentFolder(!_uiState.value.searchInCurrentFolderOnly)
    }

    fun toggleSearchLargeFilesOnly() {
        toggleSearchLargeFilesOnly(!_uiState.value.searchLargeFilesOnly)
    }
}
