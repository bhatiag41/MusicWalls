package com.music.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.music.wallpaper.managers.ColorPaletteManager
import com.music.wallpaper.models.ColorPalette
import com.music.wallpaper.models.WallpaperPreferences
import com.music.wallpaper.services.MusicListenerService
import com.music.wallpaper.ui.ComposeSettingsActivity
import com.music.wallpaper.ui.components.GlassCard
import com.music.wallpaper.ui.components.GlassPrimaryButton
import com.music.wallpaper.ui.components.GlassSecondaryButton
import com.music.wallpaper.ui.screens.LivePreviewThumbnail
import com.music.wallpaper.ui.theme.DarkBackground
import com.music.wallpaper.ui.theme.GlassBorder
import com.music.wallpaper.ui.theme.GlassSurface
import com.music.wallpaper.ui.theme.GlassTheme
import com.music.wallpaper.ui.theme.LocalGlassPalette
import com.music.wallpaper.ui.theme.TextMuted
import com.music.wallpaper.ui.theme.TextPrimary
import com.music.wallpaper.ui.theme.TextSecondary
import com.music.wallpaper.utils.PermissionManager

class MainActivity : ComponentActivity(), ColorPaletteManager.ColorPaletteListener {

    private val currentPaletteState = mutableStateOf(ColorPalette.getDefaultPalette())
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ColorPaletteManager.getInstance().addListener(this)
        currentPaletteState.value = ColorPaletteManager.getInstance().getCurrentPalette(this) ?: ColorPalette.getDefaultPalette()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED)
        )

        loadPersistedTrackInfo()

        setContent {
            val palette = currentPaletteState.value
            val accentColor = remember(palette) {
                val colors = palette.allColors
                if (colors.isNotEmpty()) {
                    val c = if (colors.size > 1) colors[1] else colors[0]
                    Color(c)
                } else {
                    Color(0xFF818CF8)
                }
            }

            GlassTheme(accentColor = accentColor) {
                MainScreen(
                    currentPalette = palette,
                    trackTitle = trackTitleState.value,
                    artistName = artistNameState.value,
                    hasNotificationPermission = permissionGrantedState.value,
                    onRequestPermission = {
                        PermissionManager.openNotificationListenerSettings(this)
                    },
                    onSetWallpaper = {
                        PermissionManager.openLiveWallpaperSettings(this)
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, ComposeSettingsActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionGrantedState.value = PermissionManager.isNotificationListenerEnabled(this)
        loadPersistedTrackInfo()
    }

    override fun onColorPaletteChanged(newPalette: ColorPalette?) {
        if (newPalette != null) {
            currentPaletteState.value = newPalette
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

@Composable
fun MainScreen(
    currentPalette: ColorPalette,
    trackTitle: String?,
    artistName: String?,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { WallpaperPreferences.load(context) }
    val palette = LocalGlassPalette.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen live ambient background
        LivePreviewThumbnail(
            preferences = preferences,
            colors = currentPalette.toFloatArray(),
            modifier = Modifier.fillMaxSize()
        )

        // Subtle gradient scrim overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBackground.copy(alpha = 0.45f),
                            DarkBackground.copy(alpha = 0.75f),
                            DarkBackground.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                    Text(
                        text = "FLUID AMBIENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Music Walls",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Dynamic ethereal light synced to your sound",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Central Cards: Music Status & Permissions
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Currently Playing Card
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT TRACK",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent
                        )

                        if (trackTitle != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "SYNCED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (trackTitle != null) {
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (artistName != null) {
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "No music playing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Play a song in Spotify, Apple Music, or YouTube to extract live colors",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Notification Permission Alert (if not granted)
                AnimatedVisibility(visible = !hasNotificationPermission) {
                    GlassCard(
                        borderColor = Color(0xFFF59E0B).copy(alpha = 0.3f),
                        backgroundColor = Color(0xFF1E1608).copy(alpha = 0.7f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Access Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Needed to detect current track album artwork.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        GlassSecondaryButton(
                            text = "Grant Permission",
                            onClick = onRequestPermission
                        )
                    }
                }
            }

            // Bottom Actions
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPrimaryButton(
                    text = "Apply Live Wallpaper",
                    onClick = onSetWallpaper
                )

                GlassSecondaryButton(
                    text = "Settings & Customization",
                    onClick = onOpenSettings
                )
            }
        }
    }
}
