package com.music.wallpaper.renderer

import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * High-performance abstraction for the fluid ambient wallpaper rendering engine.
 * AGSL RuntimeShader (API 33+) with ultra-optimized noise math + Canvas fallback (API 26-32).
 */
interface ShaderRenderer {
    fun onSurfaceChanged(width: Int, height: Int)
    fun draw(canvas: Canvas)
    fun setColors(colors: FloatArray) // 20 floats: 5 colors × RGBA
    fun setSpeed(speed: Float)        // 0.1 – 2.0
    fun setIntensity(intensity: Float) // 0.3 – 1.5
    fun setGrainEnabled(enabled: Boolean)
    fun isAnimating(): Boolean
    fun release()

    companion object {
        private const val TAG = "ShaderRenderer"

        fun create(): ShaderRenderer {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    return AGSLShaderRenderer()
                } catch (e: Throwable) {
                    Log.w(TAG, "AGSLShaderRenderer initialization failed, falling back", e)
                }
            }
            return FallbackShaderRenderer()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AGSL Renderer — API 33+
// Ultra-optimized domain-warped noise field, 5-color bleeding, vignette, grain
// ─────────────────────────────────────────────────────────────────────────────

@RequiresApi(33)
class AGSLShaderRenderer : ShaderRenderer {

    private var shader: android.graphics.RuntimeShader? = null
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val fallbackRenderer = FallbackShaderRenderer()

    private var startTime = System.nanoTime()
    private var speed = 0.5f
    private var intensity = 0.8f
    private var grainEnabled = true

    private val colors = FloatArray(20) {
        val i = it / 4
        val c = it % 4
        if (c == 3) 1f
        else when (i) {
            0 -> 0.6f; 1 -> 0.4f; 2 -> 0.2f; 3 -> 0.7f; else -> 0.3f
        }
    }

    init {
        try {
            shader = android.graphics.RuntimeShader(AGSL_SOURCE)
        } catch (e: Throwable) {
            Log.e("AGSLShaderRenderer", "Failed to compile AGSL shader, using fallback", e)
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        shader?.setFloatUniform("uResolution", width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f))
        fallbackRenderer.onSurfaceChanged(width, height)
    }

    override fun draw(canvas: Canvas) {
        val s = shader
        if (s == null || !canvas.isHardwareAccelerated) {
            fallbackRenderer.draw(canvas)
            return
        }

        try {
            val w = if (surfaceWidth > 0) surfaceWidth.toFloat() else canvas.width.toFloat()
            val h = if (surfaceHeight > 0) surfaceHeight.toFloat() else canvas.height.toFloat()

            val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
            s.setFloatUniform("uResolution", w, h)
            s.setFloatUniform("uTime", elapsed)
            s.setFloatUniform("uSpeed", speed)
            s.setFloatUniform("uIntensity", intensity)
            s.setFloatUniform("uGrain", if (grainEnabled) 0.035f else 0f)

            for (i in 0 until 5) {
                val offset = i * 4
                s.setFloatUniform(
                    "uColor$i",
                    colors[offset], colors[offset + 1], colors[offset + 2], colors[offset + 3]
                )
            }

            shaderPaint.shader = s
            canvas.drawRect(0f, 0f, w, h, shaderPaint)
        } catch (e: Throwable) {
            fallbackRenderer.draw(canvas)
        }
    }

    override fun setColors(colors: FloatArray) {
        System.arraycopy(colors, 0, this.colors, 0, minOf(colors.size, 20))
        fallbackRenderer.setColors(colors)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.1f, 2.0f)
        fallbackRenderer.setSpeed(speed)
    }

    override fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.3f, 1.5f)
        fallbackRenderer.setIntensity(intensity)
    }

    override fun setGrainEnabled(enabled: Boolean) {
        this.grainEnabled = enabled
        fallbackRenderer.setGrainEnabled(enabled)
    }

    override fun isAnimating(): Boolean = true

    override fun release() {
        shader = null
        fallbackRenderer.release()
    }

    companion object {
        /**
         * Ultra-optimized AGSL shader:
         * Uses minimal ALU instructions and efficient 2-octave domain warping.
         */
        private const val AGSL_SOURCE = """
uniform float2 uResolution;
uniform float  uTime;
uniform float  uSpeed;
uniform float  uIntensity;
uniform float  uGrain;
uniform float4 uColor0;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float4 uColor3;
uniform float4 uColor4;

// ── Fast Hash ────────────────────────────────────────────────────────────
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// ── 2D Value Noise with Cubic Interpolation ──────────────────────────────
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// ── 2-Octave FBM ─────────────────────────────────────────────────────────
float fbm2(float2 p) {
    return noise(p) * 0.65 + noise(p * 2.0 + 1.6) * 0.35;
}

// ── Fast Screen Blend ────────────────────────────────────────────────────
float3 screenBlend(float3 a, float3 b, float w) {
    return mix(a, 1.0 - (1.0 - a) * (1.0 - b), w);
}

// ── Main Fragment Function ───────────────────────────────────────────────
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float t = uTime * uSpeed * 0.18;

    // Aspect-ratio correction
    float2 p = uv * 2.0;
    p.x *= uResolution.x / uResolution.y;

    // Single efficient domain warp
    float2 q = float2(
        fbm2(p + t * 0.6),
        fbm2(p + float2(3.7, 1.2) - t * 0.5)
    );

    float2 warpedP = p + 1.8 * q;

    // Primary and secondary noise fields
    float nA = fbm2(warpedP + t * 0.35);
    float nB = fbm2(warpedP * 1.4 + float2(4.3, 2.1) - t * 0.4);

    // Color distribution weights
    float w0 = smoothstep(0.15, 0.75, nA);
    float w1 = smoothstep(0.20, 0.80, nB);
    float w2 = smoothstep(0.25, 0.85, (nA + nB) * 0.5);
    float w3 = smoothstep(0.20, 0.70, nA * nB * 2.2);
    float w4 = smoothstep(0.15, 0.85, 1.0 - nA);

    // Dark base from dominant color
    float3 col = uColor0.rgb * 0.20;

    // Screen blend layering for luminous aurora bleed
    col = screenBlend(col, uColor0.rgb, w0 * 0.75);
    col = screenBlend(col, uColor1.rgb, w1 * 0.65);
    col = screenBlend(col, uColor2.rgb, w2 * 0.55);
    col = screenBlend(col, uColor3.rgb, w3 * 0.50);
    col = screenBlend(col, uColor4.rgb, w4 * 0.45);

    // Color intensity / saturation scaling
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(lum), col, uIntensity);

    // Radial vignette with edge contrast falloff
    float2 vc = uv - 0.5;
    float vignette = clamp(1.0 - dot(vc, vc) * 1.5, 0.35, 1.0);
    col *= vignette;

    // Subtle animated grain overlay
    if (uGrain > 0.001) {
        float grain = (hash(fragCoord + fract(uTime * 11.37)) - 0.5) * uGrain;
        col += float3(grain);
    }

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col), 1.0);
}
"""
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Fallback Renderer — API 26-32
// Layered radial gradients with animated drift, vignette, and grain overlay
// ─────────────────────────────────────────────────────────────────────────────

class FallbackShaderRenderer : ShaderRenderer {

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val startTime = System.nanoTime()
    private var speed = 0.5f
    private var intensity = 0.8f
    private var grainEnabled = true

    private val layerColors = intArrayOf(
        Color.argb(255, 120, 60, 160),
        Color.argb(255, 40, 100, 200),
        Color.argb(255, 20, 150, 140),
        Color.argb(255, 180, 70, 100),
        Color.argb(255, 70, 80, 160)
    )

    private val layerPhaseX = floatArrayOf(0.0f, 1.3f, 2.7f, 4.1f, 5.5f)
    private val layerPhaseY = floatArrayOf(0.8f, 2.1f, 3.5f, 4.9f, 0.3f)
    private val layerScale  = floatArrayOf(0.75f, 0.85f, 0.95f, 0.80f, 0.90f)
    private val layerSpeed  = floatArrayOf(0.12f, 0.15f, 0.20f, 0.18f, 0.10f)

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint().apply { alpha = 12 }
    private var grainBitmap: Bitmap? = null

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        generateGrainBitmap()
    }

    private fun generateGrainBitmap() {
        if (grainBitmap == null) {
            val size = 128
            grainBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).also { bmp ->
                val pixels = IntArray(size * size)
                val rng = java.util.Random()
                for (i in pixels.indices) {
                    val v = rng.nextInt(256)
                    pixels[i] = Color.argb(v, 255, 255, 255)
                }
                bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = if (surfaceWidth > 0) surfaceWidth.toFloat() else canvas.width.toFloat()
        val h = if (surfaceHeight > 0) surfaceHeight.toFloat() else canvas.height.toFloat()
        val maxDim = maxOf(w, h)

        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
        val t = elapsed * speed

        canvas.drawColor(Color.rgb(10, 10, 16))

        for (i in 0 until 5) {
            val cx = w * (0.5f + 0.35f * kotlin.math.sin(t * layerSpeed[i] + layerPhaseX[i]))
            val cy = h * (0.5f + 0.35f * kotlin.math.cos(t * layerSpeed[i] * 0.8f + layerPhaseY[i]))
            val radius = maxDim * layerScale[i]

            val baseColor = layerColors[i]
            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)
            val centerAlpha = (200 * intensity).toInt().coerceIn(0, 255)

            val gradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.argb(centerAlpha, r, g, b),
                    Color.argb((centerAlpha * 0.45f).toInt(), r, g, b),
                    Color.argb(0, r, g, b)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            gradientPaint.shader = gradient
            gradientPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            canvas.drawCircle(cx, cy, radius, gradientPaint)
            gradientPaint.xfermode = null
        }

        // Radial Vignette
        val vcx = w / 2f
        val vcy = h / 2f
        val vRadius = maxDim * 0.85f
        val vignetteGradient = RadialGradient(
            vcx, vcy, vRadius,
            intArrayOf(Color.TRANSPARENT, Color.argb(90, 0, 0, 0), Color.argb(210, 0, 0, 0)),
            floatArrayOf(0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        vignettePaint.shader = vignetteGradient
        canvas.drawRect(0f, 0f, w, h, vignettePaint)

        // Film Grain
        if (grainEnabled) {
            grainBitmap?.let { grain ->
                val grainShader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val grainMatrix = Matrix()
                grainMatrix.setTranslate(
                    (elapsed * 83f) % grain.width,
                    (elapsed * 53f) % grain.height
                )
                grainShader.setLocalMatrix(grainMatrix)
                grainPaint.shader = grainShader
                canvas.drawRect(0f, 0f, w, h, grainPaint)
                grainPaint.shader = null
            }
        }
    }

    override fun setColors(colors: FloatArray) {
        for (i in 0 until 5) {
            val offset = i * 4
            if (offset + 3 < colors.size) {
                layerColors[i] = Color.argb(
                    (colors[offset + 3] * 255).toInt().coerceIn(0, 255),
                    (colors[offset] * 255).toInt().coerceIn(0, 255),
                    (colors[offset + 1] * 255).toInt().coerceIn(0, 255),
                    (colors[offset + 2] * 255).toInt().coerceIn(0, 255)
                )
            }
        }
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.1f, 2.0f)
    }

    override fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.3f, 1.5f)
    }

    override fun setGrainEnabled(enabled: Boolean) {
        this.grainEnabled = enabled
    }

    override fun isAnimating(): Boolean = true

    override fun release() {
        grainBitmap?.recycle()
        grainBitmap = null
    }
}
