package com.aurora.filemanager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Aurora Glassmorphism Color System.
 *
 * Designed around matte-black & charcoal backgrounds, deep indigo & ocean-blue accents,
 * and carefully layered translucent surfaces with hairline specular borders.
 */
object AuroraPalette {
    // Primary Dark Palette (Matte Obsidian & Charcoal)
    val MatteObsidian = Color(0xFF080A0F)
    val DeepCharcoal = Color(0xFF0E121B)
    val ElevatedCharcoal = Color(0xFF151B28)
    val CardCharcoal = Color(0xFF1A2232)

    // Accents (Deep Indigo & Ocean Blue)
    val DeepIndigo = Color(0xFF4F46E5)
    val IndigoGlow = Color(0xFF6366F1)
    val OceanBlue = Color(0xFF0EA5E9)
    val CyanHighlight = Color(0xFF38BDF8)

    // Translucent Glass Surfaces (Dark Mode)
    val GlassSurfaceDark = Color(0xCC161D2B)        // 80% opacity
    val GlassElevatedDark = Color(0xD91E273A)       // 85% opacity
    val GlassInteractiveDark = Color(0xB3263147)    // 70% opacity
    val GlassSelectedDark = Color(0x3D4F46E5)       // 24% indigo tint
    val GlassBorderDark = Color(0x29FFFFFF)         // 16% white specular border
    val GlassHighlightDark = Color(0x14FFFFFF)      // 8% top sheen

    // Translucent Glass Surfaces (Light Mode)
    val AlabasterBg = Color(0xFFF3F6FA)
    val PearlSurface = Color(0xFFFFFFFF)
    val GlassSurfaceLight = Color(0xD9FFFFFF)       // 85% white
    val GlassElevatedLight = Color(0xF0FFFFFF)      // 94% white
    val GlassInteractiveLight = Color(0xCCEEF2F8)   // 80% cool gray
    val GlassSelectedLight = Color(0x244F46E5)      // 14% indigo tint
    val GlassBorderLight = Color(0x1F0F172A)        // 12% slate border
    val GlassHighlightLight = Color(0x99FFFFFF)     // 60% white sheen

    // Typography & Iconography
    val TextPrimaryDark = Color(0xFFF8FAFC)
    val TextSecondaryDark = Color(0xFF94A3B8)
    val TextMutedDark = Color(0xFF64748B)

    val TextPrimaryLight = Color(0xFF0F172A)
    val TextSecondaryLight = Color(0xFF475569)
    val TextMutedLight = Color(0xFF94A3B8)

    // Semantic File Category Accents
    val CategoryImage = Color(0xFF38BDF8)    // Sky Blue
    val CategoryVideo = Color(0xFFA855F7)    // Violet
    val CategoryAudio = Color(0xFFF43F5E)    // Rose
    val CategoryPdf = Color(0xFFEF4444)      // Crimson
    val CategoryDocument = Color(0xFF3B82F6) // Royal Blue
    val CategoryArchive = Color(0xFFF59E0B)  // Amber
    val CategoryApk = Color(0xFF10B981)      // Emerald
    val CategoryCode = Color(0xFF14B8A6)     // Teal
    val CategoryFolder = Color(0xFF6366F1)   // Indigo

    // Status Colors
    val SuccessEmerald = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val DangerCrimson = Color(0xFFEF4444)
}

@Immutable
data class AuroraGlassColors(
    val isDark: Boolean,
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val rowSurface: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassInteractive: Color,
    val glassSelected: Color,
    val glassBorder: Color,
    val glassBorderSelected: Color,
    val glassHighlight: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val danger: Color,
    val warning: Color,
    val success: Color
)

val DarkAuroraGlassColors = AuroraGlassColors(
    isDark = true,
    backgroundPrimary = AuroraPalette.MatteObsidian,
    backgroundSecondary = AuroraPalette.DeepCharcoal,
    rowSurface = AuroraPalette.ElevatedCharcoal,
    glassSurface = AuroraPalette.GlassSurfaceDark,
    glassElevated = AuroraPalette.GlassElevatedDark,
    glassInteractive = AuroraPalette.GlassInteractiveDark,
    glassSelected = AuroraPalette.GlassSelectedDark,
    glassBorder = AuroraPalette.GlassBorderDark,
    glassBorderSelected = AuroraPalette.OceanBlue.copy(alpha = 0.65f),
    glassHighlight = AuroraPalette.GlassHighlightDark,
    accentPrimary = AuroraPalette.DeepIndigo,
    accentSecondary = AuroraPalette.OceanBlue,
    textPrimary = AuroraPalette.TextPrimaryDark,
    textSecondary = AuroraPalette.TextSecondaryDark,
    textMuted = AuroraPalette.TextMutedDark,
    danger = AuroraPalette.DangerCrimson,
    warning = AuroraPalette.WarningAmber,
    success = AuroraPalette.SuccessEmerald
)

val LightAuroraGlassColors = AuroraGlassColors(
    isDark = false,
    backgroundPrimary = AuroraPalette.AlabasterBg,
    backgroundSecondary = AuroraPalette.PearlSurface,
    rowSurface = Color(0xFFFFFFFF),
    glassSurface = AuroraPalette.GlassSurfaceLight,
    glassElevated = AuroraPalette.GlassElevatedLight,
    glassInteractive = AuroraPalette.GlassInteractiveLight,
    glassSelected = AuroraPalette.GlassSelectedLight,
    glassBorder = AuroraPalette.GlassBorderLight,
    glassBorderSelected = AuroraPalette.DeepIndigo.copy(alpha = 0.55f),
    glassHighlight = AuroraPalette.GlassHighlightLight,
    accentPrimary = AuroraPalette.DeepIndigo,
    accentSecondary = AuroraPalette.OceanBlue,
    textPrimary = AuroraPalette.TextPrimaryLight,
    textSecondary = AuroraPalette.TextSecondaryLight,
    textMuted = AuroraPalette.TextMutedLight,
    danger = AuroraPalette.DangerCrimson,
    warning = AuroraPalette.WarningAmber,
    success = AuroraPalette.SuccessEmerald
)
