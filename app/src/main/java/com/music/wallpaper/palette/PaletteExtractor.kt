package com.music.wallpaper.palette

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.music.wallpaper.models.ColorPalette

/**
 * Extracts 5 colors from album art and returns them as shader-ready float arrays.
 * Includes ugly-palette guarding: if extracted colors are too close in hue/lightness,
 * saturation and spread are boosted to prevent flat gray wallpapers.
 */
object PaletteExtractor {

    /**
     * Extracts 5 colors from the bitmap and returns 20 floats (5 × RGBA, values 0..1).
     * Thread-safe, can be called from any thread.
     */
    @JvmStatic
    fun extract(bitmap: Bitmap?): FloatArray {
        if (bitmap == null) return defaultColors()

        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val raw = extractSlots(palette)
            val distinct = ensureDistinct(raw)
            val guarded = guardUglyPalette(distinct)
            colorsToFloatArray(guarded)
        } catch (e: Exception) {
            defaultColors()
        }
    }

    /**
     * Extracts 5 colors from the bitmap and returns a ColorPalette instance.
     */
    @JvmStatic
    fun extractPalette(bitmap: Bitmap?): ColorPalette {
        if (bitmap == null) return ColorPalette.getDefaultPalette()
        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val raw = extractSlots(palette)
            val distinct = ensureDistinct(raw)
            val guarded = guardUglyPalette(distinct)
            ColorPalette(guarded)
        } catch (e: Exception) {
            ColorPalette.getDefaultPalette()
        }
    }

    /**
     * Extracts 5 colors from a Palette using the priority slots.
     */
    private fun extractSlots(palette: Palette): List<Int> {
        val candidates = mutableListOf<Int>()

        // Priority order: dominant, vibrant, muted, darkVibrant, lightVibrant
        palette.dominantSwatch?.rgb?.let { candidates.add(it) }
        palette.vibrantSwatch?.rgb?.let { candidates.add(it) }
        palette.mutedSwatch?.rgb?.let { candidates.add(it) }
        palette.darkVibrantSwatch?.rgb?.let { candidates.add(it) }
        palette.lightVibrantSwatch?.rgb?.let { candidates.add(it) }

        // Fill from remaining swatches if needed
        if (candidates.size < 5) {
            palette.darkMutedSwatch?.rgb?.let { candidates.add(it) }
            palette.lightMutedSwatch?.rgb?.let { candidates.add(it) }

            // Population-sorted remaining swatches
            for (swatch in palette.swatches.sortedByDescending { it.population }) {
                if (candidates.size >= 7) break
                if (swatch.rgb !in candidates) {
                    candidates.add(swatch.rgb)
                }
            }
        }

        // Filter black/white
        val filtered = candidates.filter { c ->
            c != Color.BLACK && c != Color.WHITE && Color.alpha(c) > 200
        }

        return if (filtered.size >= 5) filtered.take(5)
        else padToFive(filtered)
    }

    /**
     * Ensure at least 5 distinct colors by generating hue-shifted variants.
     */
    private fun padToFive(colors: List<Int>): List<Int> {
        val result = colors.toMutableList()
        val base = if (result.isNotEmpty()) result[0] else Color.rgb(60, 80, 120)

        var shiftIndex = 1
        while (result.size < 5) {
            val hsv = FloatArray(3)
            Color.colorToHSV(base, hsv)
            hsv[0] = (hsv[0] + 60f * shiftIndex) % 360f
            hsv[1] = (hsv[1] * 0.8f).coerceIn(0.3f, 1f)
            hsv[2] = (hsv[2] + 0.1f * shiftIndex).coerceIn(0.3f, 0.9f)
            result.add(Color.HSVToColor(hsv))
            shiftIndex++
        }
        return result
    }

    /**
     * Remove colors that are too similar (Euclidean distance in RGB < 35).
     * Replace duplicates with hue-shifted variants.
     */
    private fun ensureDistinct(colors: List<Int>): List<Int> {
        val result = mutableListOf(colors[0])

        for (i in 1 until colors.size) {
            val c = colors[i]
            val tooClose = result.any { existing -> colorDistance(c, existing) < 35.0 }
            if (!tooClose) {
                result.add(c)
            } else {
                // Generate a shifted variant
                val hsv = FloatArray(3)
                Color.colorToHSV(c, hsv)
                hsv[0] = (hsv[0] + 40f + 20f * i) % 360f
                hsv[1] = (hsv[1] + 0.15f).coerceIn(0.3f, 1f)
                result.add(Color.HSVToColor(hsv))
            }
        }

        return padToFive(result)
    }

    /**
     * Guard against ugly palettes: monochrome album art, very dark, or very desaturated.
     *
     * If hue spread < 30°: boost saturation by 20% and spread hues ±15° from centroid.
     * If lightness range < 0.15: push darks darker, lights lighter.
     */
    private fun guardUglyPalette(colors: List<Int>): List<Int> {
        val hsvList = Array(colors.size) { FloatArray(3).also { hsv -> Color.colorToHSV(colors[it], hsv) } }

        // Calculate hue spread
        val hues = hsvList.map { it[0] }
        val hueSpread = hueRange(hues)
        val avgSat = hsvList.map { it[1] }.average().toFloat()
        val values = hsvList.map { it[2] }
        val valueRange = (values.maxOrNull() ?: 0.5f) - (values.minOrNull() ?: 0.5f)

        var modified = false

        // If all hues are clustered within 30°, spread them out
        if (hueSpread < 30f) {
            val centroidHue = hueCircularMean(hues)
            for (i in hsvList.indices) {
                val offset = (i - 2) * 15f // -30, -15, 0, +15, +30
                hsvList[i][0] = (centroidHue + offset + 360f) % 360f
            }
            modified = true
        }

        // If saturation is too low, boost it
        if (avgSat < 0.35f) {
            for (hsv in hsvList) {
                hsv[1] = (hsv[1] + 0.2f).coerceIn(0.3f, 1f)
            }
            modified = true
        }

        // If value range is too narrow, expand it
        if (valueRange < 0.15f) {
            val avgValue = values.average().toFloat()
            for (i in hsvList.indices) {
                val offset = (i.toFloat() / (hsvList.size - 1)) - 0.5f // -0.5 to 0.5
                hsvList[i][2] = (avgValue + offset * 0.4f).coerceIn(0.2f, 0.95f)
            }
            modified = true
        }

        return if (modified) {
            hsvList.map { Color.HSVToColor(it) }
        } else {
            colors
        }
    }

    /**
     * Calculate the angular range of hues (accounting for circular wraparound).
     */
    private fun hueRange(hues: List<Float>): Float {
        if (hues.size < 2) return 0f
        val sorted = hues.sorted()
        var maxGap = 0f
        for (i in 1 until sorted.size) {
            maxGap = maxOf(maxGap, sorted[i] - sorted[i - 1])
        }
        maxGap = maxOf(maxGap, 360f - sorted.last() + sorted.first())
        return 360f - maxGap // The span covered by the hues
    }

    /**
     * Circular mean of angles in degrees.
     */
    private fun hueCircularMean(hues: List<Float>): Float {
        var sinSum = 0.0
        var cosSum = 0.0
        for (h in hues) {
            val rad = Math.toRadians(h.toDouble())
            sinSum += kotlin.math.sin(rad)
            cosSum += kotlin.math.cos(rad)
        }
        val meanRad = kotlin.math.atan2(sinSum, cosSum)
        return ((Math.toDegrees(meanRad) + 360) % 360).toFloat()
    }

    private fun colorDistance(c1: Int, c2: Int): Double {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    /**
     * Convert 5 ARGB ints to 20 floats (RGBA, 0..1 each).
     */
    private fun colorsToFloatArray(colors: List<Int>): FloatArray {
        val result = FloatArray(20)
        for (i in 0 until minOf(5, colors.size)) {
            val c = colors[i]
            result[i * 4 + 0] = Color.red(c) / 255f
            result[i * 4 + 1] = Color.green(c) / 255f
            result[i * 4 + 2] = Color.blue(c) / 255f
            result[i * 4 + 3] = Color.alpha(c) / 255f
        }
        return result
    }

    private fun defaultColors(): FloatArray {
        val defaults = listOf(
            Color.rgb(80, 40, 90),    // deep purple
            Color.rgb(40, 80, 140),   // ocean blue
            Color.rgb(30, 110, 100),  // teal
            Color.rgb(120, 50, 70),   // muted rose
            Color.rgb(50, 60, 110)    // slate blue
        )
        return colorsToFloatArray(defaults)
    }
}
