package com.music.wallpaper.models

import android.content.Context
import android.content.SharedPreferences



enum class WallpaperStyle(val displayName: String) {
    AURORA_DRIFT("Aurora Drift"),       // 0: Domain-warped noise, soft screen blend (default)
    CRYSTAL_REFRACTION("Faceted Light"),// 1: Voronoi gem/crystal facets — gap-free
    MIDNIGHT("Midnight"),               // 2: True black AMOLED, Aurora Drift starved of color
    NEON_FILAMENTS("Filaments"),        // 3: True black AMOLED, noise gradient ridge glowing filaments
    POLY_MESH("Poly Mesh")             // 4: Original low-poly 3D-lit triangle mesh
}

data class WallpaperPreferences(
    val wallpaperStyle: WallpaperStyle = WallpaperStyle.AURORA_DRIFT,
    val animationSpeed: Float = 0.5f,       // 0.1f to 2.0f
    val intensity: Float = 0.8f,            // 0.3f to 1.5f
    val enabledMusicApps: Set<String> = defaultMusicApps
) {
    companion object {
        const val PREFS_NAME = "WallpaperPreferences"

        const val KEY_WALLPAPER_STYLE = "wallpaper_style_preset"
        const val KEY_ANIMATION_SPEED = "animation_speed_val"
        const val KEY_INTENSITY = "intensity_val"
        const val KEY_ENABLED_MUSIC_APPS = "enabled_music_apps_set"

        val defaultMusicApps: Set<String> = setOf(
            "spotify", "youtube", "music", "pandora",
            "soundcloud", "apple", "tidal", "deezer"
        )

        fun load(context: Context): WallpaperPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stylePresetName = prefs.getString(KEY_WALLPAPER_STYLE, WallpaperStyle.AURORA_DRIFT.name)
                ?: WallpaperStyle.AURORA_DRIFT.name
            val wallpaperStyle = try {
                WallpaperStyle.valueOf(stylePresetName)
            } catch (e: Exception) {
                WallpaperStyle.AURORA_DRIFT
            }

            val speed = prefs.getFloat(KEY_ANIMATION_SPEED, 0.5f)
            val intensity = prefs.getFloat(KEY_INTENSITY, 0.8f)
            val apps = prefs.getStringSet(KEY_ENABLED_MUSIC_APPS, defaultMusicApps) ?: defaultMusicApps

            return WallpaperPreferences(
                wallpaperStyle = wallpaperStyle,
                animationSpeed = speed,
                intensity = intensity,
                enabledMusicApps = apps
            )
        }

        fun save(context: Context, preferences: WallpaperPreferences) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_WALLPAPER_STYLE, preferences.wallpaperStyle.name)
                .putFloat(KEY_ANIMATION_SPEED, preferences.animationSpeed)
                .putFloat(KEY_INTENSITY, preferences.intensity)
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
