package com.music.wallpaper.utils;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import com.music.wallpaper.models.ColorPalette;

/**
 * Utility class for extracting color palettes from album artwork using Android Palette API.
 */
public class ColorExtractor {
    
    private static final String TAG = "ColorExtractor";
    private static final int MAX_PALETTE_COLORS = 16;
    
    // Cache for recently extracted palettes to avoid duplicate processing
    private static Bitmap lastBitmap = null;
    private static ColorPalette lastPalette = null;
    
    /**
     * Extracts a color palette from the given bitmap using Palette API.
     * Returns a default aesthetic palette if extraction fails or bitmap is null.
     *
     * @param bitmap Album artwork bitmap
     * @return ColorPalette with extracted colors
     */
    @NonNull
    public static ColorPalette extractPalette(@Nullable Bitmap bitmap) {
        // Return cached palette if same bitmap
        if (bitmap != null && bitmap.equals(lastBitmap) && lastPalette != null) {
            Log.d(TAG, "Returning cached palette");
            return lastPalette;
        }
        
        if (bitmap == null) {
            Log.w(TAG, "Bitmap is null, returning default palette");
            return ColorPalette.getDefaultPalette();
        }
        
        try {
            // Generate palette synchronously (we're already on a background thread)
            Palette palette = Palette.from(bitmap)
                    .maximumColorCount(MAX_PALETTE_COLORS)
                    .generate();
            
            // Extract colors with fallbacks
            int dominant = getColorOrDefault(palette.getDominantSwatch(), 0xFF6A5ACD);
            int vibrant = getColorOrDefault(palette.getVibrantSwatch(), 0xFF9370DB);
            int muted = getColorOrDefault(palette.getMutedSwatch(), 0xFF8B7FB8);
            int darkVibrant = getColorOrDefault(palette.getDarkVibrantSwatch(), 0xFF483D8B);
            int lightVibrant = getColorOrDefault(palette.getLightVibrantSwatch(), 0xFFB19CD9);
            
            ColorPalette colorPalette = new ColorPalette(
                dominant,
                vibrant,
                muted,
                darkVibrant,
                lightVibrant
            );
            
            // Cache the result
            lastBitmap = bitmap;
            lastPalette = colorPalette;
            
            Log.d(TAG, "Extracted palette: " + colorPalette);
            return colorPalette;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting palette, using default", e);
            return ColorPalette.getDefaultPalette();
        }
    }
    
    /**
     * Extracts color from swatch or returns default if swatch is null.
     */
    private static int getColorOrDefault(@Nullable Palette.Swatch swatch, int defaultColor) {
        return swatch != null ? swatch.getRgb() : defaultColor;
    }
    
    /**
     * Clears the palette cache.
     */
    public static void clearCache() {
        lastBitmap = null;
        lastPalette = null;
    }
}
