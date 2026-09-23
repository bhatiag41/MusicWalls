package com.music.wallpaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.music.wallpaper.R

// ─── Radius Design Tokens ────────────────────────────────────────────────────
object Radius {
    val xs   = 8.dp    // chips, small pills
    val sm   = 12.dp   // buttons, input fields, slider tracks
    val md   = 16.dp   // cards, banners, dropdowns
    val lg   = 24.dp   // bottom sheet top-corner surfaces
    val pill = 999.dp  // primary CTA only ("Set as Wallpaper")
}

// ─── Accent Color Helper ──────────────────────────────────────────────────────
/**
 * Converts a raw extracted album-art color into a soft, muted pastel accent
 * that still visibly shifts with the music but never neon/over-saturated.
 *
 * Clamps HSL:
 *   Saturation → 35 – 55 %
 *   Lightness  → 55 – 70 %
 */
fun toPremiumAccent(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    // Clamp saturation to 0.35–0.55
    hsl[1] = hsl[1].coerceIn(0.35f, 0.55f)
    // Clamp lightness to 0.55–0.70
    hsl[2] = hsl[2].coerceIn(0.55f, 0.70f)
    return Color(ColorUtils.HSLToColor(hsl))
}

// Native Android Dark Palette
val DarkBackground = Color(0xFF0C0C0F)
val DarkSurface = Color(0xFF141418)
val DarkSurfaceVariant = Color(0xFF1C1C22)
val DarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)

val TextPrimary = Color(0xFFF2F2F5)
val TextSecondary = Color(0xFF9090A0)
val TextMuted = Color(0xFF656577)

data class AppThemeColors(
    val accent: Color = Color(0xFF8B5CF6),
    val background: Color = DarkBackground,
    val surface: Color = DarkSurface,
    val surfaceVariant: Color = DarkSurfaceVariant,
    val border: Color = DarkBorder,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted
)

val LocalAppThemeColors = staticCompositionLocalOf { AppThemeColors() }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

private val GoogleFontsProvider = androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val InterFont = androidx.compose.ui.text.googlefonts.GoogleFont("Inter")

private val InterFontFamily = FontFamily(
    androidx.compose.ui.text.googlefonts.Font(
        googleFont = InterFont,
        fontProvider = GoogleFontsProvider,
        weight = FontWeight.Normal
    ),
    androidx.compose.ui.text.googlefonts.Font(
        googleFont = InterFont,
        fontProvider = GoogleFontsProvider,
        weight = FontWeight.Medium
    ),
    androidx.compose.ui.text.googlefonts.Font(
        googleFont = InterFont,
        fontProvider = GoogleFontsProvider,
        weight = FontWeight.SemiBold
    ),
    androidx.compose.ui.text.googlefonts.Font(
        googleFont = InterFont,
        fontProvider = GoogleFontsProvider,
        weight = FontWeight.Bold
    )
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
        color = TextMuted
    )
)

@Composable
fun GlassTheme(
    accentColor: Color = Color(0xFF8B5CF6),
    content: @Composable () -> Unit
) {
    val themeColors = AppThemeColors(
        accent = accentColor
    )

    val dynamicColorScheme = DarkColorScheme.copy(
        primary = accentColor,
        secondary = accentColor
    )

    CompositionLocalProvider(LocalAppThemeColors provides themeColors) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
