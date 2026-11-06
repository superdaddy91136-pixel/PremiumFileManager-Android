package com.aurora.filemanager.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.aurora.filemanager.ui.components.itemCard3DStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.AuroraAmbientBackground
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.util.ZipEntryInfo
import com.aurora.filemanager.util.ZipManager
import java.io.File

/**
 * Interactive ZIP Archive Inspector & Extractor Screen:
 * - Displays all archive entries, compressed/uncompressed sizes, and hierarchy.
 * - Supports selecting individual entries or extracting the entire archive.
 * - Uses [ZipManager] with strict Zip-Slip path traversal protection.
 */
@Composable
fun ZipBrowserScreen(
    zipItem: FileItem,
    onExtractEntries: (Set<String>?) -> Unit,
    onBack: () -> Unit
) {
    val colors = AuroraTheme.colors
    var entries by remember(zipItem.path) { mutableStateOf<List<ZipEntryInfo>>(emptyList()) }
    var selectedPaths by remember(zipItem.path) { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember(zipItem.path) { mutableStateOf(true) }
    var errorMessage by remember(zipItem.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(zipItem.path) {
        isLoading = true
        val res = ZipManager.listEntries(File(zipItem.path))
        res.onSuccess { list ->
            entries = list
            errorMessage = null
        }.onFailure { err ->
            errorMessage = err.localizedMessage ?: "Failed to read ZIP archive."
        }
        isLoading = false
    }

    val totalUncompressed = remember(entries) { entries.sumOf { it.uncompressedSize } }

    AuroraAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Glass Bar
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
                            text = zipItem.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entries.size} entries • ${FileItem.formatFileSize(totalUncompressed)} uncompressed",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedPaths = if (selectedPaths.size == entries.size) {
                                emptySet()
                            } else {
                                entries.map { it.entryPath }.toSet()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = "Select All Entries",
                            tint = colors.textPrimary
                        )
                    }
                }
            }

            // Content List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color = colors.accentSecondary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.danger,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(entries, key = { it.entryPath }) { entry ->
                                val isSelected = entry.entryPath in selectedPaths
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (!isSelected) {
                                                Modifier.itemCard3DStyle(false, AuroraRadius.Medium)
                                            } else {
                                                Modifier
                                                    .clip(AuroraRadius.Medium)
                                                    .background(colors.glassSelected)
                                                    .border(
                                                        1.dp,
                                                        colors.glassBorderSelected,
                                                        AuroraRadius.Medium
                                                    )
                                            }
                                        )
                                        .clickable {
                                            selectedPaths = if (isSelected) {
                                                selectedPaths - entry.entryPath
                                            } else {
                                                selectedPaths + entry.entryPath
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.isDirectory) {
                                            Icons.Outlined.Folder
                                        } else {
                                            Icons.Outlined.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = if (entry.isDirectory) {
                                            AuroraPalette.CategoryArchive
                                        } else {
                                            AuroraPalette.OceanBlue
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.entryPath,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!entry.isDirectory) {
                                            Text(
                                                text = "${FileItem.formatFileSize(entry.uncompressedSize)} (compressed ${FileItem.formatFileSize(entry.compressedSize)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isSelected) {
                                            Icons.Outlined.CheckBox
                                        } else {
                                            Icons.Outlined.CheckBoxOutlineBlank
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) colors.accentSecondary else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Extraction Toolbar
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = AuroraRadius.Large,
                elevated = true,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onExtractEntries(
                                if (selectedPaths.isEmpty()) null else selectedPaths
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Unarchive,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedPaths.isEmpty()) {
                                "Extract All (${entries.size} entries)"
                            } else {
                                "Extract Selected (${selectedPaths.size} entries)"
                            },
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
