package com.aurora.filemanager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.model.ConflictStrategy
import com.aurora.filemanager.data.model.DetailedFileMetadata
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.FileOperationProgress
import com.aurora.filemanager.data.model.GroupingMode
import com.aurora.filemanager.data.model.SortField
import com.aurora.filemanager.data.model.SortOrder
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.ui.theme.ThemeMode
import com.aurora.filemanager.util.ThumbnailAndMetadataManager

/**
 * Glass Bottom Sheet for View Mode, Grid Density, Sort Field, Sort Order, Grouping,
 * File Extensions toggle, Hidden Files toggle, and Theme Mode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewAndSortBottomSheet(
    isRecentsSection: Boolean,
    currentViewMode: ViewMode,
    gridColumns: Int,
    currentSortField: SortField,
    currentSortOrder: SortOrder,
    currentGrouping: GroupingMode,
    showFileExtensions: Boolean,
    showHiddenFiles: Boolean,
    cleanerLook: Boolean,
    currentThemeMode: ThemeMode,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onSortChange: (SortField, SortOrder) -> Unit,
    onGroupingChange: (GroupingMode) -> Unit,
    onShowExtensionsChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onCleanerLookChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = if (isRecentsSection) "Recents View & Sort" else "Storage View & Sort",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Layout Mode (List vs Grid)
            Text(
                text = "LAYOUT",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassFilterChip(
                    label = "List View",
                    selected = currentViewMode == ViewMode.LIST,
                    leadingIcon = Icons.Outlined.ViewList,
                    onClick = { onViewModeChange(ViewMode.LIST) },
                    modifier = Modifier.weight(1f)
                )
                GlassFilterChip(
                    label = "Grid View",
                    selected = currentViewMode == ViewMode.GRID,
                    leadingIcon = Icons.Outlined.GridView,
                    onClick = { onViewModeChange(ViewMode.GRID) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (currentViewMode == ViewMode.GRID) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "GRID DENSITY (COLUMNS)",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 4, 5).forEach { cols ->
                        GlassFilterChip(
                            label = "$cols Columns",
                            selected = gridColumns == cols,
                            onClick = { onGridColumnsChange(cols) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Sort Field
            Text(
                text = "SORT BY",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SortField.entries.forEach { field ->
                    GlassFilterChip(
                        label = field.displayName,
                        selected = currentSortField == field,
                        onClick = { onSortChange(field, currentSortOrder) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Sort Order (Ascending vs Descending)
            Text(
                text = "ORDER",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val descLabel = when (currentSortField) {
                    SortField.DATE_ADDED, SortField.DATE_MODIFIED -> "Newest First"
                    SortField.SIZE -> "Largest First"
                    SortField.NAME, SortField.TYPE -> "Z → A"
                }
                val ascLabel = when (currentSortField) {
                    SortField.DATE_ADDED, SortField.DATE_MODIFIED -> "Oldest First"
                    SortField.SIZE -> "Smallest First"
                    SortField.NAME, SortField.TYPE -> "A → Z"
                }
                GlassFilterChip(
                    label = descLabel,
                    selected = currentSortOrder == SortOrder.DESCENDING,
                    leadingIcon = Icons.Outlined.ArrowDownward,
                    onClick = { onSortChange(currentSortField, SortOrder.DESCENDING) },
                    modifier = Modifier.weight(1f)
                )
                GlassFilterChip(
                    label = ascLabel,
                    selected = currentSortOrder == SortOrder.ASCENDING,
                    leadingIcon = Icons.Outlined.ArrowUpward,
                    onClick = { onSortChange(currentSortField, SortOrder.ASCENDING) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Grouping
            Text(
                text = "GROUPING",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupingMode.entries.forEach { mode ->
                    GlassFilterChip(
                        label = mode.displayName,
                        selected = currentGrouping == mode,
                        onClick = { onGroupingChange(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.glassBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // 5. Display Toggles
            PreferenceSwitchRow(
                title = "Show file extensions",
                subtitle = "Display .pdf, .jpg, .apk, and .zip suffixes in lists",
                checked = showFileExtensions,
                onCheckedChange = onShowExtensionsChange
            )
            PreferenceSwitchRow(
                title = "Show hidden files",
                subtitle = "Include dotfiles starting with '.' where permitted",
                checked = showHiddenFiles,
                onCheckedChange = onShowHiddenChange
            )
            PreferenceSwitchRow(
                title = "Cleaner Look",
                subtitle = "Show only file name, size, and date for a minimalist view",
                checked = cleanerLook,
                onCheckedChange = onCleanerLookChange
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "APPEARANCE THEME",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    GlassFilterChip(
                        label = when (mode) {
                            ThemeMode.DARK -> "Dark Glass"
                            ThemeMode.LIGHT -> "Light Glass"
                            ThemeMode.SYSTEM -> "System"
                        },
                        selected = currentThemeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
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

class AnchoredPopupPositionProvider(
    private val anchorRect: Rect?,
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = with(density) { 12.dp.roundToPx() }
        val topInsetPx = with(density) { 48.dp.roundToPx() }
        val bottomInsetPx = with(density) { 48.dp.roundToPx() }

        if (anchorRect == null) {
            val defaultX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
            val defaultY = (topInsetPx * 2).coerceAtMost((windowSize.height - popupContentSize.height - bottomInsetPx).coerceAtLeast(0))
            return IntOffset(defaultX, defaultY)
        }

        val anchorRight = anchorRect.right.toInt()
        val anchorBottom = anchorRect.bottom.toInt()
        val anchorTop = anchorRect.top.toInt()

        // Align right edge of popup near right edge of button
        var x = anchorRight - popupContentSize.width
        x = x.coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))

        // Open just below the button by default
        var y = anchorBottom + with(density) { 4.dp.roundToPx() }

        // If not enough space below, flip above the button
        val spaceBelow = windowSize.height - bottomInsetPx - y
        if (spaceBelow < popupContentSize.height) {
            val flippedY = anchorTop - popupContentSize.height - with(density) { 4.dp.roundToPx() }
            if (flippedY >= topInsetPx) {
                y = flippedY
            }
        }

        // Constrain within screen safe area
        y = y.coerceIn(topInsetPx, (windowSize.height - popupContentSize.height - bottomInsetPx).coerceAtLeast(topInsetPx))

        return IntOffset(x, y)
    }
}

/**
 * Compact Anchored Popup Menu for a single file or folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextualMenuSheet(
    item: FileItem,
    anchorRect: Rect? = null,
    onOpen: () -> Unit,
    onOpenWithExternal: () -> Unit,
    onInspectArchive: (() -> Unit)?,
    onCompressToZip: () -> Unit,
    onInstallApk: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCutMove: () -> Unit,
    onOpenContainingFolder: (() -> Unit)?,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    val density = LocalDensity.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        popupPositionProvider = remember(anchorRect, density) {
            AnchoredPopupPositionProvider(anchorRect, density)
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .width(200.dp)
                    .heightIn(max = 380.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.glassElevated,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    PopupActionItem(
                        icon = Icons.Outlined.FolderOpen,
                        label = if (item.isDirectory) "Open Folder" else "Open Preview",
                        onClick = { onDismiss(); onOpen() }
                    )

                    if (!item.isDirectory) {
                        PopupActionItem(
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            label = "Open with External App…",
                            onClick = { onDismiss(); onOpenWithExternal() }
                        )
                    }

                    if (onInstallApk != null && item.category == FileCategory.APK) {
                        PopupActionItem(
                            icon = Icons.Outlined.Android,
                            label = "Install Package (System Installer)",
                            tint = AuroraPalette.CategoryApk,
                            onClick = { onDismiss(); onInstallApk() }
                        )
                    }

                    if (onInspectArchive != null && item.category == FileCategory.ARCHIVE) {
                        PopupActionItem(
                            icon = Icons.Outlined.Unarchive,
                            label = "Browse & Extract ZIP Archive",
                            tint = AuroraPalette.CategoryArchive,
                            onClick = { onDismiss(); onInspectArchive() }
                        )
                    }

                    PopupActionItem(
                        icon = Icons.Outlined.PushPin,
                        label = if (item.isPinned) "Unpin from Top" else "Pin to Top",
                        tint = if (item.isPinned) colors.accentSecondary else colors.textPrimary,
                        onClick = { onDismiss(); onTogglePinned() }
                    )

                    PopupActionItem(
                        icon = if (item.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                        tint = if (item.isFavorite) AuroraPalette.WarningAmber else colors.textPrimary,
                        onClick = { onDismiss(); onToggleFavorite() }
                    )

                    if (onOpenContainingFolder != null) {
                        PopupActionItem(
                            icon = Icons.Outlined.FolderOpen,
                            label = "Open Containing Folder",
                            onClick = { onDismiss(); onOpenContainingFolder() }
                        )
                    }

                    PopupActionItem(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy",
                        onClick = { onDismiss(); onCopy() }
                    )

                    PopupActionItem(
                        icon = Icons.Outlined.ContentCut,
                        label = "Cut / Move",
                        onClick = { onDismiss(); onCutMove() }
                    )

                    PopupActionItem(
                        icon = Icons.Outlined.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = { onDismiss(); onRename() }
                    )

                    PopupActionItem(
                        icon = Icons.Outlined.FolderZip,
                        label = "Compress to ZIP…",
                        onClick = { onDismiss(); onCompressToZip() }
                    )

                    if (!item.isDirectory) {
                        PopupActionItem(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            onClick = { onDismiss(); onShare() }
                        )
                    }

                    PopupActionItem(
                        icon = Icons.Outlined.Info,
                        label = "Properties & Metadata",
                        onClick = { onDismiss(); onViewDetails() }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PopupActionItem(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Delete",
                        tint = colors.danger,
                        onClick = { onDismiss(); onDelete() }
                    )

                    PopupActionItem(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Delete Permanently",
                        tint = colors.danger,
                        onClick = { onDismiss(); onDeletePermanently() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = AuroraTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = tint
        )
    }
}

/**
 * File Details & Metadata Bottom Sheet.
 * Extracts rich format-specific metadata and on-demand SHA-256 / MD5 hashes off the main thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsBottomSheet(
    item: FileItem,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onOpenContainingFolder: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = AuroraTheme.colors
    var details by remember(item.path) { mutableStateOf<DetailedFileMetadata?>(null) }
    var computeHashes by remember(item.path) { mutableStateOf(false) }
    var isCalculatingHash by remember(item.path) { mutableStateOf(false) }

    LaunchedEffect(item.path, computeHashes) {
        if (computeHashes) isCalculatingHash = true
        details = ThumbnailAndMetadataManager.extractDetailedMetadata(
            context = context,
            item = item,
            computeHashes = computeHashes
        )
        isCalculatingHash = false
    }

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnailBox(item = item, enableThumbnails = true, size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${item.category.displayName} (${item.mimeType})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassFilterChip(
                    label = "Rename",
                    selected = false,
                    leadingIcon = Icons.Outlined.DriveFileRenameOutline,
                    onClick = { onDismiss(); onRename() },
                    modifier = Modifier.weight(1f)
                )
                if (!item.isDirectory) {
                    GlassFilterChip(
                        label = "Share",
                        selected = false,
                        leadingIcon = Icons.Outlined.Share,
                        onClick = { onDismiss(); onShare() },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onOpenContainingFolder != null) {
                    GlassFilterChip(
                        label = "Folder",
                        selected = false,
                        leadingIcon = Icons.Outlined.FolderOpen,
                        onClick = { onDismiss(); onOpenContainingFolder() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = AuroraRadius.Medium,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(
                        label = "Size",
                        value = if (item.isDirectory) {
                            item.formattedSize
                        } else {
                            "${item.formattedSize} (${String.format("%,d", item.sizeBytes)} bytes)"
                        }
                    )
                    DetailRow(
                        label = "Accessible Location",
                        value = item.path,
                        copyable = true,
                        context = context
                    )
                    DetailRow(
                        label = item.timestamps.primaryAddedSource.label,
                        value = FileItem.formatTimestamp(item.timestamps.primaryAddedOrDiscoveredMillis)
                    )
                    item.timestamps.creationTimeMillis?.let { created ->
                        DetailRow(
                            label = "Filesystem Created",
                            value = FileItem.formatTimestamp(created)
                        )
                    }
                    DetailRow(
                        label = "Last Modified",
                        value = FileItem.formatTimestamp(item.timestamps.lastModifiedMillis)
                    )
                    item.timestamps.lastOpenedMillis?.let { opened ->
                        DetailRow(
                            label = "Last Opened in Aurora",
                            value = FileItem.formatTimestamp(opened)
                        )
                    }

                    val meta = details
                    if (meta != null) {
                        DetailRow(
                            label = "Access Permissions",
                            value = buildString {
                                append(if (meta.readable) "Readable" else "Not Readable")
                                append(" • ")
                                append(if (meta.writable) "Writable" else "Read-Only")
                            }
                        )
                        meta.imageDimensions?.let { DetailRow("Image Dimensions", it) }
                        meta.cameraMakeModel?.let { DetailRow("Camera / EXIF", it) }
                        meta.videoResolution?.let { DetailRow("Video Resolution", it) }
                        meta.videoDurationFormatted?.let { DetailRow("Video Duration", it) }
                        meta.audioTitle?.let { DetailRow("Track Title", it) }
                        meta.audioArtist?.let { DetailRow("Artist", it) }
                        meta.audioAlbum?.let { DetailRow("Album", it) }
                        meta.audioDurationFormatted?.let { DetailRow("Audio Duration", it) }
                        meta.audioBitrateKbps?.let { DetailRow("Audio Bitrate", "$it kbps") }
                        meta.pdfPageCount?.let { DetailRow("PDF Page Count", "$it pages") }
                        meta.apkAppName?.let { DetailRow("Application Name", it) }
                        meta.apkPackageName?.let {
                            DetailRow("Package Name", it, copyable = true, context = context)
                        }
                        meta.apkVersionName?.let { ver ->
                            val code = meta.apkVersionCode ?: 0L
                            DetailRow("APK Version", "$ver (versionCode $code)")
                        }
                        if (meta.apkMinSdk != null && meta.apkTargetSdk != null) {
                            DetailRow("SDK Target", "minSdk ${meta.apkMinSdk} • targetSdk ${meta.apkTargetSdk}")
                        }
                        meta.zipEntryCount?.let { count ->
                            val uncomp = FileItem.formatFileSize(meta.zipUncompressedBytes ?: 0L)
                            DetailRow("Archive Contents", "$count entries ($uncomp uncompressed)")
                        }
                        meta.sha256Hash?.let {
                            DetailRow("SHA-256 Checksum", it, copyable = true, context = context, monospace = true)
                        }
                        meta.md5Hash?.let {
                            DetailRow("MD5 Checksum", it, copyable = true, context = context, monospace = true)
                        }
                    }
                }
            }

            if (!item.isDirectory && details?.sha256Hash == null) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassFilterChip(
                    label = if (isCalculatingHash) "Computing SHA-256 & MD5…" else "Compute SHA-256 & MD5 Checksums",
                    selected = computeHashes,
                    leadingIcon = Icons.Outlined.Fingerprint,
                    onClick = { if (!isCalculatingHash) computeHashes = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    context: Context? = null,
    monospace: Boolean = false
) {
    val colors = AuroraTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            if (copyable && context != null) {
                Text(
                    text = "Copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentSecondary,
                    modifier = Modifier.clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = colors.textPrimary
        )
    }
}

/**
 * Dialog for Renaming a file/folder or Creating a new folder or ZIP archive.
 */
@Composable
fun SingleInputGlassDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.glassElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
            ) {
                Text(confirmLabel, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

/**
 * Destructive Delete Confirmation Dialog.
 */
@Composable
fun ConfirmDeleteDialog(
    items: List<FileItem>,
    isPermanent: Boolean = false,
    isOversized: Boolean = false,
    onConfirmDelete: (permanently: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    val title = if (items.size == 1) {
        if (isPermanent) "Delete '${items.first().name}' permanently?"
        else "Move '${items.first().name}' to Recycle Bin?"
    } else {
        if (isPermanent) "Delete ${items.size} items permanently?"
        else "Move ${items.size} items to Recycle Bin?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.glassElevated,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Text(
                text = when {
                    isOversized -> "The selected item(s) exceed the 1 GB Recycle Bin limit and will be permanently deleted from shared storage. This cannot be undone."
                    isPermanent -> "This action permanently deletes the selected item(s) from shared storage and cannot be undone."
                    else -> "The selected item(s) will be moved to the Recycle Bin and automatically deleted after 30 days."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(isPermanent || isOversized) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPermanent || isOversized) colors.danger else colors.accentPrimary
                )
            ) {
                Text(if (isPermanent || isOversized) "Delete Permanently" else "Move to Bin", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

/**
 * Conflict Resolution Dialog when copying/moving files into a folder with matching filenames.
 */
@Composable
fun ConflictResolutionDialog(
    conflictingNames: List<String>,
    onSelectStrategy: (ConflictStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.glassElevated,
        title = {
            Text(
                text = "Name Conflict Detected",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val previewNames = conflictingNames.take(3).joinToString(", ")
                val extra = if (conflictingNames.size > 3) " and ${conflictingNames.size - 3} more" else ""
                Text(
                    text = "Destination already contains $previewNames$extra. How would you like to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                ConflictStrategy.entries.forEach { strategy ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AuroraRadius.Small)
                            .background(colors.glassInteractive)
                            .border(1.dp, colors.glassBorder, AuroraRadius.Small)
                            .clickable { onSelectStrategy(strategy) }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = strategy.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strategy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel Operation", color = colors.textSecondary)
            }
        }
    )
}

/**
 * Progress Dialog for long-running Copy, Move, Delete, or ZIP operations with safe cancellation.
 */
@Composable
fun OperationProgressDialog(
    progress: FileOperationProgress,
    onCancel: () -> Unit
) {
    val colors = AuroraTheme.colors
    AlertDialog(
        onDismissRequest = {},
        containerColor = colors.glassElevated,
        title = {
            Text(
                text = progress.operationTitle,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = progress.currentFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    color = colors.accentSecondary,
                    trackColor = colors.glassBorder,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${progress.processedCount} of ${progress.totalCount} items",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (progress.isCancellable) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = colors.danger)
                }
            }
        }
    )
}
