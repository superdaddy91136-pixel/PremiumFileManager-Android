package com.aurora.filemanager.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VideoFile
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.vectorResource
import com.aurora.filemanager.R
import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.util.QuickCardBadgeInfo
import com.aurora.filemanager.util.ThumbnailAndMetadataManager

@Composable
fun fileTypeOutlineIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> ImageVector.vectorResource(R.drawable.ic_type_image)
    FileCategory.PDF -> ImageVector.vectorResource(R.drawable.ic_type_pdf)
    FileCategory.VIDEO -> ImageVector.vectorResource(R.drawable.ic_type_video)
    FileCategory.AUDIO -> ImageVector.vectorResource(R.drawable.ic_type_audio)
    FileCategory.DOCUMENT -> Icons.Outlined.Description
    FileCategory.ARCHIVE -> Icons.Outlined.FolderZip
    FileCategory.APK -> Icons.Outlined.Android
    FileCategory.CODE -> Icons.Outlined.Code
    FileCategory.OTHER -> Icons.Outlined.InsertDriveFile
    FileCategory.FOLDER -> Icons.Outlined.Folder
}

@Composable
fun categoryIcon(category: FileCategory): ImageVector = fileTypeOutlineIcon(category)

fun categoryAccentColor(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> AuroraPalette.CategoryFolder
    FileCategory.IMAGE -> AuroraPalette.CategoryImage
    FileCategory.VIDEO -> AuroraPalette.CategoryVideo
    FileCategory.AUDIO -> AuroraPalette.CategoryAudio
    FileCategory.PDF -> AuroraPalette.CategoryPdf
    FileCategory.DOCUMENT -> AuroraPalette.CategoryDocument
    FileCategory.ARCHIVE -> AuroraPalette.CategoryArchive
    FileCategory.APK -> AuroraPalette.CategoryApk
    FileCategory.CODE -> AuroraPalette.CategoryCode
    FileCategory.OTHER -> AuroraPalette.OceanBlue
}

/**
 * Tasteful category-specific colors for floating file-type indicators:
 * - Images: lavender
 * - Videos: white or blue
 * - Audio: pink or magenta
 * - APK: Android green
 * - PDF: soft red
 * - Documents: light blue
 * - Archives: amber
 * - Code: teal
 * - Unknown files: neutral grey
 */
fun fileTypeColor(category: FileCategory): Color = when (category) {
    FileCategory.IMAGE -> Color(0xFFFFD600)    // Bright Yellow
    FileCategory.VIDEO -> Color(0xFF38BDF8)    // Bright Blue
    FileCategory.AUDIO -> Color(0xFFFF1493)    // Bright Deep Pink
    FileCategory.PDF -> Color.Unspecified      // Use original SVG colors
    FileCategory.APK -> Color(0xFF4ADE80)      // Android Green
    FileCategory.DOCUMENT -> Color(0xFF00E5FF) // Light Blue
    FileCategory.ARCHIVE -> Color(0xFFFFB300)  // Amber
    FileCategory.CODE -> Color(0xFF2DD4BF)     // Teal
    FileCategory.OTHER -> Color(0xFFD1D5DB)    // Neutral Grey
    FileCategory.FOLDER -> AuroraPalette.CategoryFolder
}

/**
 * Modern, minimal, icon-only file-type indicator floating directly over the bottom-right corner of file thumbnails.
 * Has no surrounding box, badge, circle, pill, or background container.
 * Features boldened outline strokes with soft ambient drop-shadows for high-definition contrast on any background.
 */
@Composable
fun FloatingFileTypeIndicator(
    category: FileCategory,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val icon = fileTypeOutlineIcon(category)
    val color = fileTypeColor(category)

    Box(
        modifier = modifier.padding(bottom = 3.dp, end = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Soft subtle ambient shadow for floating depth
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.50f),
            modifier = Modifier
                .offset(x = 1.5.dp, y = 1.5.dp)
                .size(size)
        )

        // 2. High-contrast dark outline contour for crisp legibility over bright backgrounds
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier
                .offset(x = 0.75.dp, y = 0.75.dp)
                .size(size)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.60f),
            modifier = Modifier
                .offset(x = (-0.5).dp, y = (-0.5).dp)
                .size(size)
        )

        // 3. Boldened multi-pass primary icon for thicker, stronger outline stroke weight
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .offset(x = 0.25.dp, y = 0.dp)
                .size(size)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .offset(x = (-0.25).dp, y = 0.dp)
                .size(size)
        )
        Icon(
            imageVector = icon,
            contentDescription = category.displayName,
            tint = color,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Highlights matching search substrings inside [text] when [highlightQuery] is non-empty.
 */
@Composable
fun HighlightedFilenameText(
    text: String,
    highlightQuery: String,
    maxLines: Int = 1,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    val colors = AuroraTheme.colors
    if (highlightQuery.isBlank()) {
        Text(
            text = text,
            style = style,
            color = colors.textPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val annotated = remember(text, highlightQuery, colors.accentSecondary) {
        buildAnnotatedString {
            val lowerText = text.lowercase()
            val lowerQuery = highlightQuery.trim().lowercase()
            var startIndex = 0
            while (startIndex < text.length) {
                val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    append(text.substring(startIndex))
                    break
                }
                if (matchIndex > startIndex) {
                    append(text.substring(startIndex, matchIndex))
                }
                withStyle(
                    SpanStyle(
                        color = AuroraPalette.CyanHighlight,
                        fontWeight = FontWeight.Bold,
                        background = AuroraPalette.OceanBlue.copy(alpha = 0.22f)
                    )
                ) {
                    append(text.substring(matchIndex, matchIndex + lowerQuery.length))
                }
                startIndex = matchIndex + lowerQuery.length
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        color = colors.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Asynchronous thumbnail & badge loader composable with memory-cache lookup.
 */
@Composable
fun FileThumbnailBox(
    item: FileItem,
    enableThumbnails: Boolean,
    cleanerLook: Boolean = false,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier,
    overlayBadgeText: String? = null,
    overlayBadgeColor: Color? = null,
    onBadgeLoaded: ((QuickCardBadgeInfo?) -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = categoryAccentColor(item.category)
    val isApk = item.category == FileCategory.APK
    val boxBg = if (isApk) Color.Transparent else accent.copy(alpha = 0.14f)
    val cacheKey = "${item.path}:${item.timestamps.lastModifiedMillis}:256"

    var bitmap by remember(item.path, item.timestamps.lastModifiedMillis) {
        mutableStateOf<Bitmap?>(ThumbnailAndMetadataManager.getCachedThumbnail(cacheKey))
    }

    LaunchedEffect(item.path, item.timestamps.lastModifiedMillis, enableThumbnails) {
        if (!item.isDirectory && enableThumbnails) {
            val (bmp, badge) = ThumbnailAndMetadataManager.loadThumbnailAndBadge(
                context = context,
                item = item,
                targetSizePx = 256
            )
            bitmap = bmp
            onBadgeLoaded?.invoke(badge)
        } else {
            onBadgeLoaded?.invoke(null)
        }
    }

    // Adaptive icon size based on thumbnail dimensions (25% smaller):
    // - Grid mode (size >= 80.dp): 17dp
    // - List mode (size <= 52.dp): 12dp
    // - Other layouts: scale proportionally
    val indicatorSize = when {
        size >= 80.dp -> 17.dp
        size <= 52.dp -> 12.dp
        else -> (size * 0.26f).coerceIn(12.dp, 17.dp)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(AuroraRadius.Small)
            .background(boxBg),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null && !item.isDirectory) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = item.name,
                contentScale = if (item.category == FileCategory.APK || item.category == FileCategory.PDF) {
                    ContentScale.Fit
                } else {
                    ContentScale.Crop
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (item.isRestrictedSystemFolder) Icons.Outlined.Lock else categoryIcon(item.category),
                contentDescription = item.category.displayName,
                tint = accent,
                modifier = Modifier.size(size * 0.52f)
            )
        }

        // Floating file-type indicator for non-directory files
        if (!item.isDirectory) {
            FloatingFileTypeIndicator(
                category = item.category,
                size = indicatorSize,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // Top-left overlay badge (e.g. for Recycle Bin expiry days)
        if (!overlayBadgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .clip(AuroraRadius.ExtraSmall)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = overlayBadgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = overlayBadgeColor ?: Color.Red
                )
            }
        }
    }
}

/**
 * Modifier that applies a subtle 3D, material-like appearance to file and folder cards:
 * soft shadows, gentle surface depth, understated top highlight, and bottom contact shadow,
 * without a visible border outline.
 */
@Composable
fun Modifier.itemCard3DStyle(
    isSelected: Boolean,
    shape: Shape = AuroraRadius.Medium
): Modifier {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    if (isSelected) {
        return this
    }
    return this
        .shadow(
            elevation = 3.dp,
            shape = shape,
            clip = false
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        colors.glassElevated,
                        colors.rowSurface
                    )
                } else {
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF1F5F9)
                    )
                }
            )
        )
        .drawWithCache {
            val topHighlightColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
            val bottomContactColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.07f)
            
            onDrawWithContent {
                drawContent()
                drawLine(
                    color = topHighlightColor,
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bottomContactColor),
                        startY = size.height * 0.75f,
                        endY = size.height
                    )
                )
            }
        }
}

@Composable
fun Modifier.folderCard3DStyle(
    isDirectory: Boolean,
    isSelected: Boolean,
    shape: Shape = AuroraRadius.Medium
): Modifier {
    return this.itemCard3DStyle(isSelected, shape)
}

/**
 * List Row representation of a File or Directory.
 * Uses clean matte surfaces for unselected rows and frosted glass highlights for selected rows,
 * avoiding expensive per-row blur effects to guarantee 60/120fps scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    item: FileItem,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    showAddedTimestamp: Boolean,
    cleanerLook: Boolean = false,
    highlightQuery: String = "",
    isCurrentlyPlaying: Boolean = false,
    isPlayingState: Boolean = false,
    overlayBadgeText: String? = null,
    overlayBadgeColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: (Rect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AuroraTheme.colors
    val triggerHaptic = rememberHapticAction()
    var buttonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var badgeInfo by remember(item.path) {
        mutableStateOf(ThumbnailAndMetadataManager.getCachedBadge(item.path))
    }

    val rowBg by animateColorAsState(
        targetValue = when {
            isSelected -> colors.glassSelected
            isCurrentlyPlaying -> colors.accentSecondary.copy(alpha = 0.15f)
            else -> colors.rowSurface.copy(alpha = 0.72f)
        },
        animationSpec = tween(150),
        label = "rowBg"
    )
    val rowBorder by animateColorAsState(
        targetValue = when {
            isSelected -> colors.glassBorderSelected
            isCurrentlyPlaying -> colors.accentSecondary.copy(alpha = 0.45f)
            else -> colors.glassBorder.copy(alpha = 0.45f)
        },
        animationSpec = tween(150),
        label = "rowBorder"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (!isSelected && !isCurrentlyPlaying) {
                    Modifier.itemCard3DStyle(false, AuroraRadius.Medium)
                } else {
                    Modifier
                        .clip(AuroraRadius.Medium)
                        .background(rowBg)
                        .border(1.dp, rowBorder, AuroraRadius.Medium)
                }
            )
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) triggerHaptic()
                    onClick()
                },
                onLongClick = {
                    triggerHaptic()
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = item.name },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            FileThumbnailBox(
                item = item,
                enableThumbnails = enableThumbnails,
                cleanerLook = cleanerLook,
                size = 48.dp,
                overlayBadgeText = overlayBadgeText,
                overlayBadgeColor = overlayBadgeColor,
                onBadgeLoaded = { badgeInfo = it }
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.accentSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    HighlightedFilenameText(
                        text = item.displayName(showExtension),
                        highlightQuery = highlightQuery
                    )
                }
                if (isCurrentlyPlaying) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AnimatedEqualizerIcon(
                        isPlaying = isPlayingState,
                        tint = colors.accentSecondary
                    )
                }
                if (item.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp).graphicsLayer(rotationZ = 45f)
                    )
                } else if (item.isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Favorited",
                        tint = AuroraPalette.WarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Secondary metadata line ( artist/album or APK package or date & size )
            val secondaryMeta = if (cleanerLook) null else badgeInfo?.secondaryTag
            if (!secondaryMeta.isNullOrBlank()) {
                Text(
                    text = secondaryMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            val dateText = if (showAddedTimestamp && !item.isDirectory) {
                item.formattedAddedDateWithLabel
            } else {
                item.formattedModifiedDate
            }
            val primaryBadge = if (cleanerLook) null else badgeInfo?.primaryTag
            val metaLine = buildString {
                append(item.formattedSize)
                if (!primaryBadge.isNullOrBlank()) {
                    append(" • ")
                    append(primaryBadge)
                }
                append(" • ")
                append(dateText)
            }

            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = {
                triggerHaptic()
                val bounds = buttonCoordinates?.boundsInWindow()
                onMoreClick(bounds)
            },
            modifier = Modifier
                .size(36.dp)
                .onGloballyPositioned { buttonCoordinates = it }
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More actions for ${item.name}",
                tint = colors.textSecondary
            )
        }
    }
}

/**
 * Grid Card representation of a File or Directory with aspect-ratio-preserving thumbnail preview.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    showExtension: Boolean,
    enableThumbnails: Boolean,
    cleanerLook: Boolean = false,
    highlightQuery: String = "",
    isCurrentlyPlaying: Boolean = false,
    isPlayingState: Boolean = false,
    overlayBadgeText: String? = null,
    overlayBadgeColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: (Rect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AuroraTheme.colors
    val triggerHaptic = rememberHapticAction()
    var buttonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var badgeInfo by remember(item.path) {
        mutableStateOf(ThumbnailAndMetadataManager.getCachedBadge(item.path))
    }

    val cardBg by animateColorAsState(
        targetValue = when {
            isSelected -> colors.glassSelected
            isCurrentlyPlaying -> colors.accentSecondary.copy(alpha = 0.15f)
            else -> colors.rowSurface.copy(alpha = 0.78f)
        },
        animationSpec = tween(150),
        label = "gridCardBg"
    )
    val cardBorder by animateColorAsState(
        targetValue = when {
            isSelected -> colors.glassBorderSelected
            isCurrentlyPlaying -> colors.accentSecondary.copy(alpha = 0.45f)
            else -> colors.glassBorder.copy(alpha = 0.45f)
        },
        animationSpec = tween(150),
        label = "gridCardBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isSelected && !isCurrentlyPlaying) {
                    Modifier.itemCard3DStyle(false, AuroraRadius.Medium)
                } else {
                    Modifier
                        .clip(AuroraRadius.Medium)
                        .background(cardBg)
                        .border(1.dp, cardBorder, AuroraRadius.Medium)
                }
            )
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) triggerHaptic()
                    onClick()
                },
                onLongClick = {
                    triggerHaptic()
                    onLongClick()
                }
            )
            .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 8.dp)
            .semantics { contentDescription = item.name }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.22f),
            contentAlignment = Alignment.Center
        ) {
            FileThumbnailBox(
                item = item,
                enableThumbnails = enableThumbnails,
                cleanerLook = cleanerLook,
                size = 96.dp,
                modifier = Modifier.fillMaxSize(),
                overlayBadgeText = overlayBadgeText,
                overlayBadgeColor = overlayBadgeColor,
                onBadgeLoaded = { badgeInfo = it }
            )

            // Top-left favorite star or selection checkmark
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.accentSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (item.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp).graphicsLayer(rotationZ = 45f)
                    )
                }
            } else if (item.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Favorited",
                        tint = AuroraPalette.WarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Bottom-left badge pill (duration, page count, dimensions, APK version)
            val primaryTag = if (cleanerLook) null else badgeInfo?.primaryTag
            if (!primaryTag.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(AuroraRadius.ExtraSmall)
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = primaryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        HighlightedFilenameText(
                            text = item.displayName(showExtension),
                            highlightQuery = highlightQuery,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (isCurrentlyPlaying) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AnimatedEqualizerIcon(
                            isPlaying = isPlayingState,
                            tint = colors.accentSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (cleanerLook) "${item.formattedSize} • ${item.formattedModifiedDate}" else item.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = {
                    triggerHaptic()
                    val bounds = buttonCoordinates?.boundsInWindow()
                    onMoreClick(bounds)
                },
                modifier = Modifier
                    .size(28.dp)
                    .onGloballyPositioned { buttonCoordinates = it }
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More actions",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedEqualizerIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4.dp.value,
        targetValue = 14.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 12.dp.value,
        targetValue = 3.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6.dp.value,
        targetValue = 12.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier
            .size(width = 16.dp, height = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) bar1Height.dp else 6.dp
        val h2 = if (isPlaying) bar2Height.dp else 6.dp
        val h3 = if (isPlaying) bar3Height.dp else 6.dp

        Box(modifier = Modifier.width(3.dp).height(h1).background(tint, CircleShape))
        Box(modifier = Modifier.width(3.dp).height(h2).background(tint, CircleShape))
        Box(modifier = Modifier.width(3.dp).height(h3).background(tint, CircleShape))
    }
}
