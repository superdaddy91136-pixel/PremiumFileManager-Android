package com.aurora.filemanager.ui.screens.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.local.TrashEntity
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.FileTimestamps
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.ui.components.GlassModalBottomSheet
import com.aurora.filemanager.ui.screens.home.GroupedFilesViewport
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.util.FileSectionGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinSheet(
    trashItems: List<TrashEntity>,
    viewMode: ViewMode,
    gridColumns: Int,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    cleanerLook: Boolean,
    onViewModeChange: (ViewMode) -> Unit,
    onRestore: (List<TrashEntity>) -> Unit,
    onDeletePermanently: (List<TrashEntity>) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<TrashEntity?>(null) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            containerColor = colors.glassElevated,
            title = { Text("Empty Recycle Bin?", color = colors.textPrimary) },
            text = { Text("All items will be permanently deleted.", color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashConfirm = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text("Empty Bin", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    contextMenuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { contextMenuTarget = null },
            containerColor = colors.glassElevated,
            title = { Text(target.originalName, color = colors.textPrimary) },
            text = { Text("Choose an action for this item in Recycle Bin.", color = colors.textSecondary) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val item = target
                        contextMenuTarget = null
                        onRestore(listOf(item))
                    }) {
                        Text("Restore", color = colors.accentPrimary)
                    }
                    Button(
                        onClick = {
                            val item = target
                            contextMenuTarget = null
                            onDeletePermanently(listOf(item))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                    ) {
                        Text("Delete Permanently", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { contextMenuTarget = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Recycle Bin",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        onViewModeChange(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = "Toggle View Mode",
                            tint = colors.textPrimary
                        )
                    }

                    if (trashItems.isNotEmpty()) {
                        TextButton(onClick = { showEmptyTrashConfirm = true }) {
                            Text("Empty", color = colors.danger)
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.glassBorder)

            if (trashItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Recycle Bin is empty", color = colors.textSecondary)
                }
            } else {
                val fileItems = remember(trashItems) {
                    trashItems.map { item ->
                        val ext = item.originalName.substringAfterLast('.', "").lowercase()
                        val cat = FileCategory.fromFile(item.isDirectory, ext, null)
                        FileItem(
                            id = item.originalPath,
                            path = item.trashPath,
                            contentUri = null,
                            name = item.originalName,
                            extension = ext,
                            sizeBytes = item.fileSizeBytes,
                            isDirectory = item.isDirectory,
                            category = cat,
                            mimeType = when (cat) {
                                FileCategory.IMAGE -> "image/*"
                                FileCategory.VIDEO -> "video/*"
                                FileCategory.AUDIO -> "audio/*"
                                FileCategory.PDF -> "application/pdf"
                                else -> "*/*"
                            },
                            timestamps = FileTimestamps(
                                item.deletedAtMillis,
                                com.aurora.filemanager.data.model.TimestampKind.LAST_MODIFIED_FALLBACK,
                                item.deletedAtMillis,
                                item.deletedAtMillis
                            )
                        )
                    }
                }
                val groups = listOf(FileSectionGroup("Deleted Items", fileItems))

                GroupedFilesViewport(
                    groups = groups,
                    flatItems = fileItems,
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    selectedPaths = emptySet(),
                    inSelectionMode = false,
                    showExtension = showExtension,
                    enableThumbnails = enableThumbnails,
                    cleanerLook = cleanerLook,
                    showAddedTimestamp = true,
                    listState = listState,
                    gridState = gridState,
                    overlayBadgeProvider = { fileItem ->
                        val trash = trashItems.find { it.originalPath == fileItem.id || it.originalPath == fileItem.path }
                        if (trash != null) {
                            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
                            val expiresAt = trash.deletedAtMillis + thirtyDaysMs
                            val daysLeft = ((expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
                            "$daysLeft days"
                        } else null
                    },
                    overlayBadgeColorProvider = { Color.Red },
                    onFileClick = { item, _ ->
                        val trash = trashItems.find { it.originalPath == item.id || it.originalPath == item.path }
                        if (trash != null) contextMenuTarget = trash
                    },
                    onFileLongClick = { item ->
                        val trash = trashItems.find { it.originalPath == item.id || it.originalPath == item.path }
                        if (trash != null) contextMenuTarget = trash
                    },
                    onFileMoreClick = { item, _ ->
                        val trash = trashItems.find { it.originalPath == item.id || it.originalPath == item.path }
                        if (trash != null) contextMenuTarget = trash
                    }
                )
            }
        }
    }
}
