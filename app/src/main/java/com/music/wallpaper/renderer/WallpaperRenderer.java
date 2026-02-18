package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Battery-optimized wallpaper renderer.
 * Key optimizations:
 *  - Adaptive FPS: reports isAnimating() so caller can slow down when static
 *  - Gradient caching: RadialGradient only recreated when color/position changes
 *  - 5 blobs (was 7) — 28% fewer draw calls, visually identical
 *  - No Log calls in hot draw path
 */
public class WallpaperRenderer {

    private static final int BLOB_COUNT = 5;

    private List<ColorBlob> colorBlobs;
    private int width = 0;
    private int height = 0;
    private ColorPalette currentPalette;

    // Settings-driven parameters
    private float speedFactor     = 1.0f;
    private float intensityFactor = 0.8f;
    private float blurFactor      = 0.3f;

    private final Paint blobPaint;
    private final Random random = new Random();

    // True while any blob is still transitioning color
    private boolean transitioning = false;

    public WallpaperRenderer() {
        colorBlobs = new ArrayList<>();
        blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blobPaint.setDither(true);
        blobPaint.setStyle(Paint.Style.FILL);
    }

    /** Returns true if blobs are still mid-transition (caller should keep 30fps). */
    public boolean isAnimating() {
        return transitioning;
    }

    public void setSettings(WallpaperSettings settings) {
        if (settings == null) return;
        speedFactor     = settings.getAnimationSpeedFactor();
        intensityFactor = settings.getColorIntensityFactor();
        blurFactor      = settings.getBlurAmount() / 100.0f;
        if (width > 0 && height > 0) initializeBlobs();
    }

    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
        initializeBlobs();
    }

    public void setColorPalette(ColorPalette palette) {
        this.currentPalette = palette;
        if (colorBlobs.isEmpty() || width <= 0) return;

        List<Integer> colors = (palette != null) ? palette.getAllColors() : new ArrayList<>();
        if (colors.isEmpty()) colors = getDefaultColors();

        for (int i = 0; i < colorBlobs.size(); i++) {
            int newTarget = colors.get(i % colors.size());
            if (colorBlobs.get(i).targetColor != newTarget) {
                colorBlobs.get(i).targetColor = newTarget;
                transitioning = true;
            }
        }
    }

    private void initializeBlobs() {
        if (width <= 0 || height <= 0) return;
        colorBlobs.clear();

        List<Integer> colors;
        if (currentPalette != null && !currentPalette.getAllColors().isEmpty()) {
            colors = currentPalette.getAllColors();
        } else {
            colors = getDefaultColors();
        }

        float baseSize      = Math.min(width, height);
        float radiusScale   = 0.5f + blurFactor;           // 0.5–1.5
        float baseVelocity  = 0.001f * speedFactor;

        for (int i = 0; i < BLOB_COUNT; i++) {
            ColorBlob blob = new ColorBlob();
            blob.x  = (random.nextFloat() * 0.8f) + 0.1f;
            blob.y  = (random.nextFloat() * 0.8f) + 0.1f;
            blob.vx = (random.nextFloat() - 0.5f) * baseVelocity * 2;
            blob.vy = (random.nextFloat() - 0.5f) * baseVelocity * 2;
            blob.radius = baseSize * (0.5f + random.nextFloat() * 0.4f) * radiusScale;

            int color = colors.get(i % colors.size());
            blob.color       = color;
            blob.targetColor = color;
            blob.gradient    = null; // force first build
            blob.lastGradientColor = -1;
            blob.lastGradientX = -1;
            blob.lastGradientY = -1;

            colorBlobs.add(blob);
        }
        transitioning = false;
    }

    private List<Integer> getDefaultColors() {
        List<Integer> d = new ArrayList<>();
        d.add(Color.parseColor("#B23A6F"));
        d.add(Color.parseColor("#7A3FA0"));
        d.add(Color.parseColor("#2F4FA8"));
        d.add(Color.parseColor("#1E7F86"));
        d.add(Color.parseColor("#9C7A1E"));
        return d;
    }

    public void draw(Canvas canvas) {
        if (canvas == null) return;

        canvas.drawColor(Color.BLACK);

        if (width <= 0 || height <= 0) {
            width  = canvas.getWidth();
            height = canvas.getHeight();
            initializeBlobs();
        }

        if (colorBlobs.isEmpty()) {
            initializeBlobs();
            if (colorBlobs.isEmpty()) {
                canvas.drawColor(Color.DKGRAY);
                return;
            }
        }

        boolean anyTransitioning = false;

        for (ColorBlob blob : colorBlobs) {
            updateBlobPosition(blob);
            boolean stillMoving = updateBlobColor(blob);
            if (stillMoving) anyTransitioning = true;

            float px = blob.x * width;
            float py = blob.y * height;
            if (blob.radius <= 1f) blob.radius = 100f;

            // Rebuild gradient only when color or position changed meaningfully
            boolean colorChanged    = blob.color != blob.lastGradientColor;
            boolean positionChanged = Math.abs(px - blob.lastGradientX) > 2f
                                   || Math.abs(py - blob.lastGradientY) > 2f;

            if (blob.gradient == null || colorChanged || positionChanged) {
                int centerAlpha = (int) (255 * intensityFactor);
                int midAlpha    = (int) (128 * intensityFactor);
                int c = blob.color;
                int startColor = Color.argb(centerAlpha, Color.red(c), Color.green(c), Color.blue(c));
                int midColor   = Color.argb(midAlpha,    Color.red(c), Color.green(c), Color.blue(c));
                int endColor   = Color.argb(0,           Color.red(c), Color.green(c), Color.blue(c));

                blob.gradient = new RadialGradient(
                        px, py, blob.radius,
                        new int[]   { startColor, midColor, endColor },
                        new float[] { 0.0f, 0.6f, 1.0f },
                        Shader.TileMode.CLAMP
                );
                blob.lastGradientColor = blob.color;
                blob.lastGradientX     = px;
                blob.lastGradientY     = py;
            }

            blobPaint.setShader(blob.gradient);
            canvas.drawCircle(px, py, blob.radius, blobPaint);
        }

        transitioning = anyTransitioning;
    }

    private void updateBlobPosition(ColorBlob blob) {
        blob.x += blob.vx;
        blob.y += blob.vy;
        if (blob.x < -0.2f || blob.x > 1.2f) blob.vx *= -1;
        if (blob.y < -0.2f || blob.y > 1.2f) blob.vy *= -1;
    }

    /** Returns true if still transitioning. */
    private boolean updateBlobColor(ColorBlob blob) {
        if (blob.color == blob.targetColor) return false;

        float fraction = 0.08f;
        int c1 = blob.color;
        int c2 = blob.targetColor;

        int a = (int) (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * fraction);
        int r = (int) (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * fraction);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * fraction);
        int b = (int) (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * fraction);

        blob.color = Color.argb(a, r, g, b);

        if (Math.abs(Color.red(c1)   - Color.red(c2))   < 3 &&
            Math.abs(Color.green(c1) - Color.green(c2)) < 3 &&
            Math.abs(Color.blue(c1)  - Color.blue(c2))  < 3) {
            blob.color = blob.targetColor;
            return false;
        }
        return true;
    }

    private static class ColorBlob {
        float x, y, vx, vy, radius;
        int color, targetColor;
        // Gradient cache
        RadialGradient gradient;
        int   lastGradientColor;
        float lastGradientX, lastGradientY;
    }
}
