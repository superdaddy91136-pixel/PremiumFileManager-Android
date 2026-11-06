package com.aurora.filemanager.ui.screens.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.local.FavoriteEntity
import com.aurora.filemanager.data.local.OperationHistoryEntity
import com.aurora.filemanager.data.local.RecentOpenedEntity
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.GlassFilterChip
import com.aurora.filemanager.ui.components.GlassModalBottomSheet
import com.aurora.filemanager.ui.components.itemCard3DStyle
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import java.io.File

/**
 * Unified Glass Bottom Sheet providing quick access to:
 * 1. Favorites (files & pinned folders; removing a favorite never deletes the underlying file;
 *    automatically filters out stale paths if a file was deleted externally).
 * 2. Recently Opened Files (tracked separately from the Recents chronological feed).
 * 3. Local Operation History (audit log of Copy, Move, Rename, Delete, Extract, Compress actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickHubBottomSheet(
    favorites: List<FavoriteEntity>,
    recentOpened: List<RecentOpenedEntity>,
    operationHistory: List<OperationHistoryEntity>,
    onOpenPath: (String, Boolean) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AuroraTheme.colors
    var activeTab by remember { mutableIntStateOf(0) }

    val validFavorites = remember(favorites) {
        favorites.filter { File(it.path).exists() }
    }
    val validRecentOpened = remember(recentOpened) {
        recentOpened.filter { File(it.path).exists() }
    }

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites, Opened & Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                if (activeTab == 2 && operationHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear Log", color = colors.accentSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassFilterChip(
                    label = "Favorites (${validFavorites.size})",
                    selected = activeTab == 0,
                    leadingIcon = Icons.Outlined.Star,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                GlassFilterChip(
                    label = "Opened (${validRecentOpened.size})",
                    selected = activeTab == 1,
                    leadingIcon = Icons.Outlined.Visibility,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.weight(1f)
                )
                GlassFilterChip(
                    label = "History (${operationHistory.size})",
                    selected = activeTab == 2,
                    leadingIcon = Icons.Outlined.History,
                    onClick = { activeTab = 2 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (activeTab) {
                    0 -> {
                        if (validFavorites.isEmpty()) {
                            item {
                                Text(
                                    text = "No favorited files or folders yet. Star any file or folder to pin it here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else {
                            items(validFavorites, key = { it.path }) { fav ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .itemCard3DStyle(false, AuroraRadius.Medium)
                                        .clickable {
                                            onDismiss()
                                            onOpenPath(fav.path, fav.isDirectory)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (fav.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (fav.isDirectory) AuroraPalette.CategoryFolder else AuroraPalette.OceanBlue
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fav.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = fav.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onRemoveFavorite(fav.path) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Star,
                                            contentDescription = "Remove from Favorites (keeps file)",
                                            tint = AuroraPalette.WarningAmber
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (validRecentOpened.isEmpty()) {
                            item {
                                Text(
                                    text = "Files you preview or open will appear here separately from newly added Recents.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else {
                            items(validRecentOpened, key = { it.path }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .itemCard3DStyle(false, AuroraRadius.Medium)
                                        .clickable {
                                            onDismiss()
                                            onOpenPath(entry.path, false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = AuroraPalette.OceanBlue
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Opened ${FileItem.formatTimestamp(entry.openedAtMillis)} • ${FileItem.formatFileSize(entry.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (operationHistory.isEmpty()) {
                            item {
                                Text(
                                    text = "No recent file operations logged yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else {
                            items(operationHistory, key = { it.id }) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .itemCard3DStyle(false, AuroraRadius.Medium)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.summary,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${log.actionType} • ${FileItem.formatTimestamp(log.timestampMillis)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
