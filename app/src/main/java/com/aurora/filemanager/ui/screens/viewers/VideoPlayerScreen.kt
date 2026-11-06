package com.aurora.filemanager.ui.screens.viewers

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Rational
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.theme.AuroraTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Built-in Media3 ExoPlayer Video Player supporting:
 * - Play/Pause, scrubbing/seeking, full-screen playback.
 * - Premium, borderless Compose overlay controls hiding the default exo player.
 * - Aspect ratio mode toggle, playback speed, subtitle toggle.
 * - Automatic resume from saved playback position when enabled.
 * - Picture-in-Picture (PiP) entry on supported Android versions.
 */
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    item: FileItem,
    initialPositionMs: Long,
    resumePlaybackEnabled: Boolean,
    onSavePosition: (Long) -> Unit,
    onBack: () -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit
) {
    val context = LocalContext.current
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    var showMoreMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember(item.path) {
        ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                val videoFile = File(item.path)
                val mediaUri = Uri.fromFile(videoFile)
                val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)

                // Discover matching external subtitle (.srt or .vtt) in the same folder if present
                val parent = videoFile.parentFile
                val baseName = videoFile.nameWithoutExtension
                val srtFile = parent?.let { File(it, "$baseName.srt") }
                if (srtFile != null && srtFile.exists()) {
                    val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srtFile))
                        .setMimeType("application/x-subrip")
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                if (resumePlaybackEnabled && initialPositionMs > 2_000L) {
                    seekTo(initialPositionMs)
                }
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var isBuffering by remember { mutableStateOf(false) }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    LaunchedEffect(subtitlesEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                isBuffering = player.playbackState == Player.STATE_BUFFERING
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            onSavePosition(exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(200)
        }
    }

    // Auto-hide controls
    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Full-screen immersive mode handling
    val view = LocalView.current
    LaunchedEffect(controlsVisible) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // We provide a custom Compose overlay instead
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // Handled by Compose
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Premium Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)) // Subtle scrim for text readability
            ) {
                // Top Bar (Borderless)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onSavePosition(exoPlayer.currentPosition.coerceAtLeast(0L))
                            onBack()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
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
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.formattedAddedDateWithLabel} • ${item.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    
                    // CC Button
                    IconButton(
                        onClick = {
                            subtitlesEnabled = !subtitlesEnabled
                            controlsVisible = true
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ClosedCaption,
                            contentDescription = "Toggle Subtitles",
                            tint = if (subtitlesEnabled) AuroraTheme.colors.accentSecondary else Color.White
                        )
                    }

                    // Three-dot Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    showMoreMenu = false
                                    onShare(item)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (playbackSpeed == 1.0f) "Playback speed" else "Speed (${playbackSpeed}x)") },
                                onClick = {
                                    showMoreMenu = false
                                    showSpeedMenu = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Details") },
                                onClick = {
                                    showMoreMenu = false
                                    onShowDetails(item)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }

                // Center Buffering Indicator
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                ) {
                    // Seek bar & Remaining Time
                    var sliderPosition by remember { mutableFloatStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }

                    if (!isDragging) {
                        sliderPosition = if (duration > 0) currentPosition.toFloat() / duration else 0f
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val redAccent = Color(0xFFFF3B30)
                        val unplayedTrackColor = Color.White.copy(alpha = 0.3f)

                        Slider(
                            value = sliderPosition,
                            onValueChange = {
                                sliderPosition = it
                                isDragging = true
                                controlsVisible = true
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                exoPlayer.seekTo((sliderPosition * duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = redAccent,
                                inactiveTrackColor = unplayedTrackColor
                            ),
                            thumb = {
                                // Empty box removes the thumb completely for a cleaner look
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
                                            .background(redAccent)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        val remaining = (duration - currentPosition).coerceAtLeast(0L)
                        val formatted = FileItem.formatDuration(remaining)
                        Text(
                            text = "-$formatted",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp)) // Shifted seek bar slightly higher by increasing spacer

                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    val buttonSize = if (isLandscape) 48.dp else 38.dp
                    val iconSize = if (isLandscape) 24.dp else 20.dp
                    val playButtonSize = if (isLandscape) 64.dp else 52.dp
                    val playIconSize = if (isLandscape) 32.dp else 26.dp
                    val spacing = if (isLandscape) 24.dp else 10.dp
                    val playSpacing = if (isLandscape) 32.dp else 16.dp

                    // Playback Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Picture-in-Picture
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val activity = context as? Activity
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                                controlsVisible = false
                            },
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }

                        Spacer(modifier = Modifier.width(spacing))

                        // Rewind 10s
                        IconButton(
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                controlsVisible = true
                            },
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }

                        Spacer(modifier = Modifier.width(playSpacing))

                        Box(
                            modifier = Modifier
                                .size(playButtonSize)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    controlsVisible = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(playIconSize)
                            )
                        }

                        Spacer(modifier = Modifier.width(playSpacing))

                        // Forward 10s
                        IconButton(
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration))
                                controlsVisible = true
                            },
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }

                        Spacer(modifier = Modifier.width(spacing))

                        // Aspect Ratio
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                controlsVisible = true
                            },
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AspectRatio,
                                contentDescription = "Change Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            }
        }
    }
}
