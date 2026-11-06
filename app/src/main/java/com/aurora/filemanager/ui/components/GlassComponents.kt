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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .drawWithCache {
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
