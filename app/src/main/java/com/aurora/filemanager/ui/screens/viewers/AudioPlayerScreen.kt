package com.aurora.filemanager.ui.screens.viewers

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.FileContextualMenuSheet
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import com.aurora.filemanager.util.ArtworkPaletteManager
import com.aurora.filemanager.util.QuickCardBadgeInfo
import com.aurora.filemanager.util.ThumbnailAndMetadataManager
import kotlinx.coroutines.delay

/**
 * Built-in Audio Player with Dynamic Artwork-Based Theming:
 * - Extracts color palettes asynchronously from album artwork whenever a new track starts.
 * - Provides rich immersive dynamic background gradients and adaptive UI colors.
 * - Graceful no-artwork fallback behavior to default theme.
 * - Smooth animated transitions between tracks and palettes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    queue: List<FileItem>,
    initialIndex: Int,
    player: Player?,
    backgroundPlaybackEnabled: Boolean,
    onToggleBackgroundPlayback: (Boolean) -> Unit,
    onTrackShown: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onMove: (FileItem) -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onOpenWithExternal: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    val safeQueue = if (queue.isEmpty()) return else queue
    val context = LocalContext.current
    val colors = AuroraTheme.colors
    val isDarkTheme = colors.isDark

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, safeQueue.lastIndex)) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showQueue by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    val currentTrack = safeQueue[currentIndex.coerceIn(0, safeQueue.lastIndex)]
    var artworkBitmap by remember(currentTrack.path) { mutableStateOf<Bitmap?>(null) }
    var badgeInfo by remember(currentTrack.path) { mutableStateOf<QuickCardBadgeInfo?>(null) }
    var dynamicTheme by remember(currentTrack.path, isDarkTheme) {
        mutableStateOf(ArtworkPaletteManager.getDefaultTheme(isDarkTheme))
    }

    val exoPlayer = player ?: return

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, safeQueue.lastIndex)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        
        if (!exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            currentIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, safeQueue.lastIndex)
        }

        shuffleEnabled = exoPlayer.shuffleModeEnabled
        repeatMode = exoPlayer.repeatMode

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(currentTrack.path, isDarkTheme) {
        onTrackShown(currentTrack)
        val info = ThumbnailAndMetadataManager.extractAudioMetadata(context, currentTrack)
        badgeInfo = info
        artworkBitmap = info.thumbnailBitmap

        val cached = ArtworkPaletteManager.getCachedTheme(
            currentTrack.path,
            currentTrack.timestamps.lastModifiedMillis,
            isDarkTheme
        )
        if (cached != null) {
            dynamicTheme = cached
        } else {
            val extracted = ArtworkPaletteManager.extractTheme(
                path = currentTrack.path,
                lastModified = currentTrack.timestamps.lastModifiedMillis,
                bitmap = artworkBitmap,
                isDarkTheme = isDarkTheme
            )
            dynamicTheme = extracted
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(100)
        }
    }

    // Animated transition colors for dynamic artwork-based theming
    val bgTop by animateColorAsState(targetValue = dynamicTheme.backgroundTop, animationSpec = tween(600), label = "bgTop")
    val bgBottom by animateColorAsState(targetValue = dynamicTheme.backgroundBottom, animationSpec = tween(600), label = "bgBottom")
    val accentColor by animateColorAsState(targetValue = dynamicTheme.accentColor, animationSpec = tween(600), label = "accent")
    val secondaryAccent by animateColorAsState(targetValue = dynamicTheme.secondaryAccentColor, animationSpec = tween(600), label = "secondaryAccent")
    val textColor by animateColorAsState(targetValue = dynamicTheme.textColor, animationSpec = tween(600), label = "textColor")
    val textSecondaryColor by animateColorAsState(targetValue = dynamicTheme.textSecondaryColor, animationSpec = tween(600), label = "textSecondary")

    val playbackAccent = if (dynamicTheme.isDefault) Color(0xFFFF3B30) else accentColor

    if (showContextMenu) {
        FileContextualMenuSheet(
            item = currentTrack,
            onOpen = {},
            onOpenWithExternal = { onOpenWithExternal(currentTrack) },
            onInspectArchive = null,
            onCompressToZip = {},
            onInstallApk = null,
            onToggleFavorite = { onToggleFavorite(currentTrack) },
            onTogglePinned = {},
            onShare = { onShare(currentTrack) },
            onRename = {},
            onCopy = { onCopy(currentTrack) },
            onCutMove = { onMove(currentTrack) },
            onOpenContainingFolder = null,
            onViewDetails = { onShowDetails(currentTrack) },
            onDelete = { onDelete(currentTrack) },
            onDeletePermanently = { /* Optional, standard delete uses trash */ },
            onDismiss = { showContextMenu = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgTop, bgBottom)
                )
            )
            .drawWithCache {
                val topGlow = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isDarkTheme) 0.18f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.75f
                )
                val bottomGlow = Brush.radialGradient(
                    colors = listOf(
                        secondaryAccent.copy(alpha = if (isDarkTheme) 0.14f else 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.85f),
                    radius = size.width * 0.75f
                )
                onDrawBehind {
                    drawRect(topGlow)
                    drawRect(bottomGlow)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top Bar: Back arrow left; Share, Favorite, three-dot overflow menu right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Share
                    IconButton(onClick = { onShare(currentTrack) }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    // Favorite
                    IconButton(onClick = { onToggleFavorite(currentTrack) }) {
                        Icon(
                            imageVector = if (currentTrack.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (currentTrack.isFavorite) AuroraPalette.WarningAmber else Color.White
                        )
                    }
                    // Three-dot overflow menu
                    IconButton(onClick = { showContextMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More file options",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (showQueue) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = AuroraRadius.Large,
                    elevated = true,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(safeQueue) { idx, track ->
                            val isSelected = idx == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentIndex = idx
                                        exoPlayer.seekTo(idx, 0L)
                                        exoPlayer.play()
                                    }
                                    .background(if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AudioFile,
                                    contentDescription = null,
                                    tint = if (isSelected) accentColor else textSecondaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Main Album Artwork (Takes flexible remaining space to center itself)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(AuroraRadius.Large)
                            .background(bgBottom.copy(alpha = 0.7f))
                            .border(1.dp, textColor.copy(alpha = 0.15f), AuroraRadius.Large),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap!!.asImageBitmap(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AudioFile,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(100.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Track Info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = badgeInfo?.title ?: currentTrack.displayNameWithoutExtension,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = badgeInfo?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback Controls & Scrubber Timeline
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val sliderPos = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    val unplayedTrackColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f)

                    Slider(
                        value = sliderPos,
                        onValueChange = {
                            val pos = (it * durationMs).toLong()
                            exoPlayer.seekTo(pos)
                            currentPositionMs = pos
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = playbackAccent,
                            inactiveTrackColor = unplayedTrackColor
                        ),
                        thumb = {
                            Box(modifier = Modifier.size(0.dp))
                        },
                        track = { sliderState ->
                            val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                                ((sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(unplayedTrackColor),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(4.dp)
                                        .background(playbackAccent)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FileItem.formatDuration(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = FileItem.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback controls ordered: Shuffle, Previous Track, Play/Pause, Next Track, Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Shuffle
                        IconButton(onClick = {
                            shuffleEnabled = !shuffleEnabled
                            exoPlayer.shuffleModeEnabled = shuffleEnabled
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleEnabled) accentColor else Color.White
                            )
                        }

                        // 2. Previous Track
                        IconButton(
                            onClick = {
                                if (exoPlayer.hasPreviousMediaItem()) {
                                    exoPlayer.seekToPreviousMediaItem()
                                    exoPlayer.play()
                                } else {
                                    exoPlayer.seekTo(0)
                                    exoPlayer.play()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // 3. Play/Pause (prominent in center with dynamic gradient)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(accentColor, secondaryAccent)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // 4. Next Track
                        IconButton(
                            onClick = {
                                if (exoPlayer.hasNextMediaItem()) {
                                    exoPlayer.seekToNextMediaItem()
                                    exoPlayer.play()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // 5. Repeat
                        IconButton(onClick = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            exoPlayer.repeatMode = repeatMode
                        }) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Outlined.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat
                                    else -> Icons.Outlined.Repeat
                                },
                                contentDescription = "Repeat mode",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}
