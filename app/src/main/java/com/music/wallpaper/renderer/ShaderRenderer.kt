package com.music.wallpaper.renderer

import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.music.wallpaper.models.WallpaperStyle

/**
 * Fluid ambient wallpaper rendering engine.
 * - API 33+: AGSL RuntimeShader with 4 visually distinct style presets
 * - API 26-32: Canvas gradient fallback (Aurora Drift + Midnight supported natively;
 *              Watercolor and Crystal fall back to Aurora Drift)
 *
 * Style audit notes (why the previous 5 were cut):
 * - Liquid Glass, Nebula Bloom, Silk Ribbon, Prism Mist all shared the same
 *   domain-warp FBM + screen-blend rendering path as Aurora Drift. At wallpaper
 *   scale their parameter differences (warp strength, star grid, axis bias,
 *   0.025 UV chromatic shift) were indistinguishable from the default.
 *   All four silently looked like Aurora Drift variants, not distinct styles.
 *
 * The 4 retained styles use genuinely different underlying techniques:
 *   0 AURORA_DRIFT      — domain-warped FBM noise, screen blend (original baseline)
 *   1 CRYSTAL_REFRACTION— (Faceted Light) 3D low-poly gradient mesh with directional lighting
 *   2 MIDNIGHT          — true AMOLED black base with organic luminous ribbons
 *   3 NEON_FILAMENTS    — true black AMOLED, noise gradient ridge glowing filaments
 */
interface ShaderRenderer {
    fun onSurfaceChanged(width: Int, height: Int)
    fun draw(canvas: Canvas)
    fun setColors(colors: FloatArray) // 20 floats: 5 colors × RGBA
    fun setSpeed(speed: Float)        // 0.1 – 2.0
    fun setIntensity(intensity: Float) // 0.3 – 1.5
    fun setStyle(style: WallpaperStyle)
    fun isAnimating(): Boolean
    fun release()

    companion object {
        private const val TAG = "ShaderRenderer"

        fun create(): ShaderRenderer {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    return AGSLShaderRenderer()
                } catch (e: Throwable) {
                    Log.w(TAG, "AGSLShaderRenderer init failed, using Canvas fallback", e)
                }
            }
            return FallbackShaderRenderer()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AGSL Renderer — API 33+
// 4 style presets using genuinely different rendering techniques
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
    private var style = WallpaperStyle.AURORA_DRIFT

    // 20 floats: 5 palette colors × RGBA
    private val colors = FloatArray(20) {
        val i = it / 4
        val c = it % 4
        if (c == 3) 1f
        else when (i) {
            0 -> 0.55f; 1 -> 0.35f; 2 -> 0.18f; 3 -> 0.65f; else -> 0.28f
        }
    }

    init {
        try {
            shader = android.graphics.RuntimeShader(AGSL_SOURCE)
        } catch (e: Throwable) {
            Log.e("AGSLShaderRenderer", "AGSL compile failed, falling back", e)
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
            s.setFloatUniform("uStyle", style.ordinal.toFloat())

            for (i in 0 until 5) {
                val offset = i * 4
                s.setFloatUniform("uColor$i",
                    colors[offset], colors[offset + 1], colors[offset + 2], colors[offset + 3])
            }

            shaderPaint.shader = s
            canvas.drawRect(0f, 0f, w, h, shaderPaint)
        } catch (e: Throwable) {
            Log.e("AGSLShaderRenderer", "Draw failed: ${e.message}")
            fallbackRenderer.draw(canvas)
        }
    }

    override fun setColors(colors: FloatArray) {
        System.arraycopy(colors, 0, this.colors, 0, minOf(colors.size, 20))
        fallbackRenderer.setColors(colors)
    }
    override fun setSpeed(speed: Float) { this.speed = speed.coerceIn(0.1f, 2.0f); fallbackRenderer.setSpeed(speed) }
    override fun setIntensity(intensity: Float) { this.intensity = intensity.coerceIn(0.3f, 1.5f); fallbackRenderer.setIntensity(intensity) }
    override fun setStyle(style: WallpaperStyle) { this.style = style; fallbackRenderer.setStyle(style) }
    override fun isAnimating(): Boolean = true
    override fun release() { shader = null; fallbackRenderer.release() }

    companion object {
        /**
         * 4-style AGSL shader:
         *
         * Style 0 – AURORA DRIFT
         *   Domain-warped FBM noise, screen blend on dark base, omnidirectional soft drift.
         *
         * Style 1 – FACETED LIGHT
         *   High-performance 3D low-poly gradient mesh with dynamic directional lighting.
         *   Flawless geometry defined purely by surface normals and light shading (no artificial lines).
         *
         * Style 2 – MIDNIGHT
         *   Pure AMOLED black background with organic, luminous ribbons of Aurora Drift.
         *
         * Style 3 – FILAMENTS
         *   True black base with thin animated glowing contour filaments.
         */
        private const val AGSL_SOURCE = """
uniform float2 uResolution;
uniform float  uTime;
uniform float  uSpeed;
uniform float  uIntensity;
uniform float  uStyle;
uniform float4 uColor0;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float4 uColor3;
uniform float4 uColor4;

// ── Utilities ─────────────────────────────────────────────────────────────

float hash(float2 p) {
    p = fract(p * float2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float2 hash2(float2 p) {
    return fract(sin(float2(dot(p, float2(127.1, 311.7)),
                            dot(p, float2(269.5, 183.3)))) * 43758.547);
}

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

float fbm2(float2 p) {
    return noise(p) * 0.65 + noise(p * 2.0 + 1.6) * 0.35;
}

// ── Faceted Light Mesh Helpers ────────────────────────────────────────────

float getMeshHeight(float2 g, float tVal) {
    float2 h = hash2(g + float2(5.3, 17.1));
    return noise(g * 0.6 + float2(tVal * 0.05, tVal * 0.04)) * 0.8
         + noise(g * 1.2 + float2(tVal * 0.03 + h.x * 3.0, tVal * 0.02)) * 0.2;
}

float2 getMeshVertex(float2 g, float tVal) {
    float2 h = hash2(g + float2(13.7, 31.9));
    float2 drift = 0.30 * sin(float2(
        h.x * 6.28318 + tVal * 0.08 + g.y * 0.7,
        h.y * 6.28318 + tVal * 0.06 + g.x * 0.8
    ));
    return g + 0.5 + (h - 0.5) * 0.65 + drift;
}

float3 getMeshBaseColor(float2 vPos, float2 totalGrid, float tVal, float intensity) {
    float2 normPos = vPos / totalGrid;
    
    float gradT = clamp(normPos.x * 0.4 + normPos.y * 0.6, 0.0, 1.0);
    
    float3 col;
    if (gradT < 0.25) col = mix(uColor0.rgb, uColor1.rgb, gradT * 4.0);
    else if (gradT < 0.50) col = mix(uColor1.rgb, uColor2.rgb, (gradT - 0.25) * 4.0);
    else if (gradT < 0.75) col = mix(uColor2.rgb, uColor3.rgb, (gradT - 0.50) * 4.0);
    else col = mix(uColor3.rgb, uColor4.rgb, (gradT - 0.75) * 4.0);

    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(lum), col, intensity);

    return clamp(col, 0.0, 1.0);
}

// ── Main ──────────────────────────────────────────────────────────────────

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float aspect = uResolution.x / uResolution.y;
    int style = int(uStyle + 0.5);
    float t = uTime * uSpeed;

    // ══════════════════════════════════════════════════════════════════════
    // STYLE 0: AURORA DRIFT
    // ══════════════════════════════════════════════════════════════════════
    if (style == 0) {
        float2 p = uv * 2.2;
        p.x *= aspect;
        float t0 = t * 0.18;

        float2 q = float2(fbm2(p + t0 * 0.6),
                          fbm2(p + float2(3.7, 1.2) - t0 * 0.5));
        float2 wp = p + 1.8 * q;

        float nA = fbm2(wp + t0 * 0.35);
        float nB = fbm2(wp * 1.4 + float2(4.3, 2.1) - t0 * 0.4);

        float3 col = uColor0.rgb * 0.20;
        col = 1.0 - (1.0 - col) * (1.0 - uColor0.rgb * smoothstep(0.15, 0.75, nA) * 0.75);
        col = 1.0 - (1.0 - col) * (1.0 - uColor1.rgb * smoothstep(0.20, 0.80, nB) * 0.65);
        col = 1.0 - (1.0 - col) * (1.0 - uColor2.rgb * smoothstep(0.25, 0.85, (nA + nB) * 0.5) * 0.55);
        col = 1.0 - (1.0 - col) * (1.0 - uColor3.rgb * smoothstep(0.20, 0.70, nA * nB * 2.2) * 0.50);
        col = 1.0 - (1.0 - col) * (1.0 - uColor4.rgb * smoothstep(0.15, 0.85, 1.0 - nA) * 0.45);

        float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
        col = mix(float3(lum), col, uIntensity);

        float2 vc = uv - 0.5;
        col *= clamp(1.0 - dot(vc, vc) * 1.5, 0.35, 1.0);
        return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // STYLE 1: FACETED LIGHT — Low-Poly 3D-Lit Gradient Mesh
    // ══════════════════════════════════════════════════════════════════════
    if (style == 1) {
        float2 totalGrid = float2(max(aspect * 4.5, 3.0), 6.5);
        float2 p = uv * totalGrid;
        float2 baseCell = floor(p);
        float t2 = t * 0.35;

        // Slowly orbiting directional light (unit vector rotating over ~70s)
        float3 lightDir = normalize(float3(
            cos(t2 * 0.045),
            sin(t2 * 0.035),
            0.65
        ));

        float3 meshColor = float3(0.0);
        bool found = false;

        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 0; dx++) {
                if (!found) {
                    float2 cell = baseCell + float2(float(dx), float(dy));

                    // 4 jittered vertex positions
                    float2 v00 = getMeshVertex(cell, t2);
                    float2 v10 = getMeshVertex(cell + float2(1.0, 0.0), t2);
                    float2 v01 = getMeshVertex(cell + float2(0.0, 1.0), t2);
                    float2 v11 = getMeshVertex(cell + float2(1.0, 1.0), t2);

                    // Pseudo-heights from slow noise field
                    float h00 = getMeshHeight(cell, t2);
                    float h10 = getMeshHeight(cell + float2(1.0, 0.0), t2);
                    float h01 = getMeshHeight(cell + float2(0.0, 1.0), t2);
                    float h11 = getMeshHeight(cell + float2(1.0, 1.0), t2);

                    // Base palette colors per vertex
                    float3 c00 = getMeshBaseColor(v00, totalGrid, t2, uIntensity);
                    float3 c10 = getMeshBaseColor(v10, totalGrid, t2, uIntensity);
                    float3 c01 = getMeshBaseColor(v01, totalGrid, t2, uIntensity);
                    float3 c11 = getMeshBaseColor(v11, totalGrid, t2, uIntensity);

                    bool diag = (hash(cell * 4.19 + float2(1.3, 7.1)) > 0.5);

                    // --- Triangle 1 ---
                    float2 A1 = v00; float hA1 = h00; float3 cA1 = c00;
                    float2 B1 = v10; float hB1 = h10; float3 cB1 = c10;
                    float2 C1 = diag ? v11 : v01;
                    float hC1 = diag ? h11 : h01;
                    float3 cC1 = diag ? c11 : c01;

                    float2 e0 = B1 - A1; float2 e1 = C1 - A1; float2 e2 = p - A1;
                    float d00T = dot(e0, e0); float d01T = dot(e0, e1); float d11T = dot(e1, e1);
                    float d20T = dot(e2, e0); float d21T = dot(e2, e1);
                    float denom = d00T * d11T - d01T * d01T;

                    if (abs(denom) > 0.00001) {
                        float bv = (d11T * d20T - d01T * d21T) / denom;
                        float bw = (d00T * d21T - d01T * d20T) / denom;
                        float bu = 1.0 - bv - bw;
                        if (bu >= -0.005 && bv >= -0.005 && bw >= -0.005) {
                            // Barycentric color
                            float3 triColor = clamp(bu, 0.0, 1.0) * cA1 + clamp(bv, 0.0, 1.0) * cB1 + clamp(bw, 0.0, 1.0) * cC1;

                            // Face normal from 3D positions (x,y,height)
                            float3 pA = float3(A1.x, A1.y, hA1 * 0.5);
                            float3 pB = float3(B1.x, B1.y, hB1 * 0.5);
                            float3 pC = float3(C1.x, C1.y, hC1 * 0.5);
                            float3 edgeAB = pB - pA;
                            float3 edgeAC = pC - pA;
                            float3 faceN = normalize(float3(
                                edgeAB.y * edgeAC.z - edgeAB.z * edgeAC.y,
                                edgeAB.z * edgeAC.x - edgeAB.x * edgeAC.z,
                                edgeAB.x * edgeAC.y - edgeAB.y * edgeAC.x
                            ));
                            if (faceN.z < 0.0) faceN = -faceN;

                            // Lighting: dot(normal, light), clamped for visible 3D effect
                            float lightFactor = clamp(dot(faceN, lightDir) * 0.55 + 0.65, 0.4, 1.3);
                            triColor *= lightFactor;

                            meshColor = triColor;
                            found = true;
                        }
                    }

                    // --- Triangle 2 ---
                    if (!found) {
                        float2 A2 = diag ? v00 : v10;
                        float hA2 = diag ? h00 : h10;
                        float3 cA2 = diag ? c00 : c10;
                        float2 B2 = v11; float hB2 = h11; float3 cB2 = c11;
                        float2 C2 = v01; float hC2 = h01; float3 cC2 = c01;

                        float2 e0B = B2 - A2; float2 e1B = C2 - A2; float2 e2B = p - A2;
                        float d00B = dot(e0B, e0B); float d01B = dot(e0B, e1B); float d11B = dot(e1B, e1B);
                        float d20B = dot(e2B, e0B); float d21B = dot(e2B, e1B);
                        float denomB = d00B * d11B - d01B * d01B;

                        if (abs(denomB) > 0.00001) {
                            float bvB = (d11B * d20B - d01B * d21B) / denomB;
                            float bwB = (d00B * d21B - d01B * d20B) / denomB;
                            float buB = 1.0 - bvB - bwB;
                            if (buB >= -0.005 && bvB >= -0.005 && bwB >= -0.005) {
                                float3 triColor = clamp(buB, 0.0, 1.0) * cA2 + clamp(bvB, 0.0, 1.0) * cB2 + clamp(bwB, 0.0, 1.0) * cC2;

                                float3 pA = float3(A2.x, A2.y, hA2 * 0.5);
                                float3 pB = float3(B2.x, B2.y, hB2 * 0.5);
                                float3 pC = float3(C2.x, C2.y, hC2 * 0.5);
                                float3 edgeAB = pB - pA;
                                float3 edgeAC = pC - pA;
                                float3 faceN = normalize(float3(
                                    edgeAB.y * edgeAC.z - edgeAB.z * edgeAC.y,
                                    edgeAB.z * edgeAC.x - edgeAB.x * edgeAC.z,
                                    edgeAB.x * edgeAC.y - edgeAB.y * edgeAC.x
                                ));
                                if (faceN.z < 0.0) faceN = -faceN;

                                float lightFactor = clamp(dot(faceN, lightDir) * 0.55 + 0.65, 0.4, 1.3);
                                triColor *= lightFactor;

                                meshColor = triColor;
                                found = true;
                            }
                        }
                    }
                }
            }
        }

        if (!found) {
            meshColor = getMeshBaseColor(p, totalGrid, t2, uIntensity);
        }

        return half4(half3(clamp(meshColor, 0.0, 1.0)), 1.0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // STYLE 2: MIDNIGHT — AMOLED Black + Luminous Floating Aurora Ribbons
    // ══════════════════════════════════════════════════════════════════════
    if (style == 2) {
        float3 baseBg = float3(0.0); // Strict AMOLED black (0% pixel power)

        float2 p = uv * 2.2;
        p.x *= aspect;
        float t3 = t * 0.18;

        // Exact Aurora Drift domain warping
        float2 q = float2(
            fbm2(p + t3 * 0.6),
            fbm2(p + float2(3.7, 1.2) - t3 * 0.5)
        );
        float2 wp = p + 1.8 * q;

        float nA = fbm2(wp + t3 * 0.35);
        float nB = fbm2(wp * 1.4 + float2(4.3, 2.1) - t3 * 0.4);

        // Fluid density calculation
        float density = fbm2(wp + float2(nA * 0.8, nB * 0.8));

        // High-pass threshold luminance mask: 0 outside ribbons, smooth transition
        float ribbonMask = smoothstep(0.35, 0.75, density);

        // Absolute AMOLED black if outside active ribbon
        if (ribbonMask <= 0.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        // Palette color interpolation ONLY inside active ribbon regions (ribbonMask > 0.0)
        float w1 = smoothstep(0.10, 0.65, nA);
        float w2 = smoothstep(0.15, 0.75, nB);
        float w3 = smoothstep(0.20, 0.80, (nA + nB) * 0.5);
        float w4 = smoothstep(0.15, 0.70, nA * nB * 2.0);

        float3 auroraColor = float3(0.0);
        auroraColor = 1.0 - (1.0 - auroraColor) * (1.0 - uColor1.rgb * w1 * 0.90);
        auroraColor = 1.0 - (1.0 - auroraColor) * (1.0 - uColor2.rgb * w2 * 0.80);
        auroraColor = 1.0 - (1.0 - auroraColor) * (1.0 - uColor3.rgb * w3 * 0.70);
        auroraColor = 1.0 - (1.0 - auroraColor) * (1.0 - uColor4.rgb * w4 * 0.65);

        // Scale intensity / saturation
        float lum = dot(auroraColor, float3(0.2126, 0.7152, 0.0722));
        auroraColor = mix(float3(lum), auroraColor, uIntensity);

        // Scale output color strictly by ribbonMask over baseBg
        float3 finalColor = mix(baseBg, auroraColor, ribbonMask);

        return half4(half3(clamp(finalColor, 0.0, 1.0)), 1.0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // STYLE 3: NEON FILAMENTS — Thin glowing contour filaments on true black
    // ══════════════════════════════════════════════════════════════════════
    if (style == 3) {
        float2 p = uv * 3.0;
        p.x *= aspect;
        float t4 = t * 0.12;

        // Sample noise at slightly offset positions for gradient estimation
        float n  = fbm2(p + t4 * 0.4);
        float nx = fbm2(p + float2(0.004, 0.0) + t4 * 0.4);
        float ny = fbm2(p + float2(0.0, 0.004) + t4 * 0.4);

        // Gradient magnitude — high at ridges/isoline edges
        float2 grad = float2(nx - n, ny - n) / 0.004;
        float gradMag = length(grad);

        // Multiple thin iso-contour bands at different noise levels
        // Each band picks a different palette color
        float3 col = float3(0.0); // true black base

        // Band 0
        float iso0 = abs(fract(n * 4.0 + t4 * 0.1) - 0.5);
        float line0 = smoothstep(0.06, 0.0, iso0) * gradMag * 0.7;
        col += uColor0.rgb * line0 * uIntensity;

        // Band 1 (offset phase)
        float iso1 = abs(fract(n * 4.0 + t4 * 0.08 + 0.5) - 0.5);
        float line1 = smoothstep(0.06, 0.0, iso1) * gradMag * 0.6;
        col += uColor1.rgb * line1 * uIntensity;

        // Band 2 (slower, different scale)
        float n2 = fbm2(p * 0.7 + float2(7.3, 2.9) + t4 * 0.25);
        float iso2 = abs(fract(n2 * 3.0 + t4 * 0.05) - 0.5);
        float line2 = smoothstep(0.055, 0.0, iso2) * 0.5;
        col += uColor2.rgb * line2 * uIntensity;

        // Band 3
        float iso3 = abs(fract(n2 * 3.0 + t4 * 0.04 + 0.33) - 0.5);
        float line3 = smoothstep(0.045, 0.0, iso3) * 0.45;
        col += uColor3.rgb * line3 * uIntensity;

        return half4(half3(clamp(col, 0.0, 0.92)), 1.0);
    }

    // Fallback for unknown style ordinal
    return half4(0.02, 0.02, 0.03, 1.0);
}
"""
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas Fallback Renderer — API 26-32
// Aurora Drift, Midnight, and Filaments rendered natively.
// Watercolor and Crystal fall back to Aurora Drift (acceptable per spec).
// ─────────────────────────────────────────────────────────────────────────────

class FallbackShaderRenderer : ShaderRenderer {

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val startTime = System.nanoTime()
    private var speed = 0.5f
    private var intensity = 0.8f
    private var style = WallpaperStyle.AURORA_DRIFT

    private val layerColors = intArrayOf(
        Color.argb(255, 120, 60, 160),
        Color.argb(255, 40, 100, 200),
        Color.argb(255, 20, 150, 140),
        Color.argb(255, 180, 70, 100),
        Color.argb(255, 70, 80, 160)
    )

    private val phaseX = floatArrayOf(0.0f, 1.3f, 2.7f, 4.1f, 5.5f)
    private val phaseY = floatArrayOf(0.8f, 2.1f, 3.5f, 4.9f, 0.3f)
    private val layerScale = floatArrayOf(0.75f, 0.85f, 0.95f, 0.80f, 0.90f)
    private val layerSpd = floatArrayOf(0.12f, 0.15f, 0.20f, 0.18f, 0.10f)

    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint().apply { alpha = 10 }
    private var grainBitmap: Bitmap? = null

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        ensureGrainBitmap()
    }

    private fun ensureGrainBitmap() {
        if (grainBitmap == null || grainBitmap!!.isRecycled) {
            val size = 128
            grainBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).also { bmp ->
                val pixels = IntArray(size * size)
                val rng = java.util.Random()
                for (i in pixels.indices) pixels[i] = Color.argb(rng.nextInt(256), 255, 255, 255)
                bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = if (surfaceWidth > 0) surfaceWidth.toFloat() else canvas.width.toFloat()
        val h = if (surfaceHeight > 0) surfaceHeight.toFloat() else canvas.height.toFloat()
        val maxDim = maxOf(w, h)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f

        when (style) {
            WallpaperStyle.MIDNIGHT -> drawMidnight(canvas, w, h, elapsed)
            WallpaperStyle.NEON_FILAMENTS -> drawFilaments(canvas, w, h, elapsed)
            // Faceted Light falls back to Aurora Drift on old devices
            else -> drawAuroraDrift(canvas, w, h, maxDim, elapsed)
        }
    }

    private fun drawAuroraDrift(canvas: Canvas, w: Float, h: Float, maxDim: Float, elapsed: Float) {
        val t = elapsed * speed
        canvas.drawColor(Color.rgb(8, 8, 12))

        for (i in 0 until 5) {
            val cx = w * (0.5f + 0.35f * kotlin.math.sin(t * layerSpd[i] + phaseX[i]))
            val cy = h * (0.5f + 0.35f * kotlin.math.cos(t * layerSpd[i] * 0.8f + phaseY[i]))
            val radius = maxDim * layerScale[i]
            val bc = layerColors[i]
            val r = Color.red(bc); val g = Color.green(bc); val b = Color.blue(bc)
            val ca = (200 * intensity).toInt().coerceIn(0, 255)

            val grad = RadialGradient(cx, cy, radius,
                intArrayOf(Color.argb(ca, r, g, b), Color.argb((ca * 0.45f).toInt(), r, g, b), Color.argb(0, r, g, b)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            gradPaint.shader = grad
            gradPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            canvas.drawCircle(cx, cy, radius, gradPaint)
            gradPaint.xfermode = null
        }

        // Vignette
        val vigGrad = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.85f,
            intArrayOf(Color.TRANSPARENT, Color.argb(80, 0, 0, 0), Color.argb(200, 0, 0, 0)),
            floatArrayOf(0.4f, 0.75f, 1f), Shader.TileMode.CLAMP)
        vigPaint.shader = vigGrad
        canvas.drawRect(0f, 0f, w, h, vigPaint)
    }

    private fun drawMidnight(canvas: Canvas, w: Float, h: Float, elapsed: Float) {
        // True black base — AMOLED friendly
        canvas.drawColor(Color.BLACK)

        // Same Aurora Drift motion, but radically reduced opacity.
        // Only the brightest peaks of the radial gradient layers break through.
        val t = elapsed * speed
        val maxDim = maxOf(w, h)

        for (i in 0 until 5) {
            val cx = w * (0.5f + 0.35f * kotlin.math.sin(t * layerSpd[i] + phaseX[i]))
            val cy = h * (0.5f + 0.35f * kotlin.math.cos(t * layerSpd[i] * 0.8f + phaseY[i]))
            val radius = maxDim * layerScale[i] * 0.6f // tighter radius
            val bc = layerColors[i]
            val r = Color.red(bc); val g = Color.green(bc); val b = Color.blue(bc)
            // Much lower alpha — only ~15-25% of what Aurora Drift uses
            val ca = (50 * intensity).toInt().coerceIn(0, 80)

            val grad = RadialGradient(cx, cy, radius,
                intArrayOf(Color.argb(ca, r, g, b), Color.argb((ca * 0.3f).toInt(), r, g, b), Color.argb(0, r, g, b)),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            gradPaint.shader = grad
            gradPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            canvas.drawCircle(cx, cy, radius, gradPaint)
            gradPaint.xfermode = null
        }
        // No vignette, no grain — keep AMOLED blacks clean
    }

    private fun drawFilaments(canvas: Canvas, w: Float, h: Float, elapsed: Float) {
        // True black base
        canvas.drawColor(Color.BLACK)

        // Draw flowing thin isoline filaments on black
        val t = elapsed * speed * 0.08f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // Draw 4 sinusoidal line paths per color
        for (ci in 0 until 4) {
            val lc = layerColors[ci]
            val alpha = (120 * intensity).toInt().coerceIn(0, 200)
            paint.color = Color.argb(alpha, Color.red(lc), Color.green(lc), Color.blue(lc))
            val path = Path()
            val phase = phaseX[ci] + t * (0.3f + ci * 0.07f)
            val freq = 0.008f + ci * 0.002f
            val amp = h * (0.10f + ci * 0.04f)
            val yBase = h * (0.25f + ci * 0.15f)
            path.moveTo(0f, yBase + amp * kotlin.math.sin(phase).toFloat())
            for (x in 1..w.toInt() step 4) {
                val xf = x.toFloat()
                val y = yBase + amp * kotlin.math.sin((xf * freq + phase).toDouble()).toFloat()
                path.lineTo(xf, y)
            }
            canvas.drawPath(path, paint)
        }
    }



    override fun setColors(colors: FloatArray) {
        for (i in 0 until 5) {
            val o = i * 4
            if (o + 3 < colors.size) {
                layerColors[i] = Color.argb(
                    (colors[o + 3] * 255).toInt().coerceIn(0, 255),
                    (colors[o] * 255).toInt().coerceIn(0, 255),
                    (colors[o + 1] * 255).toInt().coerceIn(0, 255),
                    (colors[o + 2] * 255).toInt().coerceIn(0, 255)
                )
            }
        }
    }

    override fun setSpeed(speed: Float) { this.speed = speed.coerceIn(0.1f, 2.0f) }
    override fun setIntensity(intensity: Float) { this.intensity = intensity.coerceIn(0.3f, 1.5f) }
    override fun setStyle(style: WallpaperStyle) { this.style = style }
    override fun isAnimating(): Boolean = true

    override fun release() {
    }
}
