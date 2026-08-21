package com.music.wallpaper.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.music.wallpaper.managers.ColorPaletteManager
import com.music.wallpaper.models.ColorPalette
import com.music.wallpaper.models.WallpaperPreferences
import com.music.wallpaper.models.WallpaperStyle
import com.music.wallpaper.services.MusicListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayingTrackInfo(
    val title: String? = null,
    val artist: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application),
    ColorPaletteManager.ColorPaletteListener {

    private val _preferences = MutableStateFlow(WallpaperPreferences.load(application))
    val preferences: StateFlow<WallpaperPreferences> = _preferences.asStateFlow()

    private val _currentPalette = MutableStateFlow(
        ColorPaletteManager.getInstance().getCurrentPalette(application) ?: ColorPalette.getDefaultPalette()
    )
    val currentPalette: StateFlow<ColorPalette> = _currentPalette.asStateFlow()

    private val _trackInfo = MutableStateFlow(loadTrackInfo())
    val trackInfo: StateFlow<PlayingTrackInfo> = _trackInfo.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra(MusicListenerService.EXTRA_TRACK_TITLE)
            val artist = intent?.getStringExtra(MusicListenerService.EXTRA_ARTIST_NAME)
            if (title != null || artist != null) {
                _trackInfo.value = PlayingTrackInfo(title, artist)
            }
        }
    }

    init {
        ColorPaletteManager.getInstance().addListener(this)
        LocalBroadcastManager.getInstance(application).registerReceiver(
            receiver,
            IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED)
        )
    }

    fun updateWallpaperStyle(style: WallpaperStyle) {
        val updated = _preferences.value.copy(wallpaperStyle = style)
        _preferences.value = updated
        WallpaperPreferences.save(getApplication(), updated)
    }

    fun updateSpeed(speed: Float) {
        val updated = _preferences.value.copy(animationSpeed = speed)
        _preferences.value = updated
        WallpaperPreferences.save(getApplication(), updated)
    }

    fun updateIntensity(intensity: Float) {
        val updated = _preferences.value.copy(intensity = intensity)
        _preferences.value = updated
        WallpaperPreferences.save(getApplication(), updated)
    }



    fun toggleMusicApp(appKey: String, enabled: Boolean) {
        val currentApps = _preferences.value.enabledMusicApps.toMutableSet()
        if (enabled) {
            currentApps.add(appKey)
        } else {
            currentApps.remove(appKey)
        }
        val updated = _preferences.value.copy(enabledMusicApps = currentApps)
        _preferences.value = updated
        WallpaperPreferences.save(getApplication(), updated)
    }

    fun resetToDefaults() {
        val defaults = WallpaperPreferences()
        _preferences.value = defaults
        WallpaperPreferences.save(getApplication(), defaults)
    }

    override fun onColorPaletteChanged(newPalette: ColorPalette?) {
        if (newPalette != null) {
            _currentPalette.value = newPalette
        }
    }

    private fun loadTrackInfo(): PlayingTrackInfo {
        val prefs = getApplication<Application>().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        val title = prefs.getString("last_track_title", null)
        val artist = prefs.getString("last_artist_name", null)
        return PlayingTrackInfo(title, artist)
    }

    override fun onCleared() {
        super.onCleared()
        ColorPaletteManager.getInstance().removeListener(this)
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(receiver)
    }
}
