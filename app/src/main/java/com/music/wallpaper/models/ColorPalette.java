package com.music.wallpaper.models;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data model representing a color palette extracted from album artwork.
 * Contains dominant, vibrant, and muted colors for gradient generation.
 */
public class ColorPalette {
    
    private final int dominantColor;
    private final int vibrantColor;
    private final int mutedColor;
    private final int darkVibrantColor;
    private final int lightVibrantColor;
    
    /**
     * Constructor for ColorPalette.
     *
     * @param dominantColor     Most dominant color in the image
     * @param vibrantColor      Most vibrant/saturated color
     * @param mutedColor        Most muted/desaturated color
     * @param darkVibrantColor  Dark vibrant color variant
     * @param lightVibrantColor Light vibrant color variant
     */
    public ColorPalette(int dominantColor, 
                       int vibrantColor, 
                       int mutedColor,
                       int darkVibrantColor,
                       int lightVibrantColor) {
        this.dominantColor = dominantColor;
        this.vibrantColor = vibrantColor;
        this.mutedColor = mutedColor;
        this.darkVibrantColor = darkVibrantColor;
        this.lightVibrantColor = lightVibrantColor;
    }
    
    public int getDominantColor() {
        return dominantColor;
    }
    
    public int getVibrantColor() {
        return vibrantColor;
    }
    
    public int getMutedColor() {
        return mutedColor;
    }
    
    public int getDarkVibrantColor() {
        return darkVibrantColor;
    }
    
    public int getLightVibrantColor() {
        return lightVibrantColor;
    }
    
    /**
     * Returns an array of colors suitable for gradient generation.
     * Typically returns 2-3 colors that work well together.
     *
     * @return Array of color integers
     */
    @NonNull
    public int[] getGradientColors() {
        // Return vibrant, dominant, and muted for a nice 3-color gradient
        return new int[]{vibrantColor, dominantColor, mutedColor};
    }
    
    /**
     * Returns an array of colors for advanced gradients with more variation.
     *
     * @return Array of 5 color integers
     */
    @NonNull
    public int[] getExtendedGradientColors() {
        return new int[]{
            lightVibrantColor,
            vibrantColor,
            dominantColor,
            mutedColor,
            darkVibrantColor
        };
    }
    
    /**
     * Serializes this ColorPalette to JSON string.
     *
     * @return JSON string representation
     */
    @NonNull
    public String toJsonString() {
        try {
            JSONObject json = new JSONObject();
            json.put("dominant", dominantColor);
            json.put("vibrant", vibrantColor);
            json.put("muted", mutedColor);
            json.put("darkVibrant", darkVibrantColor);
            json.put("lightVibrant", lightVibrantColor);
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }
    
    /**
     * Deserializes a ColorPalette from JSON string.
     *
     * @param jsonString JSON string representation
     * @return ColorPalette object, or null if parsing fails
     */
    @NonNull
    public static ColorPalette fromJsonString(@NonNull String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            return new ColorPalette(
                json.getInt("dominant"),
                json.getInt("vibrant"),
                json.getInt("muted"),
                json.getInt("darkVibrant"),
                json.getInt("lightVibrant")
            );
        } catch (JSONException e) {
            // Return default palette on error
            return getDefaultPalette();
        }
    }
    
    /**
     * Returns a default aesthetic color palette for when no music is playing.
     *
     * @return Default ColorPalette
     */
    @NonNull
    public static ColorPalette getDefaultPalette() {
        // Beautiful purple to blue gradient
        return new ColorPalette(
            0xFF6A5ACD,  // Slate blue (dominant)
            0xFF9370DB,  // Medium purple (vibrant)
            0xFF8B7FB8,  // Muted purple
            0xFF483D8B,  // Dark slate blue (dark vibrant)
            0xFFB19CD9   // Light purple (light vibrant)
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ColorPalette that = (ColorPalette) o;
        
        if (dominantColor != that.dominantColor) return false;
        if (vibrantColor != that.vibrantColor) return false;
        if (mutedColor != that.mutedColor) return false;
        if (darkVibrantColor != that.darkVibrantColor) return false;
        return lightVibrantColor == that.lightVibrantColor;
    }
    
    @Override
    public int hashCode() {
        int result = dominantColor;
        result = 31 * result + vibrantColor;
        result = 31 * result + mutedColor;
        result = 31 * result + darkVibrantColor;
        result = 31 * result + lightVibrantColor;
        return result;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "ColorPalette{" +
                "dominant=" + String.format("#%06X", (0xFFFFFF & dominantColor)) +
                ", vibrant=" + String.format("#%06X", (0xFFFFFF & vibrantColor)) +
                ", muted=" + String.format("#%06X", (0xFFFFFF & mutedColor)) +
                '}';
    }
}
