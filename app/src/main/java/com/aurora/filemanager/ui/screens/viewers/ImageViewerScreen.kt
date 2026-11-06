package com.aurora.filemanager.ui.screens.viewers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Image Viewer with:
 * - Horizontal swipe between sibling images via [HorizontalPager].
 * - Pinch-to-zoom and pan.
 * - Double-tap to toggle 1x (fit-to-screen) and 2.5x zoom.
 * - Refined minimal header showing date/time with dynamic luminance contrast adaptation & top scrim.
 * - Unified bottom action pill (Share -> Copy -> Move -> Favorite -> Delete -> More).
 */
@Composable
fun ImageViewerScreen(
    images: List<FileItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onShare: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onMove: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onShowMore: (FileItem) -> Unit,
    onImageShown: (FileItem) -> Unit
) {
    val safeImages = if (images.isEmpty()) return else images
    val startPage = initialIndex.coerceIn(0, safeImages.lastIndex)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { safeImages.size })
    var chromeVisible by remember { mutableStateOf(true) }

    val currentItem = safeImages[pagerState.currentPage.coerceIn(0, safeImages.lastIndex)]

    var isTopLight by remember(currentItem.path) { mutableStateOf(false) }

    LaunchedEffect(currentItem.path) {
        onImageShown(currentItem)
        isTopLight = withContext(Dispatchers.IO) {
            try {
                val file = File(currentItem.path)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    val width = bmp.width
                    val topHeight = (bmp.height * 0.15f).toInt().coerceAtLeast(1)
                    var totalLum = 0.0
                    var count = 0
                    val pixels = IntArray(width * topHeight)
                    bmp.getPixels(pixels, 0, width, 0, 0, width, topHeight)
                    for (color in pixels) {
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        totalLum += (0.299 * r + 0.587 * g + 0.114 * b)
                        count++
                    }
                    bmp.recycle()
                    if (count > 0) (totalLum / count) > 130.0 else false
                } else false
            } catch (_: Exception) {
                false
            }
        }
    }

    val timestampMillis = listOfNotNull(
        currentItem.timestamps.creationTimeMillis,
        currentItem.timestamps.primaryAddedOrDiscoveredMillis,
        currentItem.timestamps.lastModifiedMillis
    ).firstOrNull { it > 0L } ?: 0L

    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val dateTimeLabel = remember(currentItem.path, timestampMillis, is24) {
        if (timestampMillis > 0L) {
            val timePattern = if (is24) "HH:mm" else "h:mm a"
            SimpleDateFormat("d MMM yyyy · $timePattern", Locale.getDefault()).format(Date(timestampMillis))
        } else {
            currentItem.name
        }
    }

    val contentColor = if (isTopLight) Color(0xFF0F172A) else Color.White
    val textShadow = Shadow(
        color = if (isTopLight) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
        blurRadius = 4f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            val pageItem = safeImages[pageIndex]
            ZoomableImagePage(
                item = pageItem,
                onSingleTap = { chromeVisible = !chromeVisible }
            )
        }

        // Top Adaptive Scrim & Minimal Header (Date/Time)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isTopLight) {
                                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)
                            } else {
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                            }
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button (Borderless, direct over image)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Date/Time & Counter (Directly over image, slightly larger, semi-bold with contrast shadow)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = dateTimeLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = textShadow
                            ),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${safeImages.size} • ${currentItem.formattedSize}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                shadow = textShadow
                            ),
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }

                    // Info Button (Borderless, direct over image)
                    IconButton(
                        onClick = { onShowDetails(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Image details",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Bottom Floating Action Pill Container (Share -> Copy -> Move -> Save/Favorite -> Delete -> More)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .align(Alignment.BottomCenter)
        ) {
            val colors = AuroraTheme.colors
            val isDark = colors.isDark

            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.4f)
                    )
                    .clip(CircleShape)
                    .background(
                        color = if (isDark) colors.glassSurface.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                        ),
                        CircleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Share
                    IconButton(
                        onClick = { onShare(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share image",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 2. Copy
                    IconButton(
                        onClick = { onCopy(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy image",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3. Move
                    IconButton(
                        onClick = { onMove(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCut,
                            contentDescription = "Move image",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 4. Save/Favorite
                    IconButton(
                        onClick = { onToggleFavorite(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (currentItem.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite image",
                            tint = if (currentItem.isFavorite) AuroraPalette.WarningAmber else (if (isDark) Color.White else Color.Black),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 5. Delete
                    IconButton(
                        onClick = { onDelete(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete image",
                            tint = AuroraPalette.DangerCrimson,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 6. More (three-dot menu)
                    IconButton(
                        onClick = { onShowMore(currentItem) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More actions",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    item: FileItem,
    onSingleTap: () -> Unit
) {
    var bitmap by remember(item.path) { mutableStateOf<Bitmap?>(null) }
    var dimensionsLabel by remember(item.path) { mutableStateOf<String?>(null) }
    var loadError by remember(item.path) { mutableStateOf(false) }
    var isLoading by remember(item.path) { mutableStateOf(true) }

    var scale by remember(item.path) { mutableFloatStateOf(1f) }
    var offset by remember(item.path) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.path) {
        isLoading = true
        loadError = false
        withContext(Dispatchers.IO) {
            try {
                val file = File(item.path)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    dimensionsLabel = "${bounds.outWidth} × ${bounds.outHeight} px"
                }
                var sample = 1
                while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (decoded != null) {
                    bitmap = decoded
                } else {
                    loadError = true
                }
            } catch (_: Throwable) {
                loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(item.path) {
                detectZoomAndPan(
                    shouldConsume = { scale > 1f }
                ) { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = AuroraPalette.OceanBlue)
            loadError || bitmap == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = AuroraPalette.TextSecondaryDark,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Unable to decode image preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            else -> {
                val bmp = bitmap!!
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
                dimensionsLabel?.let { dims ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 92.dp)
                            .background(Color.Black.copy(alpha = 0.6f), AuroraRadius.Small)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dims,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
