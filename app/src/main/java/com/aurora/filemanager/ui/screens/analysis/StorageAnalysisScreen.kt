package com.aurora.filemanager.ui.screens.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.AuroraAmbientBackground
import com.aurora.filemanager.ui.components.GlassFilterChip
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.components.categoryAccentColor
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.util.DuplicateGroup
import com.aurora.filemanager.util.StorageAnalysisReport

/**
 * Storage Analysis & Exact Duplicate Detector Screen:
 * - Displays Total, Used, and Available device storage with a restrained multi-segment breakdown bar.
 * - Clearly distinguishes accessible user files from inaccessible system/app data.
 * - Provides tabs for:
 *   1. Category Breakdown & Storage-Heavy Directories
 *   2. Largest Files (All), Large Videos, and Large Downloads
 *   3. Exact Duplicate Files (verified by size pre-filter + SHA-256 content hash, never auto-deleting)
 */
@Composable
fun StorageAnalysisScreen(
    report: StorageAnalysisReport?,
    isScanning: Boolean,
    onRefreshScan: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenDirectoryPath: (String) -> Unit,
    onRequestDeleteFile: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    val colors = AuroraTheme.colors
    var selectedSection by remember { mutableIntStateOf(0) }

    AuroraAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = AuroraRadius.Medium,
                elevated = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Analysis & Duplicates",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Manual review • Content-hash verified duplicates",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    IconButton(onClick = onRefreshScan, enabled = !isScanning) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rescan Storage",
                            tint = colors.textPrimary
                        )
                    }
                }
            }

            if (isScanning || report == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accentSecondary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Analyzing storage categories & computing SHA-256 duplicate hashes…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Storage Summary Card
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AuroraRadius.Large,
                        elevated = true,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "Used Storage",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = FileItem.formatFileSize(report.usedStorageBytes),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = "${FileItem.formatFileSize(report.availableStorageBytes)} free of ${FileItem.formatFileSize(report.totalStorageBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.accentSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Multi-Category Stacked Bar
                            val totalDenom = report.totalStorageBytes.coerceAtLeast(1L).toFloat()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(AuroraRadius.Small)
                                    .background(colors.glassBorder)
                            ) {
                                report.categoryBreakdown.forEach { usage ->
                                    val weight = (usage.totalBytes.toFloat() / totalDenom).coerceAtLeast(0.005f)
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(categoryAccentColor(usage.category))
                                    )
                                }
                                val sysWeight = (report.systemAndOtherInaccessibleBytes.toFloat() / totalDenom)
                                    .coerceAtLeast(0.01f)
                                Box(
                                    modifier = Modifier
                                        .weight(sysWeight)
                                        .fillMaxHeight()
                                        .background(colors.textMuted.copy(alpha = 0.55f))
                                )
                                val freeWeight = (report.availableStorageBytes.toFloat() / totalDenom)
                                    .coerceAtLeast(0.01f)
                                Box(
                                    modifier = Modifier
                                        .weight(freeWeight)
                                        .fillMaxHeight()
                                        .background(Color.Transparent)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Accessible user files: ${FileItem.formatFileSize(report.accessibleUserFilesBytes)} • System & private app data: ${FileItem.formatFileSize(report.systemAndOtherInaccessibleBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // Section Selector Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassFilterChip(
                            label = "Categories & Folders",
                            selected = selectedSection == 0,
                            onClick = { selectedSection = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        GlassFilterChip(
                            label = "Large Files",
                            selected = selectedSection == 1,
                            onClick = { selectedSection = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        GlassFilterChip(
                            label = "Duplicates (${report.exactDuplicateGroups.size})",
                            selected = selectedSection == 2,
                            onClick = { selectedSection = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (selectedSection) {
                    0 -> {
                        item {
                            Text(
                                text = "ACCESSIBLE FILE CATEGORIES",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                        }
                        items(report.categoryBreakdown, key = { it.category.name }) { usage ->
                            val accent = categoryAccentColor(usage.category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AuroraRadius.Medium)
                                    .background(colors.rowSurface)
                                    .border(1.dp, colors.glassBorder, AuroraRadius.Medium)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = usage.category.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "${usage.fileCount} files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                                Text(
                                    text = FileItem.formatFileSize(usage.totalBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "STORAGE-HEAVY DIRECTORIES",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                        }
                        items(report.storageHeavyDirectories, key = { it.path }) { dirUsage ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AuroraRadius.Medium)
                                    .background(colors.rowSurface)
                                    .border(1.dp, colors.glassBorder, AuroraRadius.Medium)
                                    .clickable { onOpenDirectoryPath(dirUsage.path) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = AuroraPalette.CategoryFolder
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dirUsage.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${dirUsage.fileCount} files • ${dirUsage.path}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = FileItem.formatFileSize(dirUsage.totalBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.accentSecondary
                                )
                            }
                        }
                    }

                    1 -> {
                        item {
                            Text(
                                text = "LARGEST ACCESSIBLE FILES",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                        }
                        items(report.largestFiles, key = { it.path }) { file ->
                            AnalysisCandidateRow(
                                item = file,
                                onOpen = { onOpenFile(file) },
                                onDelete = { onRequestDeleteFile(file) }
                            )
                        }

                        if (report.largeVideos.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "LARGE VIDEOS (> 15 MB)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary
                                )
                            }
                            items(report.largeVideos, key = { "vid_${it.path}" }) { file ->
                                AnalysisCandidateRow(
                                    item = file,
                                    onOpen = { onOpenFile(file) },
                                    onDelete = { onRequestDeleteFile(file) }
                                )
                            }
                        }

                        if (report.largeDownloads.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "LARGE DOWNLOADS (> 5 MB)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary
                                )
                            }
                            items(report.largeDownloads, key = { "dl_${it.path}" }) { file ->
                                AnalysisCandidateRow(
                                    item = file,
                                    onOpen = { onOpenFile(file) },
                                    onDelete = { onRequestDeleteFile(file) }
                                )
                            }
                        }
                    }

                    2 -> {
                        if (report.exactDuplicateGroups.isEmpty()) {
                            item {
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AuroraRadius.Medium,
                                    contentPadding = PaddingValues(20.dp)
                                ) {
                                    Text(
                                        text = "No exact duplicate files (> 4 KB) were found across accessible storage.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        } else {
                            items(report.exactDuplicateGroups, key = { it.sha256Hash }) { group ->
                                DuplicateGroupCard(
                                    group = group,
                                    onOpenFile = onOpenFile,
                                    onRequestDeleteFile = onRequestDeleteFile
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisCandidateRow(
    item: FileItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AuroraRadius.Medium)
            .background(colors.rowSurface)
            .border(1.dp, colors.glassBorder, AuroraRadius.Medium)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.formattedSize} • ${item.path}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Review & Delete ${item.name}",
                tint = colors.danger
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onOpenFile: (FileItem) -> Unit,
    onRequestDeleteFile: (FileItem) -> Unit
) {
    val colors = AuroraTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AuroraRadius.Medium,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${group.files.size} Identical Copies (${FileItem.formatFileSize(group.fileSizeEach)} each)",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accentSecondary
                )
                Text(
                    text = "SHA-256: ${group.sha256Hash.take(10)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            group.files.forEachIndexed { idx, file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AuroraRadius.Small)
                        .background(colors.rowSurface)
                        .clickable { onOpenFile(file) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (idx == 0) "${file.name} (Earliest)" else file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRequestDeleteFile(file) }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete duplicate ${file.name}",
                            tint = colors.danger
                        )
                    }
                }
            }
        }
    }
}
