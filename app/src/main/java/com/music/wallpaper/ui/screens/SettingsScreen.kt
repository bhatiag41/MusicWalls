package com.music.wallpaper.ui.screens

import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.music.wallpaper.R
import com.music.wallpaper.models.PaletteStyle
import com.music.wallpaper.models.WallpaperPreferences
import com.music.wallpaper.palette.PaletteCrossfader
import com.music.wallpaper.renderer.ShaderRenderer
import com.music.wallpaper.ui.components.*
import com.music.wallpaper.ui.theme.DarkBackground
import com.music.wallpaper.ui.theme.GlassBorder
import com.music.wallpaper.ui.theme.LocalGlassPalette
import com.music.wallpaper.ui.theme.TextMuted
import com.music.wallpaper.ui.theme.TextPrimary
import com.music.wallpaper.ui.theme.TextSecondary
import com.music.wallpaper.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsState()
    val currentPalette by viewModel.currentPalette.collectAsState()
    val scrollState = rememberScrollState()

    // Get dominant/vibrant color for accent tinting
    val accentColor = remember(currentPalette) {
        val colors = currentPalette.allColors
        if (colors.isNotEmpty()) {
            val vibrant = if (colors.size > 1) colors[1] else colors[0]
            Color(vibrant)
        } else {
            Color(0xFF818CF8)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Defaults",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Wallpaper Preview Card
            Text(
                text = "LIVE PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassPalette.current.accent
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
            ) {
                LivePreviewThumbnail(
                    preferences = preferences,
                    colors = currentPalette.toFloatArray()
                )
            }

            // Animation & Motion Settings
            Text(
                text = "MOTION & FLUIDITY",
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassPalette.current.accent
            )

            GlassCard {
                GlassSlider(
                    value = preferences.animationSpeed,
                    onValueChange = { viewModel.updateSpeed(it) },
                    valueRange = 0.1f..1.5f,
                    title = "Drift Speed",
                    valueDisplay = String.format("%.1fx", preferences.animationSpeed * 2)
                )

                Spacer(modifier = Modifier.height(20.dp))

                GlassSlider(
                    value = preferences.intensity,
                    onValueChange = { viewModel.updateIntensity(it) },
                    valueRange = 0.3f..1.5f,
                    title = "Color Intensity",
                    valueDisplay = "${(preferences.intensity * 100).toInt()}%"
                )
            }

            // Visual Texture & Styling
            Text(
                text = "ATMOSPHERE & TEXTURE",
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassPalette.current.accent
            )

            GlassCard {
                Text(
                    text = "Palette Tone",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassSegmentedControl(
                    items = PaletteStyle.entries,
                    selectedItem = preferences.paletteStyle,
                    onItemSelected = { viewModel.updatePaletteStyle(it) },
                    labelProvider = {
                        when (it) {
                            PaletteStyle.VIBRANT -> "Vibrant"
                            PaletteStyle.MUTED -> "Muted"
                            PaletteStyle.DARK -> "Ambient Dark"
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                GlassToggle(
                    checked = preferences.grainEnabled,
                    onCheckedChange = { viewModel.updateGrainEnabled(it) },
                    title = "Animated Film Grain",
                    subtitle = "3% micro-dither to eliminate banding and flat digital feel"
                )
            }

            // Music Detection Sources
            Text(
                text = "MUSIC SOURCES",
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassPalette.current.accent
            )

            GlassCard {
                val appNames = mapOf(
                    "spotify" to "Spotify",
                    "youtube" to "YouTube Music",
                    "music" to "Apple Music & Others",
                    "pandora" to "Pandora",
                    "soundcloud" to "SoundCloud",
                    "tidal" to "Tidal",
                    "deezer" to "Deezer"
                )

                appNames.entries.forEachIndexed { index, (key, name) ->
                    val isEnabled = preferences.isMusicAppEnabled(key)
                    GlassToggle(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.toggleMusicApp(key, it) },
                        title = name
                    )
                    if (index < appNames.size - 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun LivePreviewThumbnail(
    preferences: WallpaperPreferences,
    colors: FloatArray,
    modifier: Modifier = Modifier
) {
    val crossfader = remember { PaletteCrossfader() }
    val renderer = remember { ShaderRenderer.create() }

    LaunchedEffect(colors) {
        crossfader.setTargetPalette(colors)
    }

    LaunchedEffect(preferences.animationSpeed) {
        renderer.setSpeed(preferences.animationSpeed)
    }

    LaunchedEffect(preferences.intensity) {
        renderer.setIntensity(preferences.intensity)
    }

    LaunchedEffect(preferences.grainEnabled) {
        renderer.setGrainEnabled(preferences.grainEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                var isRunning = true
                val thread = HandlerThread("PreviewDrawThread").apply { start() }
                val handler = Handler(thread.looper)
                var lastTime = System.nanoTime()

                val drawRunnable = object : Runnable {
                    override fun run() {
                        if (!isRunning) return
                        val holder = holder
                        val now = System.nanoTime()
                        val dt = (now - lastTime) / 1_000_000_000f
                        lastTime = now

                        val activeColors = crossfader.update(dt)
                        renderer.setColors(activeColors)

                        var canvas: Canvas? = null
                        try {
                            canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                try {
                                    holder.lockHardwareCanvas()
                                } catch (_: Exception) {
                                    holder.lockCanvas()
                                }
                            } else {
                                holder.lockCanvas()
                            }
                            if (canvas != null) {
                                renderer.draw(canvas)
                            }
                        } catch (_: Exception) {
                        } finally {
                            if (canvas != null) {
                                try {
                                    holder.unlockCanvasAndPost(canvas)
                                } catch (_: Exception) {
                                }
                            }
                        }

                        if (isRunning) {
                            handler.postDelayed(this, 33L)
                        }
                    }
                }

                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        isRunning = true
                        lastTime = System.nanoTime()
                        handler.removeCallbacks(drawRunnable)
                        handler.post(drawRunnable)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        handler.post {
                            renderer.onSurfaceChanged(width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        isRunning = false
                        handler.removeCallbacks(drawRunnable)
                        thread.quitSafely()
                    }
                })
            }
        }
    )
}
