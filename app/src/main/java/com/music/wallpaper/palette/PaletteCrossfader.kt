package com.music.wallpaper.palette

import android.graphics.Color
import kotlin.math.pow

/**
 * Manages smooth crossfading between color palettes over ~2.5 seconds.
 * Interpolation is done in OKLab color space for perceptually uniform transitions
 * that don't go muddy/gray through intermediate colors.
 *
 * Thread-safe: setTargetPalette() can be called from any thread;
 * update() should be called from the render thread each frame.
 */
class PaletteCrossfader {

    companion object {
        private const val TRANSITION_DURATION_S = 2.5f
        private const val COLOR_COUNT = 5
        private const val FLOAT_COUNT = COLOR_COUNT * 4 // 5 colors × RGBA
    }

    // Current displayed colors (what the shader sees)
    private val currentColors = FloatArray(FLOAT_COUNT) { if (it % 4 == 3) 1f else 0.1f }

    // Source colors (where we're fading from)
    private val sourceColors = FloatArray(FLOAT_COUNT) { if (it % 4 == 3) 1f else 0.1f }

    // Target colors (where we're fading to)
    private val targetColors = FloatArray(FLOAT_COUNT) { if (it % 4 == 3) 1f else 0.1f }

    private var transitionProgress = 1f // 1.0 = complete, 0.0 = just started
    private var initialized = false

    /**
     * Set a new target palette. Begins a 2.5s crossfade from current colors.
     * If called mid-transition, the current interpolated position becomes the new source.
     */
    @Synchronized
    fun setTargetPalette(colors: FloatArray) {
        if (colors.size < FLOAT_COUNT) return

        if (!initialized) {
            // First palette: snap immediately
            System.arraycopy(colors, 0, currentColors, 0, FLOAT_COUNT)
            System.arraycopy(colors, 0, sourceColors, 0, FLOAT_COUNT)
            System.arraycopy(colors, 0, targetColors, 0, FLOAT_COUNT)
            transitionProgress = 1f
            initialized = true
            return
        }

        // Snapshot current interpolated state as the new source
        System.arraycopy(currentColors, 0, sourceColors, 0, FLOAT_COUNT)
        System.arraycopy(colors, 0, targetColors, 0, FLOAT_COUNT)
        transitionProgress = 0f
    }

    /**
     * Advance the crossfade by deltaTime seconds.
     * Returns the current interpolated color array (20 floats).
     */
    @Synchronized
    fun update(deltaTimeSeconds: Float): FloatArray {
        if (transitionProgress >= 1f) return currentColors

        transitionProgress += deltaTimeSeconds / TRANSITION_DURATION_S
        if (transitionProgress > 1f) transitionProgress = 1f

        // Ease in-out for smooth acceleration/deceleration
        val t = easeInOutCubic(transitionProgress)

        // Interpolate each color in OKLab space
        for (i in 0 until COLOR_COUNT) {
            val offset = i * 4
            interpolateOKLab(
                sourceColors, offset,
                targetColors, offset,
                t,
                currentColors, offset
            )
        }

        return currentColors
    }

    /**
     * Get the current colors without advancing time. Thread-safe snapshot.
     */
    @Synchronized
    fun getCurrentColors(): FloatArray = currentColors.copyOf()

    /**
     * Whether a transition is currently in progress.
     */
    @Synchronized
    fun isTransitioning(): Boolean = transitionProgress < 1f

    // ── OKLab interpolation ──────────────────────────────────────────────────

    /**
     * Interpolates between two RGBA colors in OKLab space.
     * OKLab provides perceptually uniform interpolation — no muddy midpoints.
     */
    private fun interpolateOKLab(
        src: FloatArray, srcOff: Int,
        dst: FloatArray, dstOff: Int,
        t: Float,
        out: FloatArray, outOff: Int
    ) {
        // Convert linear RGB to OKLab
        val srcLab = linearRgbToOKLab(src[srcOff], src[srcOff + 1], src[srcOff + 2])
        val dstLab = linearRgbToOKLab(dst[dstOff], dst[dstOff + 1], dst[dstOff + 2])

        // Lerp in OKLab space
        val L = srcLab[0] + (dstLab[0] - srcLab[0]) * t
        val a = srcLab[1] + (dstLab[1] - srcLab[1]) * t
        val b = srcLab[2] + (dstLab[2] - srcLab[2]) * t

        // Convert back to linear RGB
        val rgb = oklabToLinearRgb(L, a, b)
        out[outOff + 0] = rgb[0].coerceIn(0f, 1f)
        out[outOff + 1] = rgb[1].coerceIn(0f, 1f)
        out[outOff + 2] = rgb[2].coerceIn(0f, 1f)

        // Lerp alpha linearly
        out[outOff + 3] = src[srcOff + 3] + (dst[dstOff + 3] - src[srcOff + 3]) * t
    }

    /**
     * sRGB (0..1) → linear RGB → OKLab
     */
    private fun linearRgbToOKLab(r: Float, g: Float, b: Float): FloatArray {
        // sRGB to linear
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)

        // Linear RGB to LMS (cone responses)
        val l = 0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb
        val m = 0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb
        val s = 0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb

        // LMS to OKLab via cube root
        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)

        return floatArrayOf(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        )
    }

    /**
     * OKLab → linear RGB → sRGB (0..1)
     */
    private fun oklabToLinearRgb(L: Float, a: Float, b: Float): FloatArray {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val lr = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val lg = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val lb = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

        return floatArrayOf(
            linearToSrgb(lr),
            linearToSrgb(lg),
            linearToSrgb(lb)
        )
    }

    private fun srgbToLinear(x: Float): Float {
        return if (x <= 0.04045f) x / 12.92f
        else ((x + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(x: Float): Float {
        return if (x <= 0.0031308f) 12.92f * x
        else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    private fun cbrt(x: Float): Float {
        return if (x >= 0f) x.pow(1f / 3f)
        else -(-x).pow(1f / 3f)
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).pow(3f) / 2f
    }
}
