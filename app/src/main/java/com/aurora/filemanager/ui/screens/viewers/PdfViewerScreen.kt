package com.aurora.filemanager.ui.screens.viewers

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Built-in Native PDF Viewer powered by Android's [PdfRenderer]:
 * - High-resolution page rendering with thread-safe [Mutex] access.
 * - Page navigation (Prev / Next buttons + interactive bottom page thumbnail strip).
 * - Butter-smooth native pinch-to-zoom (using [detectTransformGestures]) and unrestricted panning.
 * - Immersive edge-to-edge full-screen reading mode with pure OLED-black background and smooth page swiping.
 */
@Composable
fun PdfViewerScreen(
    item: FileItem,
    onShare: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    val colors = AuroraTheme.colors
    val rendererMutex = remember { Mutex() }
    val view = LocalView.current

    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    
    val context = LocalContext.current
    val progressManager = remember { PdfProgressManager(context) }
    val fileKey = remember(item) { "${item.sizeBytes}_${item.timestamps.lastModifiedMillis}" }
    
    var currentPageIndex by remember { mutableIntStateOf(progressManager.getSavedPage(fileKey)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var isFullScreen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isFullScreen) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            if (isFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentSavedPageIndex by rememberUpdatedState(currentPageIndex)
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, fileKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                progressManager.savePage(fileKey, currentSavedPageIndex)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentPageIndex) {
        delay(1000)
        progressManager.savePage(fileKey, currentPageIndex)
    }

    DisposableEffect(item.path) {
        try {
            val file = File(item.path)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            pfd = descriptor
            pdfRenderer = renderer
            val count = renderer.pageCount
            pageCount = count
            currentPageIndex = currentPageIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
        } catch (t: Throwable) {
            errorMessage = "Unable to open PDF document (${t.localizedMessage ?: "corrupt or password-protected"})."
        }

        onDispose {
            progressManager.savePage(fileKey, currentSavedPageIndex)
            runCatching { pdfRenderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    val backgroundColor = if (colors.isDark) Color.Black else colors.backgroundPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(if (isFullScreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())
    ) {
        // Top Toolbar (Borderless, Standalone Icons directly on background)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pageCount > 0) {
                            "Page ${currentPageIndex + 1} of $pageCount • ${item.formattedSize}"
                        } else {
                            item.formattedSize
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                // Full-screen Toggle Button
                IconButton(onClick = {
                    isFullScreen = !isFullScreen
                    chromeVisible = !isFullScreen
                }) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit full screen" else "Enter full screen",
                        tint = colors.textPrimary
                    )
                }
                IconButton(onClick = { onShare(item) }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share PDF",
                        tint = colors.textPrimary
                    )
                }
            }
        }

        // Main PDF Page Canvas with Horizontal Pager
        if (pageCount > 0) {
            val pdfPagerState = rememberPagerState(
                initialPage = currentPageIndex,
                pageCount = { pageCount }
            )

            LaunchedEffect(pdfPagerState.currentPage) {
                if (currentPageIndex != pdfPagerState.currentPage) {
                    currentPageIndex = pdfPagerState.currentPage
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            LaunchedEffect(currentPageIndex) {
                if (pdfPagerState.currentPage != currentPageIndex) {
                    pdfPagerState.animateScrollToPage(currentPageIndex.coerceIn(0, pageCount - 1))
                    scale = 1f
                    offset = Offset.Zero
                }
            }
            
            HorizontalPager(
                state = pdfPagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(backgroundColor),
                userScrollEnabled = scale <= 1.05f
            ) { pageIndex ->
                PdfPageCanvas(
                    pdfRenderer = pdfRenderer,
                    rendererMutex = rendererMutex,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    scale = scale,
                    offset = offset,
                    errorMessage = errorMessage,
                    onScaleChanged = { newScale -> scale = newScale },
                    onOffsetChanged = { newOffset -> offset = newOffset },
                    onSingleTap = { chromeVisible = !chromeVisible }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.danger,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    CircularProgressIndicator(color = colors.accentSecondary)
                }
            }
        }

        // Bottom Page Thumbnail Strip & Navigation Controls (Premium Frosted Glass)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.then(if (isFullScreen) Modifier.navigationBarsPadding() else Modifier)
        ) {
            if (pageCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isFullScreen) 0.dp else 12.dp, vertical = 10.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = if (isFullScreen) RoundedCornerShape(0.dp) else AuroraRadius.Large,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.2f),
                            spotColor = Color.Black.copy(alpha = 0.3f)
                        )
                        .clip(if (isFullScreen) RoundedCornerShape(0.dp) else AuroraRadius.Large)
                        .background(
                            color = if (colors.isDark) colors.glassSurface.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Column {
                        val thumbListState = rememberLazyListState()
                        LaunchedEffect(currentPageIndex) {
                            thumbListState.animateScrollToItem(currentPageIndex)
                        }

                        LazyRow(
                            state = thumbListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pageCount) { idx ->
                                val selected = idx == currentPageIndex
                                PdfPageThumbnailChip(
                                    pageNumber = idx + 1,
                                    selected = selected,
                                    onClick = { currentPageIndex = idx }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronLeft,
                                    contentDescription = "Previous Page",
                                    tint = if (currentPageIndex > 0) colors.textPrimary else colors.textMuted
                                )
                            }
                            Text(
                                text = "Page ${currentPageIndex + 1} / $pageCount",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary
                            )
                            IconButton(
                                onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                                enabled = currentPageIndex < pageCount - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = "Next Page",
                                    tint = if (currentPageIndex < pageCount - 1) colors.textPrimary else colors.textMuted
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
private fun PdfPageThumbnailChip(
    pageNumber: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(52.dp)
            .clip(AuroraRadius.Small)
            .background(if (selected) colors.accentPrimary else colors.glassInteractive)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$pageNumber",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else colors.textSecondary
        )
    }
}

@Composable
private fun PdfPageCanvas(
    pdfRenderer: PdfRenderer?,
    rendererMutex: Mutex,
    pageIndex: Int,
    pageCount: Int,
    scale: Float,
    offset: Offset,
    errorMessage: String?,
    onScaleChanged: (Float) -> Unit,
    onOffsetChanged: (Offset) -> Unit,
    onSingleTap: () -> Unit
) {
    val colors = AuroraTheme.colors
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isPageLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(pdfRenderer, pageIndex) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        isPageLoading = true
        withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                runCatching {
                    renderer.openPage(pageIndex.coerceIn(0, pageCount - 1)).use { page ->
                        // Render at high resolution (2048px width) for razor-sharp text and lines when zooming in
                        val targetW = 2048
                        val aspect = page.width.toFloat().coerceAtLeast(1f) / page.height.toFloat().coerceAtLeast(1f)
                        val targetH = (targetW / aspect).toInt().coerceAtLeast(200)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageBitmap = bmp
                    }
                }
            }
        }
        isPageLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (currentScale > 1.1f) {
                            onScaleChanged(1f)
                            onOffsetChanged(Offset.Zero)
                        } else {
                            onScaleChanged(2.5f)
                            onOffsetChanged(Offset.Zero)
                        }
                    }
                )
            }
            .pointerInput(pageIndex) {
                detectZoomAndPan(
                    shouldConsume = { currentScale > 1.05f }
                ) { centroid, pan, zoom, _ ->
                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                    
                    if (newScale <= 1.05f) {
                        onScaleChanged(1f)
                        onOffsetChanged(Offset.Zero)
                    } else {
                        val scaleFactor = if (currentScale > 0f) newScale / currentScale else 1f
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f

                        var newOffset = currentOffset + pan

                        if (centroid.isSpecified && scaleFactor != 1f) {
                            val containerCenter = Offset(size.width / 2f, size.height / 2f)
                            val centroidOffset = centroid - containerCenter
                            newOffset = newOffset * scaleFactor + centroidOffset * (1f - scaleFactor)
                        }

                        newOffset = Offset(
                            x = newOffset.x.coerceIn(-maxX, maxX),
                            y = newOffset.y.coerceIn(-maxY, maxY)
                        )
                        onScaleChanged(newScale)
                        onOffsetChanged(newOffset)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.danger,
                    modifier = Modifier.padding(24.dp)
                )
            }
            isPageLoading || pageBitmap == null -> {
                CircularProgressIndicator(color = colors.accentSecondary)
            }
            else -> {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
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
            }
        }
    }
}

private class PdfProgressManager(context: Context) {
    private val prefs = context.getSharedPreferences("pdf_progress", Context.MODE_PRIVATE)

    fun getSavedPage(key: String): Int {
        val map = getMap()
        return map[key] ?: 0
    }

    fun savePage(key: String, page: Int) {
        val map = getMap().toMutableMap()
        val linkedMap = LinkedHashMap(map)
        linkedMap.remove(key)
        linkedMap[key] = page
        
        while (linkedMap.size > 200) {
            val firstKey = linkedMap.keys.first()
            linkedMap.remove(firstKey)
        }
        
        saveMap(linkedMap)
    }

    private fun getMap(): Map<String, Int> {
        val str = prefs.getString("progress_map", "") ?: ""
        if (str.isEmpty()) return emptyMap()
        
        val map = LinkedHashMap<String, Int>()
        str.split(";").forEach {
            val parts = it.split("=")
            if (parts.size == 2) {
                map[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        return map
    }

    private fun saveMap(map: Map<String, Int>) {
        val str = map.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit().putString("progress_map", str).apply()
    }
}
