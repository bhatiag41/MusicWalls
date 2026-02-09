package com.music.wallpaper.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import com.music.wallpaper.models.ColorPalette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for extracting color palettes from album artwork using Android Palette API.
 */
public class ColorExtractor {
    
    private static final String TAG = "ColorExtractor";
    
    /**
     * Extracts a color palette from the given bitmap using Palette API.
     * Ensures distinct colors are returned.
     */
    @NonNull
    public static ColorPalette extractPalette(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return ColorPalette.getDefaultPalette();
        }
        
        try {
            // Generate palette
            Palette palette = Palette.from(bitmap).generate();
            
            // Extract all standard colors
            List<Integer> colors = new ArrayList<>();
            
            // Add standard swatches
            addIfValid(colors, palette.getDominantColor(0));
            addIfValid(colors, palette.getVibrantColor(0));
            addIfValid(colors, palette.getMutedColor(0));
            addIfValid(colors, palette.getDarkVibrantColor(0));
            addIfValid(colors, palette.getLightVibrantColor(0));
            addIfValid(colors, palette.getDarkMutedColor(0));
            addIfValid(colors, palette.getLightMutedColor(0));
            
            // Also add any other population-heavy swatches if we don't have enough
            if (colors.size() < 5) {
                for (Palette.Swatch swatch : palette.getSwatches()) {
                    if (swatch != null) {
                        addIfValid(colors, swatch.getRgb());
                    }
                }
            }
            
            // Remove duplicates and similar colors
            List<Integer> distinctColors = removeSimilarColors(colors);
            
            // Ensure we have at least 5 colors (fill with variations if needed)
            while (distinctColors.size() < 5) {
                // Generate a complementary/offset color from the first color
                int base = distinctColors.isEmpty() ? Color.DKGRAY : distinctColors.get(0);
                distinctColors.add(generateVariation(base, distinctColors.size()));
            }
            
            Log.d(TAG, "Extracted " + distinctColors.size() + " distinct colors: " + distinctColors);
            return new ColorPalette(distinctColors);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting palette", e);
            return ColorPalette.getDefaultPalette();
        }
    }
    
    private static void addIfValid(List<Integer> list, int color) {
        if (color != 0 && color != Color.BLACK && color != Color.WHITE) {
            list.add(color);
        }
    }
    
    /**
     * Remove colors that are too similar to each other.
     */
    private static List<Integer> removeSimilarColors(List<Integer> extracted) {
        List<Integer> result = new ArrayList<>();
        
        for (Integer color : extracted) {
            boolean isSimilar = false;
            for (Integer existing : result) {
                if (areColorsSimilar(color, existing)) {
                    isSimilar = true;
                    break;
                }
            }
            if (!isSimilar) {
                result.add(color);
            }
        }
        return result;
    }
    
    /**
     * Check if two colors are perceptually similar.
     */
    private static boolean areColorsSimilar(int color1, int color2) {
        double dist = colorDistance(color1, color2);
        return dist < 30.0; // Threshold for similarity
    }
    
    private static double colorDistance(int c1, int c2) {
        int r1 = Color.red(c1);
        int g1 = Color.green(c1);
        int b1 = Color.blue(c1);
        
        int r2 = Color.red(c2);
        int g2 = Color.green(c2);
        int b2 = Color.blue(c2);
        
        return Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));
    }
    
    private static int generateVariation(int color, int index) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        // Shift hue
        hsv[0] = (hsv[0] + 30 * (index + 1)) % 360;
        return Color.HSVToColor(hsv);
    }
    
    public static void clearCache() {
        // No cache needed for this logic
    }
}
