package com.music.wallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Color definitions for dark-mode glass UI
val DarkBackground = Color(0xFF08080B)
val DarkSurface = Color(0xFF101015)
val GlassSurface = Color(0xFF16161E).copy(alpha = 0.65f)
val GlassSurfaceHighlight = Color(0xFF222230).copy(alpha = 0.5f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val GlassBorderFocused = Color(0xFFFFFFFF).copy(alpha = 0.22f)

val TextPrimary = Color(0xFFF0F0F5)
val TextSecondary = Color(0xFF9494A8)
val TextMuted = Color(0xFF5E5E72)

data class GlassPalette(
    val accent: Color = Color(0xFF818CF8),
    val accentSecondary: Color = Color(0xFF38BDF8),
    val background: Color = DarkBackground,
    val surface: Color = DarkSurface,
    val glassCard: Color = GlassSurface,
    val glassBorder: Color = GlassBorder,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary
)

val LocalGlassPalette = staticCompositionLocalOf { GlassPalette() }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E1E28),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = TextMuted
    )
)

@Composable
fun GlassTheme(
    accentColor: Color = Color(0xFF818CF8),
    content: @Composable () -> Unit
) {
    val glassPalette = GlassPalette(
        accent = accentColor,
        accentSecondary = accentColor.copy(alpha = 0.7f)
    )

    val dynamicColorScheme = DarkColorScheme.copy(
        primary = accentColor,
        secondary = accentColor.copy(alpha = 0.8f)
    )

    CompositionLocalProvider(LocalGlassPalette provides glassPalette) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
