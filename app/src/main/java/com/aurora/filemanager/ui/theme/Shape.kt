package com.aurora.filemanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Refined, consistent corner radii without excessive pill-shaped distortion.
 */
object AuroraRadius {
    val ExtraSmall = RoundedCornerShape(6.dp)
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(18.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val SheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

val AuroraShapes = Shapes(
    extraSmall = AuroraRadius.ExtraSmall,
    small = AuroraRadius.Small,
    medium = AuroraRadius.Medium,
    large = AuroraRadius.Large,
    extraLarge = AuroraRadius.ExtraLarge
)

object AuroraSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}
