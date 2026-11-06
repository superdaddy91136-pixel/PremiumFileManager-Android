package com.aurora.filemanager.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class DynamicPlayerTheme(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val accentColor: Color,
    val secondaryAccentColor: Color,
    val textColor: Color,
    val textSecondaryColor: Color,
    val isDefault: Boolean = false
)

object ArtworkPaletteManager {
    private val paletteCache = ConcurrentHashMap<String, DynamicPlayerTheme>()

    fun getCachedTheme(path: String, lastModified: Long, isDarkTheme: Boolean): DynamicPlayerTheme? {
        return paletteCache["$path:$lastModified:$isDarkTheme"]
    }

    suspend fun extractTheme(
        path: String,
        lastModified: Long,
        bitmap: Bitmap?,
        isDarkTheme: Boolean
    ): DynamicPlayerTheme = withContext(Dispatchers.IO) {
        val cacheKey = "$path:$lastModified:$isDarkTheme"
        paletteCache[cacheKey]?.let { return@withContext it }

        if (bitmap == null) {
            val defaultTheme = getDefaultTheme(isDarkTheme)
            paletteCache[cacheKey] = defaultTheme
            return@withContext defaultTheme
        }

        val theme = try {
            val palette = Palette.from(bitmap).maximumColorCount(32).generate()
            
            val dominant = palette.dominantSwatch
            val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: dominant
            val muted = palette.mutedSwatch ?: palette.darkMutedSwatch ?: palette.lightMutedSwatch ?: dominant
            val darkMuted = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: dominant
            val lightVibrant = palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: dominant

            if (dominant == null && vibrant == null && muted == null) {
                getDefaultTheme(isDarkTheme)
            } else {
                val baseVibrantColor = vibrant?.rgb?.let { Color(it) } ?: (dominant?.rgb?.let { Color(it) } ?: Color(0xFF4F46E5))
                val baseMutedColor = darkMuted?.rgb?.let { Color(it) } ?: (muted?.rgb?.let { Color(it) } ?: Color(0xFF0E121B))

                val bgTop = adjustColorForBackground(baseMutedColor, isDarkTheme, isTop = true)
                val bgBottom = adjustColorForBackground(baseVibrantColor, isDarkTheme, isTop = false)

                val accent = vibrant?.rgb?.let { Color(it) } ?: (dominant?.rgb?.let { Color(it) } ?: Color(0xFF0EA5E9))
                val secondaryAccent = lightVibrant?.rgb?.let { Color(it) } ?: accent

                val textPrimary = vibrant?.let { Color(it.bodyTextColor) } ?: (if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A))
                val textSecondary = muted?.let { Color(it.bodyTextColor) } ?: (if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569))

                DynamicPlayerTheme(
                    backgroundTop = bgTop,
                    backgroundBottom = bgBottom,
                    accentColor = accent,
                    secondaryAccentColor = secondaryAccent,
                    textColor = textPrimary,
                    textSecondaryColor = textSecondary,
                    isDefault = false
                )
            }
        } catch (_: Throwable) {
            getDefaultTheme(isDarkTheme)
        }

        paletteCache[cacheKey] = theme
        theme
    }

    fun getDefaultTheme(isDarkTheme: Boolean): DynamicPlayerTheme {
        return DynamicPlayerTheme(
            backgroundTop = if (isDarkTheme) Color(0xFF080A0F) else Color(0xFFF3F6FA),
            backgroundBottom = if (isDarkTheme) Color(0xFF0E121B) else Color(0xFFFFFFFF),
            accentColor = Color(0xFF4F46E5),
            secondaryAccentColor = Color(0xFF0EA5E9),
            textColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A),
            textSecondaryColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
            isDefault = true
        )
    }

    private fun adjustColorForBackground(color: Color, isDarkTheme: Boolean, isTop: Boolean): Color {
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsl)
        if (isDarkTheme) {
            hsl[1] = (hsl[1] * 0.5f).coerceIn(0.1f, 0.45f)
            hsl[2] = if (isTop) 0.07f else 0.13f
        } else {
            hsl[1] = (hsl[1] * 0.3f).coerceIn(0.05f, 0.25f)
            hsl[2] = if (isTop) 0.96f else 0.91f
        }
        return Color(android.graphics.Color.HSVToColor(hsl))
    }
}
