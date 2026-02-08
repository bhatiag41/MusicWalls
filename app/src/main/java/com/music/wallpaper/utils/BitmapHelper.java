package com.music.wallpaper.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

/**
 * Utility class for efficient bitmap operations and memory management.
 */
public class BitmapHelper {
    
    private static final String TAG = "BitmapHelper";
    private static final int CACHE_SIZE = 5; // Cache up to 5 bitmaps
    
    // LRU cache for recently used bitmaps
    private static final LruCache<String, Bitmap> bitmapCache = 
        new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return 1; // Count by number of entries, not bytes
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, 
                                       Bitmap oldValue, Bitmap newValue) {
                // Recycle bitmap when removed from cache
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };
    
    /**
     * Loads a bitmap from URI with efficient sampling to target dimensions.
     * This avoids loading huge bitmaps into memory.
     *
     * @param context      Android context
     * @param uri          URI of the image
     * @param targetWidth  Target width in pixels
     * @param targetHeight Target height in pixels
     * @return Sampled bitmap, or null if loading fails
     */
    @Nullable
    public static Bitmap loadSampledBitmap(@NonNull Context context,
                                          @NonNull Uri uri,
                                          int targetWidth,
                                          int targetHeight) {
        try {
            // First decode with inJustDecodeBounds to get dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            if (inputStream != null) inputStream.close();
            
            // Calculate sample size
            options.inSampleSize = calculateSampleSize(
                options.outWidth, 
                options.outHeight,
                targetWidth, 
                targetHeight
            );
            
            // Decode with sample size
            options.inJustDecodeBounds = false;
            inputStream = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (inputStream != null) inputStream.close();
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading sampled bitmap", e);
            return null;
        }
    }
    
    /**
     * Calculates the optimal sample size for bitmap decoding.
     */
    private static int calculateSampleSize(int width, int height, 
                                          int targetWidth, int targetHeight) {
        int sampleSize = 1;
        
        if (height > targetHeight || width > targetWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / sampleSize) >= targetHeight
                    && (halfWidth / sampleSize) >= targetWidth) {
                sampleSize *= 2;
            }
        }
        
        return sampleSize;
    }
    
    /**
     * Applies a blur effect to the bitmap using RenderScript.
     * Falls back to custom blur if RenderScript is not available.
     *
     * @param context Context for RenderScript
     * @param source  Source bitmap
     * @param radius  Blur radius (1-25)
     * @return Blurred bitmap
     */
    @Nullable
    public static Bitmap applyBlur(@NonNull Context context, 
                                  @NonNull Bitmap source, 
                                  float radius) {
        if (source == null) {
            return null;
        }
        
        // Clamp radius to valid range
        radius = Math.max(1f, Math.min(25f, radius));
        
        try {
            // Try RenderScript first (faster)
            return applyRenderScriptBlur(context, source, radius);
        } catch (Exception e) {
            Log.w(TAG, "RenderScript blur failed, using fallback", e);
            // Fallback to custom blur
            return applyCustomBlur(source, (int) radius);
        }
    }
    
    /**
     * Applies blur using RenderScript (hardware accelerated).
     */
    @Nullable
    private static Bitmap applyRenderScriptBlur(@NonNull Context context,
                                               @NonNull Bitmap source,
                                               float radius) {
        Bitmap output = Bitmap.createBitmap(
            source.getWidth(), 
            source.getHeight(), 
            Bitmap.Config.ARGB_8888
        );
        
        RenderScript rs = null;
        try {
            rs = RenderScript.create(context);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            
            Allocation input = Allocation.createFromBitmap(rs, source);
            Allocation outputAlloc = Allocation.createFromBitmap(rs, output);
            
            script.setRadius(radius);
            script.setInput(input);
            script.forEach(outputAlloc);
            outputAlloc.copyTo(output);
            
            return output;
        } finally {
            if (rs != null) {
                rs.destroy();
            }
        }
    }
    
    /**
     * Applies a simple box blur (fallback method).
     * This is slower but works without RenderScript.
     */
    @Nullable
    private static Bitmap applyCustomBlur(@NonNull Bitmap source, int radius) {
        if (radius < 1) {
            return source;
        }
        
        int width = source.getWidth();
        int height = source.getHeight();
        
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        
        // Simple approach: draw with reduced alpha multiple times offset
        // This creates a blur-like effect
        int alpha = 255 / (radius * 2 + 1);
        paint.setAlpha(alpha);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                canvas.drawBitmap(source, x, y, paint);
            }
        }
        
        return output;
    }
    
    /**
     * Scales a bitmap efficiently.
     *
     * @param source Source bitmap
     * @param width  Target width
     * @param height Target height
     * @return Scaled bitmap
     */
    @Nullable
    public static Bitmap scaleBitmap(@NonNull Bitmap source, int width, int height) {
        try {
            return Bitmap.createScaledBitmap(source, width, height, true);
        } catch (Exception e) {
            Log.e(TAG, "Error scaling bitmap", e);
            return source;
        }
    }
    
    /**
     * Adds a bitmap to the cache.
     *
     * @param key    Cache key
     * @param bitmap Bitmap to cache
     */
    public static void cacheBitmap(@NonNull String key, @NonNull Bitmap bitmap) {
        bitmapCache.put(key, bitmap);
    }
    
    /**
     * Retrieves a bitmap from the cache.
     *
     * @param key Cache key
     * @return Cached bitmap, or null if not found
     */
    @Nullable
    public static Bitmap getCachedBitmap(@NonNull String key) {
        return bitmapCache.get(key);
    }
    
    /**
     * Clears the bitmap cache and recycles all cached bitmaps.
     */
    public static void clearCache() {
        bitmapCache.evictAll();
    }
    
    /**
     * Safely recycles a bitmap if it's not null and not already recycled.
     *
     * @param bitmap Bitmap to recycle
     */
    public static void recycleBitmap(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
