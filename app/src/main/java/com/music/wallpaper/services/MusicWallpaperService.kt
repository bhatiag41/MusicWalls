package com.music.wallpaper.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.music.wallpaper.managers.ColorPaletteManager
import com.music.wallpaper.models.ColorPalette
import com.music.wallpaper.models.WallpaperPreferences
import com.music.wallpaper.palette.PaletteCrossfader
import com.music.wallpaper.renderer.ShaderRenderer

/**
 * Battery-optimized Live Wallpaper Service with AGSL / Canvas fallback shader engine.
 * Cap render to 30fps, pauses completely on visibility loss, and transitions colors smoothly via OKLab crossfader.
 */
class MusicWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "MusicWallpaperService"
        const val PREF_MUSIC_EVER_RECEIVED = "music_ever_received"
        private const val FRAME_INTERVAL_MS = 33L // ~30 fps cap
        private const val CYCLE_INTERVAL_MS = 12000L
    }

    override fun onCreateEngine(): Engine {
        return FluidShaderEngine()
    }

    private inner class FluidShaderEngine : Engine(), ColorPaletteManager.ColorPaletteListener {

        private var renderer: ShaderRenderer? = null
        private val crossfader = PaletteCrossfader()

        private var handlerThread: HandlerThread? = null
        private var drawHandler: Handler? = null

        private var visible = false
        private var surfaceHolder: SurfaceHolder? = null
        private var musicEverReceived = false

        private var lastFrameNanos: Long = 0L

        // Cycler for default ambient palettes when no music has played yet
        private val defaultPalettes = listOf(
            floatArrayOf(
                0.70f, 0.23f, 0.44f, 1f, // Magenta/Rose
                0.48f, 0.25f, 0.63f, 1f, // Purple
                0.18f, 0.31f, 0.66f, 1f, // Deep Blue
                0.12f, 0.50f, 0.53f, 1f, // Cyan/Teal
                0.61f, 0.48f, 0.12f, 1f  // Amber
            ),
            floatArrayOf(
                0.05f, 0.28f, 0.63f, 1f, // Blue
                0.08f, 0.40f, 0.75f, 1f, // Royal Blue
                0.10f, 0.46f, 0.82f, 1f, // Light Blue
                0.00f, 0.51f, 0.56f, 1f, // Teal
                0.00f, 0.38f, 0.39f, 1f  // Dark Teal
            ),
            floatArrayOf(
                0.75f, 0.21f, 0.05f, 1f, // Orange-Red
                0.90f, 0.29f, 0.10f, 1f, // Coral
                0.96f, 0.49f, 0.00f, 1f, // Orange
                1.00f, 0.56f, 0.00f, 1f, // Amber
                0.42f, 0.11f, 0.60f, 1f  // Deep Violet
            ),
            floatArrayOf(
                0.11f, 0.37f, 0.13f, 1f, // Forest
                0.18f, 0.49f, 0.20f, 1f, // Green
                0.22f, 0.56f, 0.24f, 1f, // Emerald
                0.00f, 0.41f, 0.36f, 1f, // Teal-Green
                0.00f, 0.30f, 0.25f, 1f  // Dark Pine
            )
        )
        private var cycleIndex = 0

        private val drawRunner = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                drawHandler?.removeCallbacks(this)
                drawHandler?.postDelayed(this, FRAME_INTERVAL_MS)
            }
        }

        private val colorCycleRunner = object : Runnable {
            override fun run() {
                if (!visible || musicEverReceived) return
                cycleIndex = (cycleIndex + 1) % defaultPalettes.size
                crossfader.setTargetPalette(defaultPalettes[cycleIndex])
                drawHandler?.removeCallbacks(this)
                drawHandler?.postDelayed(this, CYCLE_INTERVAL_MS)
            }
        }

        private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
        private var localColorReceiver: BroadcastReceiver? = null
        private var globalColorReceiver: BroadcastReceiver? = null

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            this.surfaceHolder = holder

            val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            musicEverReceived = prefs.getBoolean(PREF_MUSIC_EVER_RECEIVED, false)

            handlerThread = HandlerThread("FluidWallpaperDrawThread", android.os.Process.THREAD_PRIORITY_DISPLAY).apply {
                start()
                drawHandler = Handler(looper)
            }

            renderer = ShaderRenderer.create()
            applyCurrentSettings()

            // Initialize crossfader with default or saved colors
            val savedPalette = loadLastPaletteFromPrefs()
            if (savedPalette != null) {
                crossfader.setTargetPalette(savedPalette.toFloatArray())
            } else {
                crossfader.setTargetPalette(defaultPalettes[0])
            }

            ColorPaletteManager.getInstance().addListener(this)
            registerReceivers()
            registerPrefsListener()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.surfaceHolder = holder
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.surfaceHolder = holder

            drawHandler?.post {
                renderer?.onSurfaceChanged(width, height)
                drawFrame()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            drawHandler?.removeCallbacks(drawRunner)
            drawHandler?.removeCallbacks(colorCycleRunner)
            if (visible) {
                lastFrameNanos = System.nanoTime()
                drawHandler?.post(drawRunner)
                if (!musicEverReceived) {
                    drawHandler?.postDelayed(colorCycleRunner, CYCLE_INTERVAL_MS)
                }
            }
        }

        private fun drawFrame() {
            if (!visible) return
            val holder = surfaceHolder ?: return

            val now = System.nanoTime()
            val dt = if (lastFrameNanos > 0) (now - lastFrameNanos) / 1_000_000_000f else 0.033f
            lastFrameNanos = now

            // Update color crossfade
            val activeColors = crossfader.update(dt)
            renderer?.setColors(activeColors)

            var canvas: Canvas? = null
            try {
                canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        holder.lockHardwareCanvas()
                    } catch (e: Exception) {
                        holder.lockCanvas()
                    }
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    renderer?.draw(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Draw error: ${e.message}")
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "UnlockCanvas error", e)
                    }
                }
            }
        }

        private fun applyCurrentSettings() {
            val settings = WallpaperPreferences.load(this@MusicWallpaperService)
            renderer?.setSpeed(settings.animationSpeed)
            renderer?.setIntensity(settings.intensity)
            renderer?.setGrainEnabled(settings.grainEnabled)
        }

        private fun onMusicPaletteReceived(palette: ColorPalette?) {
            if (palette == null) return
            if (!musicEverReceived) {
                musicEverReceived = true
                getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_MUSIC_EVER_RECEIVED, true).apply()
            }
            drawHandler?.removeCallbacks(colorCycleRunner)
            crossfader.setTargetPalette(palette.toFloatArray())
        }

        override fun onColorPaletteChanged(newPalette: ColorPalette?) {
            onMusicPaletteReceived(newPalette)
        }

        private fun registerReceivers() {
            localColorReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == MusicListenerService.ACTION_COLOR_PALETTE_CHANGED) {
                        handlePaletteIntent(intent)
                    }
                }
            }
            LocalBroadcastManager.getInstance(this@MusicWallpaperService).registerReceiver(
                localColorReceiver!!,
                IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED)
            )

            globalColorReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == MusicListenerService.ACTION_COLOR_PALETTE_CHANGED) {
                        handlePaletteIntent(intent)
                    }
                }
            }
            this@MusicWallpaperService.registerReceiver(
                globalColorReceiver,
                IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED)
            )
        }

        private fun handlePaletteIntent(intent: Intent) {
            val json = intent.getStringExtra(MusicListenerService.EXTRA_COLOR_PALETTE_JSON)
            if (json != null) {
                val palette = ColorPalette.fromJsonString(json)
                onMusicPaletteReceived(palette)
            }
        }

        private fun registerPrefsListener() {
            prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == WallpaperPreferences.KEY_ANIMATION_SPEED ||
                    key == WallpaperPreferences.KEY_INTENSITY ||
                    key == WallpaperPreferences.KEY_GRAIN_ENABLED ||
                    key == WallpaperPreferences.KEY_PALETTE_STYLE
                ) {
                    drawHandler?.post { applyCurrentSettings() }
                }
            }
            getSharedPreferences(WallpaperPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
        }

        private fun loadLastPaletteFromPrefs(): ColorPalette? {
            return try {
                val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                val count = prefs.getInt("color_count", 0)
                if (count > 0) {
                    val colors = ArrayList<Int>()
                    for (i in 0 until count) {
                        colors.add(prefs.getInt("color_$i", 0))
                    }
                    ColorPalette(colors)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading palette", e)
                null
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            drawHandler?.removeCallbacks(drawRunner)
            drawHandler?.removeCallbacks(colorCycleRunner)
            handlerThread?.quitSafely()

            ColorPaletteManager.getInstance().removeListener(this)

            localColorReceiver?.let {
                LocalBroadcastManager.getInstance(this@MusicWallpaperService).unregisterReceiver(it)
            }
            globalColorReceiver?.let {
                try { unregisterReceiver(it) } catch (e: Exception) { Log.w(TAG, "Unregister global receiver", e) }
            }
            prefsListener?.let {
                getSharedPreferences(WallpaperPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(it)
            }

            renderer?.release()
            renderer = null
        }
    }
}
