package com.music.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.music.wallpaper.managers.ColorPaletteManager
import com.music.wallpaper.models.ColorPalette
import com.music.wallpaper.models.WallpaperPreferences
import com.music.wallpaper.models.WallpaperStyle
import com.music.wallpaper.palette.PaletteCrossfader
import com.music.wallpaper.renderer.ShaderRenderer
import com.music.wallpaper.services.MusicListenerService
import com.music.wallpaper.ui.components.*
import com.music.wallpaper.ui.theme.*
import com.music.wallpaper.ui.viewmodel.SettingsViewModel
import com.music.wallpaper.utils.PermissionManager
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

class MainActivity : ComponentActivity(), ColorPaletteManager.ColorPaletteListener {

    private val viewModel: SettingsViewModel by viewModels()

    private val currentArtworkState = mutableStateOf<Bitmap?>(null)
    private val trackTitleState = mutableStateOf<String?>(null)
    private val artistNameState = mutableStateOf<String?>(null)
    private val permissionGrantedState = mutableStateOf(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra(MusicListenerService.EXTRA_TRACK_TITLE)
            val artist = intent?.getStringExtra(MusicListenerService.EXTRA_ARTIST_NAME)
            if (title != null || artist != null) {
                trackTitleState.value = title
                artistNameState.value = artist
            }
            currentArtworkState.value = ColorPaletteManager.getInstance().getCurrentArtwork(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ColorPaletteManager.getInstance().addListener(this)
        currentArtworkState.value = ColorPaletteManager.getInstance().getCurrentArtwork(this)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED)
        )

        loadPersistedTrackInfo()

        setContent {
            val palette by viewModel.currentPalette.collectAsState()
            val accentColor = remember(palette) {
                val colors = palette.allColors
                if (colors.isNotEmpty()) {
                    val c = if (colors.size > 1) colors[1] else colors[0]
                    // Clamp to soft pastel — never neon/over-saturated
                    toPremiumAccent(Color(c))
                } else {
                    Color(0xFF8B5CF6)
                }
            }

            GlassTheme(accentColor = accentColor) {
                MainScreen(
                    viewModel = viewModel,
                    currentPalette = palette,
                    currentArtwork = currentArtworkState.value,
                    trackTitle = trackTitleState.value,
                    artistName = artistNameState.value,
                    hasNotificationPermission = permissionGrantedState.value,
                    onRequestPermission = {
                        PermissionManager.openNotificationListenerSettings(this)
                    },
                    onSetWallpaper = {
                        PermissionManager.openLiveWallpaperSettings(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionGrantedState.value = PermissionManager.isNotificationListenerEnabled(this)
        currentArtworkState.value = ColorPaletteManager.getInstance().getCurrentArtwork(this)
        loadPersistedTrackInfo()
    }

    override fun onColorPaletteChanged(newPalette: ColorPalette?) {
        if (newPalette != null) {
            currentArtworkState.value = ColorPaletteManager.getInstance().getCurrentArtwork(this)
        }
    }

    private fun loadPersistedTrackInfo() {
        val prefs = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
        trackTitleState.value = prefs.getString("last_track_title", null)
        artistNameState.value = prefs.getString("last_artist_name", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        ColorPaletteManager.getInstance().removeListener(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SettingsViewModel,
    currentPalette: ColorPalette,
    currentArtwork: Bitmap?,
    trackTitle: String?,
    artistName: String?,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsState()
    val theme = LocalAppThemeColors.current
    var isSettingsPanelOpen by remember { mutableStateOf(false) }

    val musicAppMap = remember {
        mapOf(
            "spotify" to "Spotify",
            "youtube" to "YouTube Music",
            "music" to "Apple Music",
            "pandora" to "Pandora",
            "soundcloud" to "SoundCloud",
            "tidal" to "Tidal",
            "deezer" to "Deezer"
        )
    }

    // Luminance Calculation for dynamic contrast text
    val dominantColor = remember(currentPalette) {
        val colors = currentPalette.allColors
        if (colors.isNotEmpty()) Color(colors[0]) else Color(0xFF000000)
    }
    
    val luminance = remember(dominantColor) {
        0.2126f * dominantColor.red + 0.7152f * dominantColor.green + 0.0722f * dominantColor.blue
    }
    
    // Crossfade text color over 1 second
    val dynamicTextColor by animateColorAsState(
        targetValue = if (luminance > 0.5f) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
        animationSpec = tween(durationMillis = 1000),
        label = "ContrastTextColor"
    )

    val scaffoldState = rememberBottomSheetScaffoldState()
    val isExpanded = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    val dragHandleProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f, 
        label = "dragHandle"
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 140.dp,
        sheetContainerColor = DarkBackground.copy(alpha = 0.95f),
        sheetContentColor = TextPrimary,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(32.dp, 8.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    val leftStartY = androidx.compose.ui.util.lerp(size.height, size.height / 2f, dragHandleProgress)
                    val centerPointY = androidx.compose.ui.util.lerp(0f, size.height / 2f, dragHandleProgress)
                    val rightEndY = androidx.compose.ui.util.lerp(size.height, size.height / 2f, dragHandleProgress)
                    
                    drawLine(
                        color = TextPrimary,
                        start = androidx.compose.ui.geometry.Offset(0f, leftStartY),
                        end = androidx.compose.ui.geometry.Offset(size.width / 2f, centerPointY),
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        color = TextPrimary,
                        start = androidx.compose.ui.geometry.Offset(size.width / 2f, centerPointY),
                        end = androidx.compose.ui.geometry.Offset(size.width, rightEndY),
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Primary Apply Button inside the sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    PrimaryActionButton(
                        text = "Set as Wallpaper",
                        onClick = onSetWallpaper
                    )
                }
                
                // Permission Alert — native app card style, no harsh OS colors
                AnimatedVisibility(visible = !hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notification access required",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Needed to detect current music.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            SecondaryActionButton(
                                text = "Grant access",
                                onClick = onRequestPermission
                            )
                        }
                    }
                }

                // Scrollable settings area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) {
                    SettingSectionHeader(title = "Settings")

                    SettingStylePillsRow(
                        selectedStyle = preferences.wallpaperStyle,
                        onSelectStyle = { viewModel.updateWallpaperStyle(it) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingSliderRow(
                        title = "Speed",
                        value = preferences.animationSpeed,
                        onValueChange = { viewModel.updateSpeed(it) },
                        valueRange = 0.1f..1.5f,
                        valueDisplay = String.format("%.1fx", preferences.animationSpeed * 2)
                    )

                    SettingSliderRow(
                        title = "Intensity",
                        value = preferences.intensity,
                        onValueChange = { viewModel.updateIntensity(it) },
                        valueRange = 0.3f..1.5f,
                        valueDisplay = "${(preferences.intensity * 100).toInt()}%"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSectionHeader(title = "AUDIO")

                    MusicSourcesDropdown(
                        allApps = musicAppMap,
                        enabledApps = preferences.enabledMusicApps,
                        onToggleApp = { key, isEnabled ->
                            viewModel.toggleMusicApp(key, isEnabled)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Made with ❤️ by Gaurav Bhatia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        uriHandler.openUri("https://github.com/bhatiag41/MusicWalls")
                                    }
                                    .padding(4.dp)
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Privacy Policy",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        android.widget.Toast.makeText(
                                            context,
                                            "I don't have time or tokens to collect any data",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    .padding(4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // SYNORA watermark — subtle vertical fade into transparent
                        Text(
                            text = "SYNORA",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = TextUnit(88f, TextUnitType.Sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = TextUnit(-3f, TextUnitType.Sp),
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        TextSecondary.copy(alpha = 0.10f),
                                        Color.Transparent
                                    )
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = 48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Background layer: Full Bleed Live Preview
                LivePreviewThumbnail(
                    palette = currentPalette,
                    preferences = preferences,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient scrim — legibility on bright album art backgrounds
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.28f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Foreground Overlays directly on top of the live preview
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header text — always readable against the scrim above
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Synora",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "where music meets your walls",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Track Info positioned just above the peek height
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp + 24.dp) // Offset by peek height + some padding
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Album art swatch — only shown when a track is actively detected
                        if (currentArtwork != null && !currentArtwork.isRecycled) {
                            Image(
                                bitmap = currentArtwork.asImageBitmap(),
                                contentDescription = "Album Artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(Radius.sm))
                            )
                        } else {
                            // No music — neutral icon, no placeholder colored box
                            Icon(
                                    imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trackTitle ?: "No music playing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = artistName ?: "Play a song to sync colors",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SettingStylePillsRow(
    selectedStyle: WallpaperStyle,
    onSelectStyle: (WallpaperStyle) -> Unit
) {
    val theme = LocalAppThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Style",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WallpaperStyle.entries.forEach { style ->
                val isSelected = style == selectedStyle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(if (isSelected) theme.accent else DarkSurfaceVariant)
                        .border(1.dp, if (isSelected) theme.accent else DarkBorder, RoundedCornerShape(Radius.xs))
                        .clickable { onSelectStyle(style) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.displayName,
                        color = if (isSelected) Color.White else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun LivePreviewThumbnail(
    palette: ColorPalette,
    preferences: WallpaperPreferences,
    modifier: Modifier = Modifier
) {
    val crossfader = remember { PaletteCrossfader() }
    val renderer = remember { ShaderRenderer.create() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(palette, preferences.wallpaperStyle) {
        val finalPalette = if (palette == ColorPalette.getDefaultPalette() && preferences.wallpaperStyle != com.music.wallpaper.models.WallpaperStyle.AURORA_DRIFT) {
            ColorPalette.getAestheticDefaultPalette()
        } else {
            palette
        }
        crossfader.setTargetPalette(finalPalette.toFloatArray())
    }

    LaunchedEffect(preferences.wallpaperStyle) {
        renderer.setStyle(preferences.wallpaperStyle)
    }

    LaunchedEffect(preferences.animationSpeed) {
        renderer.setSpeed(preferences.animationSpeed)
    }

    LaunchedEffect(preferences.intensity) {
        renderer.setIntensity(preferences.intensity)
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    var startRenderLoop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var stopRenderLoop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isSurfaceValid by remember { mutableStateOf<(() -> Boolean)?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isSurfaceValid?.invoke() == true) {
                    startRenderLoop?.invoke()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                stopRenderLoop?.invoke()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                var isRunning = false
                var thread: HandlerThread? = null
                var handler: Handler? = null
                var lastTime = System.nanoTime()

                val drawRunnable = object : Runnable {
                    override fun run() {
                        if (!isRunning) return
                        val h = holder
                        if (!h.surface.isValid) return
                        
                        val now = System.nanoTime()
                        val rawDt = (now - lastTime) / 1_000_000_000f
                        val dt = rawDt.coerceIn(0.001f, 0.05f)
                        lastTime = now

                        val activeColors = crossfader.update(dt)
                        renderer.setColors(activeColors)

                        var canvas: Canvas? = null
                        try {
                            canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                try { h.lockHardwareCanvas() } catch (_: Exception) { h.lockCanvas() }
                            } else {
                                h.lockCanvas()
                            }
                            if (canvas != null) {
                                renderer.draw(canvas)
                            }
                        } catch (_: Exception) {
                        } finally {
                            if (canvas != null) {
                                try { h.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                            }
                        }

                        if (isRunning) {
                            handler?.removeCallbacks(this)
                            handler?.postDelayed(this, 33L)
                        }
                    }
                }

                val start = {
                    if (!isRunning) {
                        isRunning = true
                        lastTime = System.nanoTime()
                        if (thread == null || !thread!!.isAlive) {
                            thread = HandlerThread("PreviewDrawThread").apply { start() }
                            handler = Handler(thread!!.looper)
                        }
                        handler?.removeCallbacks(drawRunnable)
                        handler?.post(drawRunnable)
                    }
                }

                val stop = {
                    isRunning = false
                    handler?.removeCallbacks(drawRunnable)
                    thread?.quitSafely()
                    thread = null
                    handler = null
                }

                startRenderLoop = start
                stopRenderLoop = stop
                isSurfaceValid = { holder.surface.isValid }

                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        start()
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        handler?.post {
                            renderer.onSurfaceChanged(width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        stop()
                    }
                })
            }
        }
    )
}
