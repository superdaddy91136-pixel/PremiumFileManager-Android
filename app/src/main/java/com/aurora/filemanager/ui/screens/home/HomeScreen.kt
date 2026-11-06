package com.aurora.filemanager.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.unit.DpOffset
import com.aurora.filemanager.data.model.SortField
import com.aurora.filemanager.data.model.SortOrder
import com.aurora.filemanager.ui.components.GlassModalBottomSheet
import com.aurora.filemanager.ui.theme.ThemeMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.R
import com.aurora.filemanager.ui.components.rememberHapticAction
import com.aurora.filemanager.data.local.RecentSearchEntity
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.data.preferences.UserPreferences
import com.aurora.filemanager.ui.components.AuroraAmbientBackground
import com.aurora.filemanager.ui.components.FileGridCard
import com.aurora.filemanager.ui.components.FileListRow
import com.aurora.filemanager.ui.components.GlassFilterChip
import com.aurora.filemanager.ui.components.GlassSegmentedControl
import com.aurora.filemanager.ui.components.GlassSkeletonRow
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.components.categoryAccentColor
import com.aurora.filemanager.ui.components.categoryIcon
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.ui.viewmodel.FileManagerUiState
import com.aurora.filemanager.ui.viewmodel.HomeSection
import com.aurora.filemanager.util.FileSectionGroup
import com.aurora.filemanager.util.StorageAccessStatus
import java.io.File

/**
 * Primary Home Screen featuring:
 * - Upper-right Search icon and adjacent View & Sort glass button, plus Quick Hub (Favorites/History).
 * - Refined `Recents | Storage` Glass Segmented Control.
 * - Independent scroll state preservation (`recentsListState`, `recentsGridState`, `storageListState`, `storageGridState`).
 * - Unified chronological Recents feed and full Storage folder hierarchy with interactive breadcrumbs.
 * - Floating glass multi-selection toolbar and clipboard paste dock.
 */
@Composable
fun HomeScreen(
    uiState: FileManagerUiState,
    preferences: UserPreferences,
    accessStatus: StorageAccessStatus,
    recentSearches: List<RecentSearchEntity>,
    currentPlayingPath: String? = null,
    isAudioPlaying: Boolean = false,
    isMenuOpen: Boolean = false,
    recentsListState: LazyListState,
    recentsGridState: LazyGridState,
    storageListState: LazyListState,
    storageGridState: LazyGridState,
    onRequestFullStoragePermission: () -> Unit,
    onRequestMediaPermission: () -> Unit,
    onSelectSection: (HomeSection) -> Unit,
    onSelectRecentsCategory: (FileCategory?) -> Unit,
    onRefreshRecents: () -> Unit,
    onRefreshStorage: () -> Unit,
    onNavigateToDirectory: (File) -> Unit,
    onOpenCreateFolderDialog: () -> Unit,
    onOpenViewSortSheet: () -> Unit,
    onOpenQuickHubSheet: () -> Unit,
    onOpenRecycleBinSheet: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit = {},
    onGridColumnsChange: (Int) -> Unit = {},
    onSortChange: (SortField, SortOrder) -> Unit = { _, _ -> },
    onShowExtensionsChange: (Boolean) -> Unit = {},
    onCleanerLookChange: (Boolean) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    // Search callbacks
    onActivateSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearchCurrentFolder: () -> Unit,
    onToggleSearchCategory: (FileCategory) -> Unit,
    onToggleSearchLargeFiles: () -> Unit,
    onClearSearchHistory: () -> Unit,
    // File & Selection callbacks
    onFileClick: (FileItem, List<FileItem>) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem, Rect?) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchCopy: () -> Unit,
    onBatchCut: () -> Unit,
    onBatchShare: () -> Unit,
    onBatchCompressZip: () -> Unit,
    onBatchDelete: () -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit
) {
    val colors = AuroraTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = if (uiState.currentSection == HomeSection.RECENTS) 0 else 1
    ) { 2 }

    LaunchedEffect(uiState.currentSection) {
        val targetPage = if (uiState.currentSection == HomeSection.RECENTS) 0 else 1
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val targetSection = if (pagerState.currentPage == 0) HomeSection.RECENTS else HomeSection.STORAGE
        if (uiState.currentSection != targetSection) {
            onSelectSection(targetSection)
        }
    }

    AuroraAmbientBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top App Bar OR Search Bar
                if (uiState.isSearchActive) {
                    SearchHeaderAndFilters(
                        query = uiState.searchQuery,
                        searchInCurrentFolder = uiState.searchInCurrentFolderOnly,
                        currentFolderName = uiState.currentStorageDir.name.ifEmpty { "Storage" },
                        selectedCategories = uiState.searchCategoryFilters,
                        largeFilesOnly = uiState.searchLargeFilesOnly,
                        recentSearches = recentSearches,
                        onQueryChange = onSearchQueryChange,
                        onToggleCurrentFolder = onToggleSearchCurrentFolder,
                        onToggleCategory = onToggleSearchCategory,
                        onToggleLargeFiles = onToggleSearchLargeFiles,
                        onClearRecentSearches = onClearSearchHistory,
                        onCloseSearch = { onActivateSearch(false) },
                        onOpenViewSortSheet = onOpenViewSortSheet
                    )
                } else {
                    val isRecents = uiState.currentSection == HomeSection.RECENTS
                    val currentViewMode = if (isRecents) preferences.recentsViewMode else (preferences.folderViewModes[uiState.currentStorageDir.absolutePath] ?: ViewMode.LIST)
                    val currentSortField = if (isRecents) preferences.recentsSortField else preferences.storageSortField
                    val currentSortOrder = if (isRecents) preferences.recentsSortOrder else preferences.storageSortOrder

                    MainGlassTopBar(
                        isRecents = isRecents,
                        currentViewMode = currentViewMode,
                        onToggleViewMode = {
                            val newMode = if (currentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            onViewModeChange(newMode)
                            if (newMode == ViewMode.GRID) {
                                onGridColumnsChange(3)
                            }
                        },
                        onSearchClick = { onActivateSearch(true) },
                        onQuickHubClick = onOpenQuickHubSheet,
                        onOpenRecycleBinSheet = onOpenRecycleBinSheet,
                        currentSortField = currentSortField,
                        currentSortOrder = currentSortOrder,
                        onSortChange = onSortChange,
                        currentThemeMode = preferences.themeMode,
                        onThemeModeChange = onThemeModeChange,
                        cleanerLook = preferences.cleanerLook,
                        onCleanerLookChange = onCleanerLookChange,
                        showFileExtensions = preferences.showFileExtensions,
                        onShowExtensionsChange = onShowExtensionsChange
                    )
                }

                // 3. Permission Banner if full storage access is not yet granted
                if (!accessStatus.hasFullStorageAccess) {
                    StoragePermissionGlassCard(
                        hasMediaAccess = accessStatus.hasMediaAccess,
                        onGrantFullAccess = onRequestFullStoragePermission,
                        onGrantMediaOnly = onRequestMediaPermission
                    )
                }

                // 4. Main Content Area (Search Results OR HorizontalPager for Recents/Storage)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.isSearchActive) {
                        SearchResultsContent(
                            results = uiState.searchResults,
                            isSearching = uiState.isSearching,
                            query = uiState.searchQuery,
                            selectedPaths = uiState.selectedPaths,
                            inSelectionMode = uiState.isSelectionMode,
                            showExtension = preferences.showFileExtensions,
                            enableThumbnails = preferences.enableThumbnails,
                            hasFullStorageAccess = accessStatus.hasFullStorageAccess,
                            currentPlayingPath = currentPlayingPath,
                            isAudioPlaying = isAudioPlaying,
                            onFileClick = { item -> onFileClick(item, uiState.searchResults) },
                            onFileLongClick = onFileLongClick,
                            onFileMoreClick = onFileMoreClick
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            if (page == 0) {
                                RecentsSectionContent(
                                    groups = uiState.recentsGroups,
                                    isLoading = uiState.isLoadingRecents,
                                    selectedCategory = uiState.recentsFilterCategory,
                                    viewMode = preferences.recentsViewMode,
                                    gridColumns = preferences.gridColumns,
                                    selectedPaths = uiState.selectedPaths,
                                    inSelectionMode = uiState.isSelectionMode,
                                    showExtension = preferences.showFileExtensions,
                                    enableThumbnails = preferences.enableThumbnails,
                                    cleanerLook = preferences.cleanerLook,
                                    listState = recentsListState,
                                    gridState = recentsGridState,
                                    currentPlayingPath = currentPlayingPath,
                                    isAudioPlaying = isAudioPlaying,
                                    onSelectCategory = onSelectRecentsCategory,
                                    onRefresh = onRefreshRecents,
                                    onFileClick = onFileClick,
                                    onFileLongClick = onFileLongClick,
                                    onFileMoreClick = onFileMoreClick
                                )
                            } else {
                                StorageSectionContent(
                                    directoryStack = uiState.directoryStack,
                                    storageRootPath = uiState.storageRootPath,
                                    groups = uiState.storageGroups,
                                    isLoading = uiState.isLoadingStorage,
                                    errorMessage = uiState.storageErrorMessage,
                                    totalBytes = uiState.totalStorageBytes,
                                    usedBytes = uiState.usedStorageBytes,
                                    availableBytes = uiState.availableStorageBytes,
                                    viewMode = preferences.folderViewModes[uiState.currentStorageDir.absolutePath] ?: ViewMode.LIST,
                                    gridColumns = preferences.gridColumns,
                                    selectedPaths = uiState.selectedPaths,
                                    inSelectionMode = uiState.isSelectionMode,
                                    showExtension = preferences.showFileExtensions,
                                    enableThumbnails = preferences.enableThumbnails,
                                    cleanerLook = preferences.cleanerLook,
                                    listState = storageListState,
                                    gridState = storageGridState,
                                    currentPlayingPath = currentPlayingPath,
                                    isAudioPlaying = isAudioPlaying,
                                    onRefresh = onRefreshStorage,
                                    onNavigateToDirectory = onNavigateToDirectory,
                                    onCreateFolderClick = onOpenCreateFolderDialog,
                                    onOpenStorageAnalysis = onOpenStorageAnalysis,
                                    onFileClick = onFileClick,
                                    onFileLongClick = onFileLongClick,
                                    onFileMoreClick = onFileMoreClick
                                )
                            }
                        }
                    }
                }
            }

            // 5. Floating Liquid Glass Bottom Navigation Bar (when not searching and no menu open)
            AnimatedVisibility(
                visible = !uiState.isSearchActive && !isMenuOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .zIndex(5f)
            ) {
                FloatingLiquidGlassNavBar(
                    selectedSection = uiState.currentSection,
                    onSectionSelected = { section ->
                        onSelectSection(section)
                        val page = if (section == HomeSection.RECENTS) 0 else 1
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }

            // 6. Floating Glass Clipboard Paste Bar (when items are staged for Copy/Move and not in selection mode)
            AnimatedVisibility(
                visible = !uiState.clipboard.isEmpty && !uiState.isSelectionMode && !isMenuOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = if (!uiState.isSearchActive) 78.dp else 14.dp)
                    .zIndex(6f)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AuroraRadius.Large,
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.clipboard.items.size} item(s) ready to ${if (uiState.clipboard.isMoveOperation) "move" else "copy"}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Destination: ${uiState.currentStorageDir.name.ifEmpty { "Internal Storage" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accentSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onClearClipboard) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                        Button(
                            onClick = onPasteHere,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Here", color = Color.White)
                        }
                    }
                }
            }

            // 6. Floating Glass Multi-Selection Batch Action Toolbar
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = if (!uiState.isSearchActive) 78.dp else 14.dp)
                    .zIndex(7f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = AuroraRadius.Large,
                            clip = false
                        )
                        .clip(AuroraRadius.Large)
                        .background(colors.glassElevated)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.glassHighlight,
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear selection",
                                        tint = colors.textPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${uiState.selectedPaths.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = onSelectAll) {
                                Icon(
                                    imageVector = Icons.Outlined.SelectAll,
                                    contentDescription = null,
                                    tint = colors.accentSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select All", color = colors.accentSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BatchToolbarAction(
                                icon = Icons.Outlined.ContentCopy,
                                label = "Copy",
                                onClick = onBatchCopy
                            )
                            BatchToolbarAction(
                                icon = Icons.Outlined.ContentCut,
                                label = "Move",
                                onClick = onBatchCut
                            )
                            BatchToolbarAction(
                                icon = Icons.Outlined.FolderZip,
                                label = "ZIP",
                                onClick = onBatchCompressZip
                            )
                            BatchToolbarAction(
                                icon = Icons.Outlined.Share,
                                label = "Share",
                                onClick = onBatchShare
                            )
                            BatchToolbarAction(
                                icon = Icons.Outlined.DeleteOutline,
                                label = "Delete",
                                tint = colors.danger,
                                onClick = onBatchDelete
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = AuroraTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(AuroraRadius.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * Main Header with app title, Search icon in the upper-right, Grid/List Layout Toggle
 * in place of Saved button, and Three-dot Overflow dropdown menu anchored at the top-right.
 */
@Composable
private fun MainGlassTopBar(
    isRecents: Boolean,
    currentViewMode: ViewMode,
    onToggleViewMode: () -> Unit,
    onSearchClick: () -> Unit,
    onQuickHubClick: () -> Unit,
    onOpenRecycleBinSheet: () -> Unit,
    currentSortField: SortField,
    currentSortOrder: SortOrder,
    onSortChange: (SortField, SortOrder) -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    cleanerLook: Boolean,
    onCleanerLookChange: (Boolean) -> Unit,
    showFileExtensions: Boolean,
    onShowExtensionsChange: (Boolean) -> Unit
) {
    val colors = AuroraTheme.colors
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                ),
                color = colors.textPrimary
            )
        }

        // 1. Upper-right Search button
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search files",
                tint = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 2. Grid / List Layout toggle button
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (currentViewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                contentDescription = if (currentViewMode == ViewMode.LIST) "Switch to Grid View" else "Switch to List View",
                tint = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 3. Three-dot Overflow button anchored to dropdown menu
        Box {
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textPrimary
                )
            }

            StorageOverflowDropdownMenu(
                expanded = showOverflowMenu,
                onDismiss = { showOverflowMenu = false },
                currentSortField = currentSortField,
                currentSortOrder = currentSortOrder,
                currentThemeMode = currentThemeMode,
                cleanerLook = cleanerLook,
                showFileExtensions = showFileExtensions,
                onSortChange = onSortChange,
                onThemeModeChange = onThemeModeChange,
                onCleanerLookChange = onCleanerLookChange,
                onShowExtensionsChange = onShowExtensionsChange,
                onOpenSaved = onQuickHubClick,
                onOpenRecycleBin = onOpenRecycleBinSheet
            )
        }
    }
}

private enum class StorageMenuPage {
    MAIN,
    SORT_BY,
    APPEARANCE
}

@Composable
private fun StorageOverflowDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentSortField: SortField,
    currentSortOrder: SortOrder,
    currentThemeMode: ThemeMode,
    cleanerLook: Boolean,
    showFileExtensions: Boolean,
    onSortChange: (SortField, SortOrder) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onCleanerLookChange: (Boolean) -> Unit,
    onShowExtensionsChange: (Boolean) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenRecycleBin: () -> Unit
) {
    val colors = AuroraTheme.colors
    var page by remember(expanded) { mutableStateOf(StorageMenuPage.MAIN) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        shape = AuroraRadius.Large,
        containerColor = colors.rowSurface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier
            .width(260.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        when (page) {
            StorageMenuPage.MAIN -> {
                val currentSortLabel = when {
                    (currentSortField == SortField.DATE_MODIFIED || currentSortField == SortField.DATE_ADDED) && currentSortOrder == SortOrder.DESCENDING -> "Newest First"
                    (currentSortField == SortField.DATE_MODIFIED || currentSortField == SortField.DATE_ADDED) && currentSortOrder == SortOrder.ASCENDING -> "Oldest First"
                    currentSortField == SortField.SIZE && currentSortOrder == SortOrder.DESCENDING -> "Largest First"
                    currentSortField == SortField.SIZE && currentSortOrder == SortOrder.ASCENDING -> "Smallest First"
                    currentSortField == SortField.NAME && currentSortOrder == SortOrder.ASCENDING -> "Name A–Z"
                    currentSortField == SortField.NAME && currentSortOrder == SortOrder.DESCENDING -> "Name Z–A"
                    else -> currentSortField.displayName
                }

                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Sort By", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(currentSortLabel, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = null,
                            tint = colors.accentSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { page = StorageMenuPage.SORT_BY },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AuroraRadius.Small)
                )

                DropdownMenuItem(
                    text = {
                        val themeLabel = when (currentThemeMode) {
                            ThemeMode.DARK -> "Dark Glass"
                            ThemeMode.LIGHT -> "Light Glass"
                            ThemeMode.SYSTEM -> "System"
                        }
                        Column {
                            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("$themeLabel • ${if (cleanerLook) "Cleaner" else "Standard"}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = colors.accentSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { page = StorageMenuPage.APPEARANCE },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AuroraRadius.Small)
                )

                HorizontalDivider(color = colors.glassBorder, modifier = Modifier.padding(vertical = 4.dp))

                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Saved", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("Favorites & Recent Activity", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Bookmarks,
                            contentDescription = null,
                            tint = colors.accentSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onDismiss()
                        onOpenSaved()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AuroraRadius.Small)
                )

                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Recycle Bin", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("Recover or permanently delete files", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.accentSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onDismiss()
                        onOpenRecycleBin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AuroraRadius.Small)
                )
            }

            StorageMenuPage.SORT_BY -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { page = StorageMenuPage.MAIN }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = colors.glassBorder, modifier = Modifier.padding(bottom = 4.dp))

                val sortOptions = listOf(
                    "Newest First" to (SortField.DATE_MODIFIED to SortOrder.DESCENDING),
                    "Oldest First" to (SortField.DATE_MODIFIED to SortOrder.ASCENDING),
                    "Largest First" to (SortField.SIZE to SortOrder.DESCENDING),
                    "Smallest First" to (SortField.SIZE to SortOrder.ASCENDING),
                    "Name A–Z" to (SortField.NAME to SortOrder.ASCENDING),
                    "Name Z–A" to (SortField.NAME to SortOrder.DESCENDING)
                )

                sortOptions.forEach { (label, pair) ->
                    val (field, order) = pair
                    val isSelected = (currentSortField == field || (field == SortField.DATE_MODIFIED && currentSortField == SortField.DATE_ADDED)) && currentSortOrder == order

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) colors.accentSecondary else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            onSortChange(field, order)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AuroraRadius.Small)
                            .background(if (isSelected) colors.glassSelected else Color.Transparent)
                    )
                }
            }

            StorageMenuPage.APPEARANCE -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { page = StorageMenuPage.MAIN }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = colors.glassBorder, modifier = Modifier.padding(bottom = 6.dp))

                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = "THEME MODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            ThemeMode.DARK to "Dark",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.SYSTEM to "Auto"
                        ).forEach { (mode, label) ->
                            GlassFilterChip(
                                label = label,
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = colors.glassBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "DISPLAY OPTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    CompactSwitchRow(
                        title = "Cleaner Look",
                        checked = cleanerLook,
                        onCheckedChange = onCleanerLookChange
                    )

                    CompactSwitchRow(
                        title = "File Extensions",
                        checked = showFileExtensions,
                        onCheckedChange = onShowExtensionsChange
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentSecondary
            )
        )
    }
}

/**
 * Search Interface with auto-focused input, clear button, View/Sort button beside it,
 * scope toggle (All Indexed Storage vs Current Folder), and category/large-file filters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHeaderAndFilters(
    query: String,
    searchInCurrentFolder: Boolean,
    currentFolderName: String,
    selectedCategories: Set<FileCategory>,
    largeFilesOnly: Boolean,
    recentSearches: List<RecentSearchEntity>,
    onQueryChange: (String) -> Unit,
    onToggleCurrentFolder: () -> Unit,
    onToggleCategory: (FileCategory) -> Unit,
    onToggleLargeFiles: () -> Unit,
    onClearRecentSearches: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenViewSortSheet: () -> Unit
) {
    val colors = AuroraTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = AuroraRadius.Medium,
                elevated = true,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.accentSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accentSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Clear query",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            GlassSurface(
                shape = AuroraRadius.Small,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onOpenViewSortSheet)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Sort search results",
                        tint = colors.accentSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(onClick = onCloseSearch) {
                Text("Done", color = colors.accentSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scope & Category Filters Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                GlassFilterChip(
                    label = if (searchInCurrentFolder) "In: $currentFolderName" else "All Accessible Storage",
                    selected = searchInCurrentFolder,
                    leadingIcon = Icons.Outlined.Folder,
                    onClick = onToggleCurrentFolder
                )
            }
            item {
                GlassFilterChip(
                    label = "Large (> 50 MB)",
                    selected = largeFilesOnly,
                    onClick = onToggleLargeFiles,
                    accentColor = AuroraPalette.WarningAmber
                )
            }
            val filterableCategories = listOf(
                FileCategory.IMAGE,
                FileCategory.VIDEO,
                FileCategory.AUDIO,
                FileCategory.PDF,
                FileCategory.DOCUMENT,
                FileCategory.APK,
                FileCategory.ARCHIVE,
                FileCategory.CODE
            )
            items(filterableCategories, key = { it.name }) { cat ->
                GlassFilterChip(
                    label = cat.displayName,
                    selected = cat in selectedCategories,
                    leadingIcon = categoryIcon(cat),
                    accentColor = categoryAccentColor(cat),
                    onClick = { onToggleCategory(cat) }
                )
            }
        }

        // Optional Recent Searches chips when query is blank
        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentSecondary,
                    modifier = Modifier.clickable(onClick = onClearRecentSearches)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches, key = { it.query }) { recent ->
                    GlassFilterChip(
                        label = recent.query,
                        selected = false,
                        leadingIcon = Icons.Outlined.History,
                        onClick = { onQueryChange(recent.query) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionGlassCard(
    hasMediaAccess: Boolean,
    onGrantFullAccess: () -> Unit,
    onGrantMediaOnly: () -> Unit
) {
    val colors = AuroraTheme.colors
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = AuroraRadius.Medium,
        elevated = true,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = colors.accentSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasMediaAccess) {
                        "Media Access Active • Grant All Files Access for Full Folder Management"
                    } else {
                        stringResource(R.string.permission_title)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGrantFullAccess,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text(stringResource(R.string.grant_all_files), color = Color.White)
                }
                if (!hasMediaAccess) {
                    TextButton(onClick = onGrantMediaOnly) {
                        Text(stringResource(R.string.grant_media_only), color = colors.accentSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Recents Section: Unified chronological feed across all file formats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentsSectionContent(
    groups: List<FileSectionGroup>,
    isLoading: Boolean,
    selectedCategory: FileCategory?,
    viewMode: ViewMode,
    gridColumns: Int,
    selectedPaths: Set<String>,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    cleanerLook: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    currentPlayingPath: String? = null,
    isAudioPlaying: Boolean = false,
    onSelectCategory: (FileCategory?) -> Unit,
    onRefresh: () -> Unit,
    onFileClick: (FileItem, List<FileItem>) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem, Rect?) -> Unit
) {
    val colors = AuroraTheme.colors
    val flatItems = remember(groups) { groups.flatMap { it.items } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Category Filter Bar + Refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    GlassFilterChip(
                        label = "All Recent",
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) }
                    )
                }
                val categories = listOf(
                    FileCategory.IMAGE,
                    FileCategory.VIDEO,
                    FileCategory.AUDIO,
                    FileCategory.PDF,
                    FileCategory.DOCUMENT,
                    FileCategory.APK,
                    FileCategory.ARCHIVE,
                    FileCategory.CODE
                )
                items(categories, key = { it.name }) { cat ->
                    GlassFilterChip(
                        label = cat.displayName,
                        selected = selectedCategory == cat,
                        leadingIcon = categoryIcon(cat),
                        accentColor = categoryAccentColor(cat),
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && flatItems.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    repeat(7) { GlassSkeletonRow() }
                }
            } else if (flatItems.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyStateBox(
                        title = "No Recent Files Found",
                        subtitle = if (selectedCategory != null) {
                            "No recent ${selectedCategory.displayName.lowercase()} found in accessible storage."
                        } else {
                            "Files added to your device will appear here in chronological order."
                        }
                    )
                }
            } else {
                GroupedFilesViewport(
                    groups = groups,
                    flatItems = flatItems,
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    selectedPaths = selectedPaths,
                    inSelectionMode = inSelectionMode,
                    showExtension = showExtension,
                    enableThumbnails = enableThumbnails,
                    cleanerLook = cleanerLook,
                    showAddedTimestamp = true,
                    listState = listState,
                    gridState = gridState,
                    currentPlayingPath = currentPlayingPath,
                    isAudioPlaying = isAudioPlaying,
                    onFileClick = onFileClick,
                    onFileLongClick = onFileLongClick,
                    onFileMoreClick = onFileMoreClick
                )
            }
        }
    }
}

/**
 * Storage Section: Real shared-storage folder hierarchy with breadcrumbs, usage summary bar,
 * New Folder button, and Storage Analysis launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSectionContent(
    directoryStack: List<File>,
    storageRootPath: String,
    groups: List<FileSectionGroup>,
    isLoading: Boolean,
    errorMessage: String?,
    totalBytes: Long,
    usedBytes: Long,
    availableBytes: Long,
    viewMode: ViewMode,
    gridColumns: Int,
    selectedPaths: Set<String>,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    cleanerLook: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    currentPlayingPath: String? = null,
    isAudioPlaying: Boolean = false,
    onRefresh: () -> Unit,
    onNavigateToDirectory: (File) -> Unit,
    onCreateFolderClick: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onFileClick: (FileItem, List<FileItem>) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem, Rect?) -> Unit
) {
    val colors = AuroraTheme.colors
    val flatItems = remember(groups) { groups.flatMap { it.items } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Storage Usage & Analysis Bar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = AuroraRadius.Medium,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Internal Shared Storage",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${FileItem.formatFileSize(usedBytes)} used • ${FileItem.formatFileSize(availableBytes)} free",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    GlassFilterChip(
                        label = "Analyze & Duplicates",
                        selected = false,
                        leadingIcon = Icons.Outlined.Analytics,
                        onClick = onOpenStorageAnalysis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val usedFraction = if (totalBytes > 0L) {
                    (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(AuroraRadius.ExtraSmall)
                        .background(colors.glassBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(usedFraction)
                            .fillMaxHeight()
                            .background(
                                if (usedFraction > 0.9f) colors.danger else colors.accentSecondary
                            )
                    )
                }
            }
        }

        // Interactive Breadcrumbs Bar + New Folder Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                directoryStack.forEachIndexed { index, dir ->
                    val isLast = index == directoryStack.lastIndex
                    val displayName = if (dir.absolutePath == storageRootPath || index == 0) {
                        "Internal Storage"
                    } else {
                        dir.name
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLast) colors.accentSecondary else colors.textSecondary,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(AuroraRadius.ExtraSmall)
                            .clickable { onNavigateToDirectory(dir) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            GlassFilterChip(
                label = "New Folder",
                selected = false,
                leadingIcon = Icons.Outlined.CreateNewFolder,
                onClick = onCreateFolderClick
            )
        }

        // Directory Contents Viewport
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && flatItems.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        repeat(6) { GlassSkeletonRow() }
                    }
                }
                errorMessage != null -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        EmptyStateBox(
                            title = "Restricted or Unavailable Folder",
                            subtitle = errorMessage,
                            isRestricted = true
                        )
                    }
                }
                flatItems.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        EmptyStateBox(
                            title = "Folder is Empty",
                            subtitle = "Use 'New Folder' or paste files from your clipboard to populate this directory."
                        )
                    }
                }
                else -> {
                    GroupedFilesViewport(
                        groups = groups,
                        flatItems = flatItems,
                        viewMode = viewMode,
                        gridColumns = gridColumns,
                        selectedPaths = selectedPaths,
                        inSelectionMode = inSelectionMode,
                        showExtension = showExtension,
                        enableThumbnails = enableThumbnails,
                        cleanerLook = cleanerLook,
                        showAddedTimestamp = false,
                        listState = listState,
                        gridState = gridState,
                        currentPlayingPath = currentPlayingPath,
                        isAudioPlaying = isAudioPlaying,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        onFileMoreClick = onFileMoreClick
                    )
                }
            }
        }
    }
}

@Composable
internal fun GroupedFilesViewport(
    groups: List<FileSectionGroup>,
    flatItems: List<FileItem>,
    viewMode: ViewMode,
    gridColumns: Int,
    selectedPaths: Set<String>,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    cleanerLook: Boolean,
    showAddedTimestamp: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    currentPlayingPath: String? = null,
    isAudioPlaying: Boolean = false,
    overlayBadgeProvider: ((FileItem) -> String?)? = null,
    overlayBadgeColorProvider: ((FileItem) -> Color?)? = null,
    onFileClick: (FileItem, List<FileItem>) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem, Rect?) -> Unit
) {
    val colors = AuroraTheme.colors

    if (viewMode == ViewMode.LIST) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 110.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { group ->
                if (group.title.isNotEmpty()) {
                    item(key = "header_${group.title}") {
                        Text(
                            text = "${group.title.uppercase()} (${group.items.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                items(
                    items = group.items,
                    key = { it.path },
                    contentType = { it.category.name }
                ) { item ->
                    val isCurrentlyPlaying = currentPlayingPath != null && File(currentPlayingPath).absolutePath == File(item.path).absolutePath
                    FileListRow(
                        item = item,
                        isSelected = item.path in selectedPaths,
                        inSelectionMode = inSelectionMode,
                        showExtension = showExtension,
                        enableThumbnails = enableThumbnails,
                        showAddedTimestamp = showAddedTimestamp,
                        cleanerLook = cleanerLook,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        isPlayingState = isAudioPlaying,
                        overlayBadgeText = overlayBadgeProvider?.invoke(item),
                        overlayBadgeColor = overlayBadgeColorProvider?.invoke(item),
                        onClick = { onFileClick(item, flatItems) },
                        onLongClick = { onFileLongClick(item) },
                        onMoreClick = { rect -> onFileMoreClick(item, rect) }
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns.coerceIn(2, 5)),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { group ->
                if (group.title.isNotEmpty()) {
                    item(
                        key = "grid_header_${group.title}",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Text(
                            text = "${group.title.uppercase()} (${group.items.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentSecondary,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                items(
                    items = group.items,
                    key = { it.path },
                    contentType = { it.category.name }
                ) { item ->
                    val isCurrentlyPlaying = currentPlayingPath != null && File(currentPlayingPath).absolutePath == File(item.path).absolutePath
                    FileGridCard(
                        item = item,
                        isSelected = item.path in selectedPaths,
                        inSelectionMode = inSelectionMode,
                        showExtension = showExtension,
                        enableThumbnails = enableThumbnails,
                        cleanerLook = cleanerLook,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        isPlayingState = isAudioPlaying,
                        overlayBadgeText = overlayBadgeProvider?.invoke(item),
                        overlayBadgeColor = overlayBadgeColorProvider?.invoke(item),
                        onClick = { onFileClick(item, flatItems) },
                        onLongClick = { onFileLongClick(item) },
                        onMoreClick = { rect -> onFileMoreClick(item, rect) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<FileItem>,
    isSearching: Boolean,
    query: String,
    selectedPaths: Set<String>,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    hasFullStorageAccess: Boolean,
    currentPlayingPath: String? = null,
    isAudioPlaying: Boolean = false,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem, Rect?) -> Unit
) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasFullStorageAccess) {
            Text(
                text = "Note: Search covers currently permitted locations. Grant All Files Access to index non-media folders.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraPalette.WarningAmber,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (isSearching && results.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(5) { GlassSkeletonRow() }
            }
        } else if (results.isEmpty()) {
            EmptyStateBox(
                title = "No Matching Files",
                subtitle = if (query.isBlank()) {
                    "Type a filename, extension (.pdf, .apk, .zip), or artist name, or toggle a filter chip above."
                } else {
                    "No accessible files matched '$query' with the active filters."
                }
            )
        } else {
            Text(
                text = "${results.size} result${if (results.size == 1) "" else "s"} found",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.path }) { item ->
                    val isCurrentlyPlaying = currentPlayingPath != null && File(currentPlayingPath).absolutePath == File(item.path).absolutePath
                    FileListRow(
                        item = item,
                        isSelected = item.path in selectedPaths,
                        inSelectionMode = inSelectionMode,
                        showExtension = showExtension,
                        enableThumbnails = enableThumbnails,
                        showAddedTimestamp = false,
                        highlightQuery = query,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        isPlayingState = isAudioPlaying,
                        onClick = { onFileClick(item) },
                        onLongClick = { onFileLongClick(item) },
                        onMoreClick = { rect -> onFileMoreClick(item, rect) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBox(
    title: String,
    subtitle: String,
    isRestricted: Boolean = false
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            shape = AuroraRadius.Large,
            contentPadding = PaddingValues(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRestricted) Icons.Outlined.Lock else Icons.Outlined.FolderOff,
                    contentDescription = null,
                    tint = if (isRestricted) AuroraPalette.WarningAmber else colors.accentSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Premium floating liquid-glass navigation bar for switching between Recents and Storage.
 */
@Composable
private fun FloatingLiquidGlassNavBar(
    selectedSection: HomeSection,
    onSectionSelected: (HomeSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuroraTheme.colors
    val triggerHaptic = rememberHapticAction()
    val isDark = colors.isDark

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(
                color = if (isDark) colors.glassSurface.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)
            )
            .border(
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                ),
                CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(0.72f)
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HomeSection.entries.forEach { section ->
                val isSelected = section == selectedSection
                val label = section.title
                val icon = when (section) {
                    HomeSection.RECENTS -> Icons.Outlined.Schedule
                    HomeSection.STORAGE -> Icons.Outlined.Storage
                }

                val bgAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(220),
                    label = "tabBgAlpha"
                )
                val textColor = if (isSelected) {
                    colors.textPrimary
                } else {
                    colors.textSecondary
                }
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.14f * bgAlpha)
                            } else {
                                Color(0xFF0F172A).copy(alpha = 0.08f * bgAlpha)
                            }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = CircleShape,
                                    clip = false
                                )
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            if (!isSelected) {
                                triggerHaptic()
                                onSectionSelected(section)
                            }
                        }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = fontWeight,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

