package com.music.wallpaper.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.LruCache;

/**
 * High-quality blur utility for glassmorphism effects.
 * Uses RenderScript for premium blur quality.
 */
public class BlurHelper {
    
    private static final float MAX_BLUR_RADIUS = 25f;
    private static final int CACHE_SIZE = 4 * 1024 * 1024; // 4MB
    
    private final Context context;
    private final LruCache<String, Bitmap> blurCache;
    private RenderScript renderScript;
    
    public BlurHelper(Context context) {
        this.context = context.getApplicationContext();
        this.blurCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }
    
    /**
     * Apply Gaussian blur to bitmap using RenderScript.
     * Falls back to stack blur on older devices.
     *
     * @param source Source bitmap
     * @param radius Blur radius (1-25)
     * @return Blurred bitmap
     */
    public Bitmap blur(Bitmap source, float radius) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        
        // Clamp radius
        radius = Math.max(1f, Math.min(radius, MAX_BLUR_RADIUS));
        
        // Check cache
        String cacheKey = getCacheKey(source, radius);
        Bitmap cached = blurCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        
        // Apply blur
        Bitmap blurred;
        try {
            blurred = blurWithRenderScript(source, radius);
        } catch (Exception e) {
            // Fallback to stack blur
            blurred = stackBlur(source, (int) radius);
        }
        
        // Cache result
        if (blurred != null) {
            blurCache.put(cacheKey, blurred);
        }
        
        return blurred;
    }
    
    /**
     * Multi-pass blur for ultra-soft effect.
     * Applies blur multiple times for watercolor-like softness.
     */
    public Bitmap ultraSoftBlur(Bitmap source, int passes) {
        if (source == null || passes <= 0) {
            return source;
        }
        
        Bitmap result = source;
        for (int i = 0; i < passes; i++) {
            Bitmap temp = blur(result, MAX_BLUR_RADIUS);
            if (i > 0 && result != source) {
                result.recycle();
            }
            result = temp;
        }
        
        return result;
    }
    
    /**
     * RenderScript Gaussian blur (best quality).
     */
    private Bitmap blurWithRenderScript(Bitmap source, float radius) {
        if (renderScript == null) {
            renderScript = RenderScript.create(context);
        }
        
        // Create output bitmap
        Bitmap output = Bitmap.createBitmap(
            source.getWidth(),
            source.getHeight(),
            Bitmap.Config.ARGB_8888
        );
        
        // Create allocations
        Allocation input = Allocation.createFromBitmap(renderScript, source);
        Allocation outputAlloc = Allocation.createFromBitmap(renderScript, output);
        
        // Create blur script
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(
            renderScript,
            Element.U8_4(renderScript)
        );
        
        // Set radius and apply
        blurScript.setRadius(radius);
        blurScript.setInput(input);
        blurScript.forEach(outputAlloc);
        
        // Copy result
        outputAlloc.copyTo(output);
        
        // Cleanup
        input.destroy();
        outputAlloc.destroy();
        blurScript.destroy();
        
        return output;
    }
    
    /**
     * Stack blur algorithm (fast fallback for older devices).
     */
    private Bitmap stackBlur(Bitmap source, int radius) {
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        
        if (radius < 1) {
            return output;
        }
        
        int width = output.getWidth();
        int height = output.getHeight();
        int[] pixels = new int[width * height];
        output.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // Horizontal pass
        blurHorizontal(pixels, width, height, radius);
        
        // Vertical pass
        blurVertical(pixels, width, height, radius);
        
        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }
    
    private void blurHorizontal(int[] pixels, int width, int height, int radius) {
        int windowSize = radius * 2 + 1;
        
        for (int y = 0; y < height; y++) {
            int sumR = 0, sumG = 0, sumB = 0, sumA = 0;
            
            // Initialize window
            for (int x = -radius; x <= radius; x++) {
                int px = clamp(x, 0, width - 1);
                int pixel = pixels[y * width + px];
                sumR += (pixel >> 16) & 0xFF;
                sumG += (pixel >> 8) & 0xFF;
                sumB += pixel & 0xFF;
                sumA += (pixel >> 24) & 0xFF;
            }
            
            // Slide window
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = (sumA / windowSize) << 24 |
                                        (sumR / windowSize) << 16 |
                                        (sumG / windowSize) << 8 |
                                        (sumB / windowSize);
                
                // Update sums
                int removeX = clamp(x - radius, 0, width - 1);
                int addX = clamp(x + radius + 1, 0, width - 1);
                
                int removePixel = pixels[y * width + removeX];
                int addPixel = pixels[y * width + addX];
                
                sumR = sumR - ((removePixel >> 16) & 0xFF) + ((addPixel >> 16) & 0xFF);
                sumG = sumG - ((removePixel >> 8) & 0xFF) + ((addPixel >> 8) & 0xFF);
                sumB = sumB - (removePixel & 0xFF) + (addPixel & 0xFF);
                sumA = sumA - ((removePixel >> 24) & 0xFF) + ((addPixel >> 24) & 0xFF);
            }
        }
    }
    
    private void blurVertical(int[] pixels, int width, int height, int radius) {
        int windowSize = radius * 2 + 1;
        
        for (int x = 0; x < width; x++) {
            int sumR = 0, sumG = 0, sumB = 0, sumA = 0;
            
            // Initialize window
            for (int y = -radius; y <= radius; y++) {
                int py = clamp(y, 0, height - 1);
                int pixel = pixels[py * width + x];
                sumR += (pixel >> 16) & 0xFF;
                sumG += (pixel >> 8) & 0xFF;
                sumB += pixel & 0xFF;
                sumA += (pixel >> 24) & 0xFF;
            }
            
            // Slide window
            for (int y = 0; y < height; y++) {
                pixels[y * width + x] = (sumA / windowSize) << 24 |
                                        (sumR / windowSize) << 16 |
                                        (sumG / windowSize) << 8 |
                                        (sumB / windowSize);
                
                // Update sums
                int removeY = clamp(y - radius, 0, height - 1);
                int addY = clamp(y + radius + 1, 0, height - 1);
                
                int removePixel = pixels[removeY * width + x];
                int addPixel = pixels[addY * width + x];
                
                sumR = sumR - ((removePixel >> 16) & 0xFF) + ((addPixel >> 16) & 0xFF);
                sumG = sumG - ((removePixel >> 8) & 0xFF) + ((addPixel >> 8) & 0xFF);
                sumB = sumB - (removePixel & 0xFF) + (addPixel & 0xFF);
                sumA = sumA - ((removePixel >> 24) & 0xFF) + ((addPixel >> 24) & 0xFF);
            }
        }
    }
    
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
    
    private String getCacheKey(Bitmap bitmap, float radius) {
        return bitmap.hashCode() + "_" + radius;
    }
    
    /**
     * Clear blur cache.
     */
    public void clearCache() {
        blurCache.evictAll();
    }
    
    /**
     * Release resources.
     */
    public void release() {
        clearCache();
        if (renderScript != null) {
            renderScript.destroy();
            renderScript = null;
        }
    }
}
