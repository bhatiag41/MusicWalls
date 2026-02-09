package com.music.wallpaper.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.music.wallpaper.models.ColorPalette;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for ColorPalette storage and distribution.
 * Provides thread-safe access to current color palette and notifies observers of changes.
 */
public class ColorPaletteManager {
    
    private static final String TAG = "ColorPaletteManager";
    private static final String PREF_CURRENT_PALETTE = "current_color_palette_json";
    
    private static volatile ColorPaletteManager instance;
    
    private ColorPalette currentPalette;
    private final List<ColorPaletteListener> listeners;
    private final Object lock = new Object();
    
    /**
     * Listener interface for palette changes.
     */
    public interface ColorPaletteListener {
        void onColorPaletteChanged(ColorPalette newPalette);
    }
    
    private ColorPaletteManager() {
        listeners = new ArrayList<>();
        currentPalette = ColorPalette.getDefaultPalette();
    }
    
    /**
     * Gets the singleton instance (thread-safe double-checked locking).
     */
    public static ColorPaletteManager getInstance() {
        if (instance == null) {
            synchronized (ColorPaletteManager.class) {
                if (instance == null) {
                    instance = new ColorPaletteManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Updates the current color palette and notifies all listeners.
     * Also persists to SharedPreferences for reliability.
     */
    public void updatePalette(Context context, ColorPalette newPalette) {
        if (newPalette == null) {
            Log.w(TAG, "Attempted to update with null palette, ignoring");
            return;
        }
        
        synchronized (lock) {
            currentPalette = newPalette;
            
            // Persist to SharedPreferences
            savePaletteToPreferences(context, newPalette);
            
            Log.d(TAG, "Palette updated: " + newPalette);
        }
        
        // Notify listeners (outside lock to avoid deadlock)
        notifyListeners(newPalette);
    }
    
    /**
     * Gets the current color palette (thread-safe).
     */
    public ColorPalette getCurrentPalette(Context context) {
        synchronized (lock) {
            // If current palette is still default, try loading from preferences
            if (currentPalette == ColorPalette.getDefaultPalette()) {
                ColorPalette savedPalette = loadPaletteFromPreferences(context);
                if (savedPalette != null) {
                    currentPalette = savedPalette;
                }
            }
            return currentPalette;
        }
    }
    
    /**
     * Registers a listener for palette changes.
     */
    public void addListener(ColorPaletteListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                Log.d(TAG, "Listener added, total: " + listeners.size());
            }
        }
    }
    
    /**
     * Unregisters a listener.
     */
    public void removeListener(ColorPaletteListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            Log.d(TAG, "Listener removed, total: " + listeners.size());
        }
    }
    
    /**
     * Notifies all registered listeners of palette change.
     */
    private void notifyListeners(ColorPalette newPalette) {
        List<ColorPaletteListener> listenersCopy;
        synchronized (listeners) {
            listenersCopy = new ArrayList<>(listeners);
        }
        
        Log.d(TAG, "Notifying " + listenersCopy.size() + " listeners");
        for (ColorPaletteListener listener : listenersCopy) {
            try {
                listener.onColorPaletteChanged(newPalette);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }
    
    /**
     * Saves palette to SharedPreferences for persistence.
     */
    private void savePaletteToPreferences(Context context, ColorPalette palette) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String json = palette.toJsonString();
            prefs.edit().putString(PREF_CURRENT_PALETTE, json).apply();
            Log.d(TAG, "Palette saved to preferences");
        } catch (Exception e) {
            Log.e(TAG, "Error saving palette to preferences", e);
        }
    }
    
    /**
     * Loads palette from SharedPreferences.
     */
    private ColorPalette loadPaletteFromPreferences(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String json = prefs.getString(PREF_CURRENT_PALETTE, null);
            if (json != null) {
                ColorPalette palette = ColorPalette.fromJsonString(json);
                Log.d(TAG, "Palette loaded from preferences");
                return palette;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading palette from preferences", e);
        }
        return null;
    }
    
    /**
     * Clears all listeners (useful for cleanup).
     */
    public void clearListeners() {
        synchronized (listeners) {
            listeners.clear();
            Log.d(TAG, "All listeners cleared");
        }
    }
}
