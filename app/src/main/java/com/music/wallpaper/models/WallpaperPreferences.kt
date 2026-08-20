package com.music.wallpaper.models

import android.content.Context
import android.content.SharedPreferences

enum class PaletteStyle {
    VIBRANT,
    MUTED,
    DARK
}

data class WallpaperPreferences(
    val animationSpeed: Float = 0.5f,       // 0.1f to 2.0f
    val intensity: Float = 0.8f,            // 0.3f to 1.5f
    val paletteStyle: PaletteStyle = PaletteStyle.VIBRANT,
    val grainEnabled: Boolean = true,
    val enabledMusicApps: Set<String> = defaultMusicApps
) {
    companion object {
        const val PREFS_NAME = "WallpaperPreferences"

        const val KEY_ANIMATION_SPEED = "animation_speed_val"
        const val KEY_INTENSITY = "intensity_val"
        const val KEY_PALETTE_STYLE = "palette_style_val"
        const val KEY_GRAIN_ENABLED = "grain_enabled_val"
        const val KEY_ENABLED_MUSIC_APPS = "enabled_music_apps_set"

        val defaultMusicApps: Set<String> = setOf(
            "spotify", "youtube", "music", "pandora",
            "soundcloud", "apple", "tidal", "deezer"
        )

        fun load(context: Context): WallpaperPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val speed = prefs.getFloat(KEY_ANIMATION_SPEED, 0.5f)
            val intensity = prefs.getFloat(KEY_INTENSITY, 0.8f)
            val styleName = prefs.getString(KEY_PALETTE_STYLE, PaletteStyle.VIBRANT.name) ?: PaletteStyle.VIBRANT.name
            val style = try {
                PaletteStyle.valueOf(styleName)
            } catch (e: Exception) {
                PaletteStyle.VIBRANT
            }
            val grain = prefs.getBoolean(KEY_GRAIN_ENABLED, true)
            val apps = prefs.getStringSet(KEY_ENABLED_MUSIC_APPS, defaultMusicApps) ?: defaultMusicApps

            return WallpaperPreferences(
                animationSpeed = speed,
                intensity = intensity,
                paletteStyle = style,
                grainEnabled = grain,
                enabledMusicApps = apps
            )
        }

        fun save(context: Context, preferences: WallpaperPreferences) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_ANIMATION_SPEED, preferences.animationSpeed)
                .putFloat(KEY_INTENSITY, preferences.intensity)
                .putString(KEY_PALETTE_STYLE, preferences.paletteStyle.name)
                .putBoolean(KEY_GRAIN_ENABLED, preferences.grainEnabled)
                .putStringSet(KEY_ENABLED_MUSIC_APPS, preferences.enabledMusicApps)
                .apply()
        }
    }

    fun isMusicAppEnabled(packageName: String): Boolean {
        if (enabledMusicApps.isEmpty()) return true
        val lower = packageName.lowercase()
        return enabledMusicApps.any { lower.contains(it.lowercase()) }
    }
}
