package com.aurora.filemanager.ui.components

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme

/**
 * Checks whether system animations are enabled so the UI respects accessibility preferences.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale > 0f
        } catch (_: Exception) {
            true
        }
    }
}

/**
 * Lightweight haptic feedback helper for meaningful actions.
 */
@Composable
fun rememberHapticAction(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

/**
 * Ambient background canvas with subtle deep indigo and ocean-blue radial depth.
 * Avoids expensive per-frame shaders while providing rich depth behind translucent glass surfaces.
 */
@Composable
fun AuroraAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AuroraTheme.colors
    val isAmoled = colors.isDark && colors.backgroundPrimary == Color(0xFF000000)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .drawWithCache {
                if (isAmoled) {
                    onDrawBehind { }
                } else {
                    val topGlow = Brush.radialGradient(
                        colors = listOf(
                            colors.accentPrimary.copy(alpha = if (colors.isDark) 0.16f else 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = size.width * 0.75f
                    )
                    val bottomGlow = Brush.radialGradient(
                        colors = listOf(
                            colors.accentSecondary.copy(alpha = if (colors.isDark) 0.12f else 0.06f),
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
            },
        content = content
    )
}

/**
 * Frosted Glass Surface used on navigation controls, floating toolbars, contextual menus,
 * dialogs, bottom sheets, and selected cards.
 *
 * Uses layered translucent fills, a top-left specular gradient sheen, and a 1dp hairline border.
 * Does NOT apply expensive blur to individual list rows, keeping scrolling at 60/120fps.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AuroraRadius.Medium,
    elevated: Boolean = false,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AuroraTheme.colors
    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> colors.glassSelected
            elevated -> colors.glassElevated
            else -> colors.glassSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "glassBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.glassBorderSelected else colors.glassBorder,
        animationSpec = tween(durationMillis = 180),
        label = "glassBorder"
    )

    val sheenBrush = remember(colors.isDark, selected) {
        Brush.linearGradient(
            colors = listOf(
                colors.glassHighlight,
                Color.Transparent,
                if (selected) colors.accentSecondary.copy(alpha = 0.08f) else Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .background(sheenBrush)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Refined Segmented Control for switching between `Recents` and `Storage` on the Home Screen.
 */
@Composable
fun <T> GlassSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: @Composable (T) -> String,
    itemIcon: (@Composable (T) -> ImageVector)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuroraTheme.colors
    val triggerHaptic = rememberHapticAction()

    GlassSurface(
        modifier = modifier.height(44.dp),
        shape = AuroraRadius.Medium,
        elevated = true,
        contentPadding = PaddingValues(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val label = itemLabel(item)
                val tabBg by animateColorAsState(
                    targetValue = if (isSelected) colors.accentPrimary else Color.Transparent,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "segmentTabBg"
                )
                val tabText by animateColorAsState(
                    targetValue = if (isSelected) Color.White else colors.textSecondary,
                    animationSpec = tween(durationMillis = 200),
                    label = "segmentTabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AuroraRadius.Small)
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(
                                    listOf(colors.accentPrimary, colors.accentSecondary.copy(alpha = 0.85f))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(tabBg, tabBg))
                            }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.25f),
                                    AuroraRadius.Small
                                )
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            if (!isSelected) {
                                triggerHaptic()
                                onItemSelected(item)
                            }
                        }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (itemIcon != null) {
                            Icon(
                                imageVector = itemIcon(item),
                                contentDescription = null,
                                tint = tabText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tabText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Filter / Action Chip with frosted glass styling.
 */
@Composable
fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    accentColor: Color = AuroraPalette.OceanBlue
) {
    val colors = AuroraTheme.colors
    val triggerHaptic = rememberHapticAction()
    val bg by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.22f) else colors.glassSurface,
        animationSpec = tween(160),
        label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.7f) else colors.glassBorder,
        animationSpec = tween(160),
        label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            if (colors.isDark) Color.White else accentColor
        } else colors.textSecondary,
        animationSpec = tween(160),
        label = "chipText"
    )

    Row(
        modifier = modifier
            .height(34.dp)
            .clip(AuroraRadius.Small)
            .background(bg)
            .border(1.dp, border, AuroraRadius.Small)
            .clickable {
                triggerHaptic()
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) accentColor else colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * Glass Modal Bottom Sheet for View/Sort controls, File Details, and Contextual Menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AuroraTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = AuroraRadius.SheetTop,
        containerColor = colors.glassElevated,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.glassBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = colors.glassBorder,
                    shape = AuroraRadius.SheetTop
                )
                .padding(bottom = 24.dp),
            content = content
        )
    }
}

/**
 * Skeleton Shimmer placeholder for smooth initial loading states.
 */
@Composable
fun GlassSkeletonRow(modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val animationsEnabled = rememberAnimationsEnabled()
    val alpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val anim by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeletonAlpha"
        )
        anim
    } else {
        0.5f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(AuroraRadius.Medium)
            .background(colors.rowSurface.copy(alpha = alpha))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(AuroraRadius.Small)
                .background(colors.glassBorder)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp)
                    .clip(AuroraRadius.ExtraSmall)
                    .background(colors.glassBorder)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(10.dp)
                    .clip(AuroraRadius.ExtraSmall)
                    .background(colors.glassBorder.copy(alpha = 0.5f))
            )
        }
    }
}

private data class ScrollPillState(
    val scrollFraction: Float,
    val heightFraction: Float,
    val isScrolling: Boolean
)

/**
 * Lightweight, precise, non-glitchy floating scrollbar pill for LazyColumn lists.
 * Renders a minimal, modern pill without any casing, line, track, or background container.
 * Auto-fades smoothly when scrolling stops.
 */
@Composable
fun LazyListScrollbarPill(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollInfo by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty()) return@derivedStateOf null

            val visibleCount = visibleItems.size
            if (visibleCount >= totalItems) return@derivedStateOf null

            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            
            val avgItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleCount
            val exactIndex = firstIndex + (offset / avgItemSize)
            val maxIndex = (totalItems - visibleCount).toFloat()
            val scrollFraction = if (maxIndex > 0f) (exactIndex / maxIndex).coerceIn(0f, 1f) else 0f

            val heightFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 0.8f)
            val isScrolling = listState.isScrollInProgress

            ScrollPillState(scrollFraction, heightFraction, isScrolling)
        }
    }

    val info = scrollInfo ?: return
    val alpha by animateFloatAsState(
        targetValue = if (info.isScrolling) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = if (info.isScrolling) 150 else 600),
        label = "listScrollbarAlpha"
    )

    if (alpha > 0.01f) {
        val scrollColor = if (AuroraTheme.colors.isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val parentHeight = size.height / info.heightFraction
                        val availableSpace = parentHeight - size.height
                        translationY = availableSpace * info.scrollFraction
                    }
                    .width(4.dp)
                    .fillMaxHeight(info.heightFraction)
                    .clip(CircleShape)
                    .background(scrollColor)
            )
        }
    }
}

/**
 * Lightweight, precise, non-glitchy floating scrollbar pill for LazyVerticalGrid.
 * Renders a minimal, modern pill without any casing, line, track, or background container.
 * Auto-fades smoothly when scrolling stops.
 */
@Composable
fun LazyGridScrollbarPill(
    gridState: LazyGridState,
    gridColumns: Int,
    modifier: Modifier = Modifier
) {
    val scrollInfo by remember(gridState, gridColumns) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty()) return@derivedStateOf null

            val columns = gridColumns.coerceAtLeast(1)
            val totalRows = (totalItems + columns - 1) / columns
            val firstVisible = visibleItems.first()
            val firstRowIndex = firstVisible.index / columns
            val lastRowIndex = visibleItems.last().index / columns
            val visibleRows = (lastRowIndex - firstRowIndex + 1).coerceAtLeast(1)

            if (visibleRows >= totalRows) return@derivedStateOf null

            val offset = gridState.firstVisibleItemScrollOffset
            val avgRowSize = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
            val exactRowIndex = firstRowIndex + (offset / avgRowSize)
            val maxRowIndex = (totalRows - visibleRows).toFloat()
            val scrollFraction = if (maxRowIndex > 0f) (exactRowIndex / maxRowIndex).coerceIn(0f, 1f) else 0f

            val heightFraction = (visibleRows.toFloat() / totalRows.toFloat()).coerceIn(0.05f, 0.8f)
            val isScrolling = gridState.isScrollInProgress

            ScrollPillState(scrollFraction, heightFraction, isScrolling)
        }
    }

    val info = scrollInfo ?: return
    val alpha by animateFloatAsState(
        targetValue = if (info.isScrolling) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = if (info.isScrolling) 150 else 600),
        label = "gridScrollbarAlpha"
    )

    if (alpha > 0.01f) {
        val scrollColor = if (AuroraTheme.colors.isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val parentHeight = size.height / info.heightFraction
                        val availableSpace = parentHeight - size.height
                        translationY = availableSpace * info.scrollFraction
                    }
                    .width(4.dp)
                    .fillMaxHeight(info.heightFraction)
                    .clip(CircleShape)
                    .background(scrollColor)
            )
        }
    }
}
