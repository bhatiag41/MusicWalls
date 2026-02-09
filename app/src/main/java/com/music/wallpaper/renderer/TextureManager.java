package com.music.wallpaper.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages generation and caching of premium texture effects.
 * Supports Acrylic, Glass, and Gradient Blur texture modes.
 */
public class TextureManager {
    
    public enum TextureMode {
        ACRYLIC,        // Microsoft Fluent Design-inspired acrylic effect
        GLASS,          // Glossy glass effect with highlights
        GRADIENT_BLUR,  // Multi-directional gradient blur
        NONE            // No texture
    }
    
    private static final int NOISE_RESOLUTION = 128;
    private static final float ACRYLIC_NOISE_ALPHA = 0.03f;
    private static final float GLASS_HIGHLIGHT_ALPHA = 0.2f;
    private static final float GRADIENT_BLUR_STRENGTH = 0.15f;
    
    private final Map<String, Bitmap> textureCache;
    private final Paint paint;
    
    private int width;
    private int height;
    private TextureMode currentMode;
    
    public TextureManager() {
        textureCache = new HashMap<>();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentMode = TextureMode.ACRYLIC;
    }
    
    /**
     * Sets the surface size and clears cache.
     */
    public void setSurfaceSize(int width, int height) {
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            clearCache();
        }
    }
    
    /**
     * Sets the texture mode.
     */
    public void setTextureMode(TextureMode mode) {
        this.currentMode = mode;
    }
    
    /**
     * Applies the current texture effect to the canvas.
     */
    public void applyTexture(Canvas canvas) {
        if (width == 0 || height == 0 || currentMode == TextureMode.NONE) {
            return;
        }
        
        switch (currentMode) {
            case ACRYLIC:
                applyAcrylicTexture(canvas);
                break;
            case GLASS:
                applyGlassTexture(canvas);
                break;
            case GRADIENT_BLUR:
                applyGradientBlurTexture(canvas);
                break;
        }
    }
    
    /**
     * Applies Acrylic texture effect (subtle noise + luminosity).
     */
    private void applyAcrylicTexture(Canvas canvas) {
        String cacheKey = "acrylic_" + width + "x" + height;
        Bitmap texture = textureCache.get(cacheKey);
        
        if (texture == null) {
            texture = generateAcrylicTexture();
            textureCache.put(cacheKey, texture);
        }
        
        paint.setAlpha((int) (ACRYLIC_NOISE_ALPHA * 255));
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        canvas.drawBitmap(texture, 0, 0, paint);
        paint.setXfermode(null);
        paint.setAlpha(255);
    }
    
    /**
     * Generates Acrylic texture with noise pattern.
     */
    private Bitmap generateAcrylicTexture() {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Create noise pattern
        Paint noisePaint = new Paint();
        java.util.Random random = new java.util.Random(12345); // Fixed seed for consistency
        
        for (int y = 0; y < height; y += NOISE_RESOLUTION / 8) {
            for (int x = 0; x < width; x += NOISE_RESOLUTION / 8) {
                int gray = 128 + random.nextInt(64) - 32; // Subtle variation
                noisePaint.setColor(Color.rgb(gray, gray, gray));
                canvas.drawRect(x, y, x + NOISE_RESOLUTION / 8, y + NOISE_RESOLUTION / 8, noisePaint);
            }
        }
        
        // Add subtle radial gradient for depth
        Paint gradientPaint = new Paint();
        android.graphics.RadialGradient radialGradient = new android.graphics.RadialGradient(
            width / 2f, height / 2f, Math.max(width, height) * 0.7f,
            new int[]{Color.argb(30, 255, 255, 255), Color.argb(10, 0, 0, 0)},
            new float[]{0.0f, 1.0f},
            Shader.TileMode.CLAMP
        );
        gradientPaint.setShader(radialGradient);
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        canvas.drawRect(0, 0, width, height, gradientPaint);
        
        return bitmap;
    }
    
    /**
     * Applies Glass texture effect (glossy highlights).
     */
    private void applyGlassTexture(Canvas canvas) {
        // Create glossy highlight effect
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        // Diagonal highlight
        LinearGradient highlight1 = new LinearGradient(
            0, 0, width * 0.3f, height * 0.3f,
            new int[]{
                Color.argb((int) (GLASS_HIGHLIGHT_ALPHA * 255), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            },
            new float[]{0.0f, 1.0f},
            Shader.TileMode.CLAMP
        );
        highlightPaint.setShader(highlight1);
        highlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        canvas.drawRect(0, 0, width, height, highlightPaint);
        
        // Subtle bottom reflection
        LinearGradient highlight2 = new LinearGradient(
            0, height * 0.7f, 0, height,
            new int[]{
                Color.argb(0, 255, 255, 255),
                Color.argb((int) (GLASS_HIGHLIGHT_ALPHA * 0.5f * 255), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            },
            new float[]{0.0f, 0.5f, 1.0f},
            Shader.TileMode.CLAMP
        );
        highlightPaint.setShader(highlight2);
        canvas.drawRect(0, height * 0.7f, width, height, highlightPaint);
        
        highlightPaint.setXfermode(null);
    }
    
    /**
     * Applies Gradient Blur texture effect (multi-directional soft gradients).
     */
    private void applyGradientBlurTexture(Canvas canvas) {
        Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        
        // Create multiple soft gradients in different directions
        // Horizontal gradient
        LinearGradient horizontalGradient = new LinearGradient(
            0, height / 2f, width, height / 2f,
            new int[]{
                Color.argb((int) (GRADIENT_BLUR_STRENGTH * 255), 0, 0, 0),
                Color.argb(0, 255, 255, 255),
                Color.argb((int) (GRADIENT_BLUR_STRENGTH * 255), 0, 0, 0)
            },
            new float[]{0.0f, 0.5f, 1.0f},
            Shader.TileMode.CLAMP
        );
        blurPaint.setShader(horizontalGradient);
        canvas.drawRect(0, 0, width, height, blurPaint);
        
        // Vertical gradient
        LinearGradient verticalGradient = new LinearGradient(
            width / 2f, 0, width / 2f, height,
            new int[]{
                Color.argb((int) (GRADIENT_BLUR_STRENGTH * 255), 0, 0, 0),
                Color.argb(0, 255, 255, 255),
                Color.argb((int) (GRADIENT_BLUR_STRENGTH * 255), 0, 0, 0)
            },
            new float[]{0.0f, 0.5f, 1.0f},
            Shader.TileMode.CLAMP
        );
        blurPaint.setShader(verticalGradient);
        canvas.drawRect(0, 0, width, height, blurPaint);
        
        // Radial gradient for depth
        android.graphics.RadialGradient radialGradient = new android.graphics.RadialGradient(
            width / 2f, height / 2f, Math.max(width, height) * 0.5f,
            new int[]{
                Color.argb(0, 255, 255, 255),
                Color.argb((int) (GRADIENT_BLUR_STRENGTH * 0.8f * 255), 0, 0, 0)
            },
            new float[]{0.0f, 1.0f},
            Shader.TileMode.CLAMP
        );
        blurPaint.setShader(radialGradient);
        blurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        canvas.drawRect(0, 0, width, height, blurPaint);
        
        blurPaint.setXfermode(null);
    }
    
    /**
     * Clears the texture cache.
     */
    public void clearCache() {
        for (Bitmap bitmap : textureCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        textureCache.clear();
    }
    
    /**
     * Releases resources.
     */
    public void release() {
        clearCache();
    }
}
