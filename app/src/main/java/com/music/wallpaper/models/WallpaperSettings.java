package com.music.wallpaper.models;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates all user preferences for the wallpaper.
 * Loads settings from SharedPreferences and provides default values.
 */
public class WallpaperSettings {
    
    // Preference keys
    public static final String KEY_ANIMATION_SPEED = "animation_speed";
    public static final String KEY_TEXTURE_MODE = "texture_mode";
    public static final String KEY_COLOR_INTENSITY = "color_intensity";
    public static final String KEY_BLUR_AMOUNT = "blur_amount";
    public static final String KEY_ENABLED_MUSIC_APPS = "enabled_music_apps";
    public static final String KEY_DEBUG_MODE = "debug_mode";
    
    // Texture mode constants
    public static final String TEXTURE_ACRYLIC = "acrylic";
    public static final String TEXTURE_GLASS = "glass";
    public static final String TEXTURE_GRADIENT_BLUR = "gradient_blur";
    
    // Default values
    public static final int DEFAULT_ANIMATION_SPEED = 50;
    public static final String DEFAULT_TEXTURE_MODE = TEXTURE_ACRYLIC;
    public static final int DEFAULT_COLOR_INTENSITY = 80;
    public static final int DEFAULT_BLUR_AMOUNT = 30;
    public static final boolean DEFAULT_DEBUG_MODE = false;
    
    // Settings fields
    private final int animationSpeed;
    private final String textureMode;
    private final int colorIntensity;
    private final int blurAmount;
    private final Set<String> enabledMusicApps;
    private final boolean debugMode;
    
    private WallpaperSettings(int animationSpeed,
                             String textureMode,
                             int colorIntensity,
                             int blurAmount,
                             Set<String> enabledMusicApps,
                             boolean debugMode) {
        this.animationSpeed = animationSpeed;
        this.textureMode = textureMode;
        this.colorIntensity = colorIntensity;
        this.blurAmount = blurAmount;
        this.enabledMusicApps = enabledMusicApps;
        this.debugMode = debugMode;
    }
    
    /**
     * Loads settings from SharedPreferences.
     *
     * @param context Android context
     * @return WallpaperSettings object with current preferences
     */
    @NonNull
    public static WallpaperSettings loadFromPreferences(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        int animationSpeed = prefs.getInt(KEY_ANIMATION_SPEED, DEFAULT_ANIMATION_SPEED);
        String textureMode = prefs.getString(KEY_TEXTURE_MODE, DEFAULT_TEXTURE_MODE);
        int colorIntensity = prefs.getInt(KEY_COLOR_INTENSITY, DEFAULT_COLOR_INTENSITY);
        int blurAmount = prefs.getInt(KEY_BLUR_AMOUNT, DEFAULT_BLUR_AMOUNT);
        boolean debugMode = prefs.getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
        
        // Load enabled music apps (default to all)
        Set<String> defaultApps = getDefaultMusicApps();
        Set<String> enabledMusicApps = prefs.getStringSet(KEY_ENABLED_MUSIC_APPS, defaultApps);
        if (enabledMusicApps == null) {
            enabledMusicApps = defaultApps;
        }
        
        return new WallpaperSettings(
            animationSpeed,
            textureMode,
            colorIntensity,
            blurAmount,
            enabledMusicApps,
            debugMode
        );
    }
    
    /**
     * Returns the default set of music apps to monitor.
     *
     * @return Set of package name patterns
     */
    @NonNull
    private static Set<String> getDefaultMusicApps() {
        Set<String> apps = new HashSet<>();
        apps.add("spotify");
        apps.add("youtube");
        apps.add("music");
        apps.add("pandora");
        apps.add("soundcloud");
        apps.add("apple");
        apps.add("tidal");
        apps.add("deezer");
        return apps;
    }
    
    /**
     * Resets all settings to default values.
     *
     * @param context Android context
     */
    public static void resetToDefaults(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt(KEY_ANIMATION_SPEED, DEFAULT_ANIMATION_SPEED);
        editor.putString(KEY_TEXTURE_MODE, DEFAULT_TEXTURE_MODE);
        editor.putInt(KEY_COLOR_INTENSITY, DEFAULT_COLOR_INTENSITY);
        editor.putInt(KEY_BLUR_AMOUNT, DEFAULT_BLUR_AMOUNT);
        editor.putStringSet(KEY_ENABLED_MUSIC_APPS, getDefaultMusicApps());
        editor.putBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
        
        editor.apply();
    }
    
    // Getters
    
    public int getAnimationSpeed() {
        return animationSpeed;
    }
    
    /**
     * Returns animation speed factor (0.0 to 2.0).
     * 50 = 1.0x speed, 0 = 0.5x speed, 100 = 2.0x speed
     */
    public float getAnimationSpeedFactor() {
        return 0.5f + (animationSpeed / 100.0f * 1.5f);
    }
    
    @NonNull
    public String getTextureMode() {
        return textureMode;
    }
    
    public int getColorIntensity() {
        return colorIntensity;
    }
    
    /**
     * Returns color intensity factor (0.0 to 1.0).
     */
    public float getColorIntensityFactor() {
        return colorIntensity / 100.0f;
    }
    
    public int getBlurAmount() {
        return blurAmount;
    }
    
    /**
     * Returns blur radius in pixels (0 to 25).
     */
    public float getBlurRadius() {
        return (blurAmount / 100.0f) * 25.0f;
    }
    
    @NonNull
    public Set<String> getEnabledMusicApps() {
        return new HashSet<>(enabledMusicApps);
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Checks if a package name belongs to an enabled music app.
     *
     * @param packageName Package name to check
     * @return true if app is enabled for monitoring
     */
    public boolean isMusicAppEnabled(@NonNull String packageName) {
        if (enabledMusicApps.isEmpty()) {
            return true; // If no filter, allow all
        }
        
        String lowerPackage = packageName.toLowerCase();
        for (String app : enabledMusicApps) {
            if (lowerPackage.contains(app.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "WallpaperSettings{" +
                "animationSpeed=" + animationSpeed +
                ", textureMode='" + textureMode + '\'' +
                ", colorIntensity=" + colorIntensity +
                ", blurAmount=" + blurAmount +
                ", debugMode=" + debugMode +
                '}';
    }
}
