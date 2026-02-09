package com.music.wallpaper.models;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a color palette extracted from album artwork.
 * Enhanced to support specific color lists for blob rendering.
 */
public class ColorPalette {
    
    private final List<Integer> colors;
    
    // Keep legacy fields for compatibility if needed, or just map them from list
    private final int dominantColor;
    
    /**
     * Constructor for ColorPalette with specific list of colors.
     */
    public ColorPalette(List<Integer> colors) {
        this.colors = new ArrayList<>(colors);
        this.dominantColor = colors.isEmpty() ? 0xFF000000 : colors.get(0);
    }
    
    /**
     * Legacy constructor compatibility
     */
    public ColorPalette(int dominant, int vibrant, int muted, int darkVibrant, int lightVibrant) {
        this.colors = new ArrayList<>();
        colors.add(dominant);
        colors.add(vibrant);
        colors.add(muted);
        colors.add(darkVibrant);
        colors.add(lightVibrant);
        this.dominantColor = dominant;
    }
    
    public int getDominantColor() {
        return dominantColor;
    }
    
    @NonNull
    public List<Integer> getAllColors() {
        return new ArrayList<>(colors);
    }
    
    @NonNull
    public int[] getGradientColors() {
        int[] result = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            result[i] = colors.get(i);
        }
        return result;
    }
    
    @NonNull
    public String toJsonString() {
        try {
            JSONObject json = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            for (int color : colors) {
                jsonArray.put(color);
            }
            json.put("colors", jsonArray);
            json.put("dominant", dominantColor);
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }
    
    @NonNull
    public static ColorPalette fromJsonString(@NonNull String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            List<Integer> colors = new ArrayList<>();
            
            if (json.has("colors")) {
                JSONArray jsonArray = json.getJSONArray("colors");
                for (int i = 0; i < jsonArray.length(); i++) {
                    colors.add(jsonArray.getInt(i));
                }
            } else {
                // Legacy fallback
                colors.add(json.optInt("dominant"));
                colors.add(json.optInt("vibrant"));
                colors.add(json.optInt("muted"));
                colors.add(json.optInt("darkVibrant"));
                colors.add(json.optInt("lightVibrant"));
            }
            
            return new ColorPalette(colors);
        } catch (JSONException e) {
            return getDefaultPalette();
        }
    }
    
    @NonNull
    public static ColorPalette getDefaultPalette() {
        // Default VIBRANT NEON colors for AMOLED
        List<Integer> defaults = new ArrayList<>();
        defaults.add(0xFF00E5FF); // Neon Cyan
        defaults.add(0xFFD500F9); // Neon Purple
        defaults.add(0xFF76FF03); // Neon Green
        defaults.add(0xFFFF3D00); // Neon Orange
        defaults.add(0xFF2979FF); // Electric Blue
        return new ColorPalette(defaults);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColorPalette that = (ColorPalette) o;
        return colors.equals(that.colors);
    }
    
    @Override
    public int hashCode() {
        return colors.hashCode();
    }
    
    @NonNull
    @Override
    public String toString() {
        return "ColorPalette{colors=" + colors + "}";
    }
}
