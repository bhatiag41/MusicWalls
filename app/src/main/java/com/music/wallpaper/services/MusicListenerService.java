package com.music.wallpaper.services;

import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.music.wallpaper.managers.ColorPaletteManager;
import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.MusicMetadata;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.utils.ColorExtractor;

import java.util.List;

/**
 * Notification listener service to detect music notifications and extract color palettes.
 * Enhanced with ColorPaletteManager for instant wallpaper updates.
 */
public class MusicListenerService extends NotificationListenerService {
    
    private static final String TAG = "MusicListenerService";
    
    public static final String ACTION_COLOR_PALETTE_CHANGED = "com.music.wallpaper.COLOR_PALETTE_CHANGED";
    public static final String EXTRA_COLOR_PALETTE_JSON = "color_palette_json";
    public static final String EXTRA_MUSIC_METADATA = "music_metadata";
    
    private WallpaperSettings settings;
    private long lastUpdateTime = 0;
    private static final long UPDATE_THROTTLE_MS = 2000; // Max once per 2 seconds
    
    @Override
    public void onCreate() {
        super.onCreate();
        settings = WallpaperSettings.loadFromPreferences(this);
        Log.d(TAG, "MusicListenerService created");
    }
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            // Reload settings to pick up changes
            settings = WallpaperSettings.loadFromPreferences(this);
            
            // Check if this is a music notification from enabled apps
            if (!isMusicNotification(sbn)) {
                return;
            }
            
            Log.d(TAG, "Music notification detected from: " + sbn.getPackageName());
            
            // Extract music metadata
            MusicMetadata metadata = extractMetadata(sbn);
            if (metadata == null) {
                Log.w(TAG, "Failed to extract metadata");
                return;
            }
            
            Log.d(TAG, "Extracted metadata: " + metadata.getTrackTitle() + " by " + metadata.getArtistName());
            
            // Extract album artwork
            Bitmap albumArt = metadata.getAlbumArtBitmap();
            if (albumArt == null) {
                Log.d(TAG, "No album artwork, using default palette");
                updateColorPalette(ColorPalette.getDefaultPalette(), metadata);
                return;
            }
            
            // Extract color palette from artwork
            ColorPalette palette = ColorExtractor.extractPalette(albumArt);
            Log.d(TAG, "Extracted palette: " + palette);
            
            // Update wallpaper with new palette
            updateColorPalette(palette, metadata);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isMusicNotification(sbn)) {
            Log.d(TAG, "Music notification removed");
            // Could optionally reset to default palette here
        }
    }
    
    /**
     * Checks if notification is from a music app.
     */
    private boolean isMusicNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName().toLowerCase();
        
        // Check against enabled music apps in settings
        return settings.isMusicAppEnabled(packageName);
    }
    
    /**
     * Extracts music metadata from notification.
     */
    private MusicMetadata extractMetadata(StatusBarNotification sbn) {
        // Try MediaSession first (most reliable)
        MusicMetadata metadata = extractFromMediaSession();
        if (metadata != null) {
            return metadata;
        }
        
        // Fallback to notification extras
        return extractFromNotification(sbn);
    }
    
    /**
     * Extracts metadata from MediaSession.
     */
    private MusicMetadata extractFromMediaSession() {
        try {
            MediaSessionManager sessionManager = (MediaSessionManager)
                getSystemService(MEDIA_SESSION_SERVICE);
            
            if (sessionManager == null) {
                return null;
            }
            
            List<MediaController> controllers = sessionManager.getActiveSessions(
                    new android.content.ComponentName(this, MusicListenerService.class)
            );
            
            if (controllers == null || controllers.isEmpty()) {
                return null;
            }
            
            // Get the first active controller
            MediaController controller = controllers.get(0);
            MediaMetadata metadata = controller.getMetadata();
            
            if (metadata == null) {
                return null;
            }
            
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            
            if (art == null) {
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            
            return new MusicMetadata(title, artist, album, art);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from MediaSession", e);
            return null;
        }
    }
    
    /**
     * Extracts metadata from notification extras (fallback).
     */
    private MusicMetadata extractFromNotification(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification.extras == null) {
                return null;
            }
            
            String title = notification.extras.getString(Notification.EXTRA_TITLE);
            String text = notification.extras.getString(Notification.EXTRA_TEXT);
            
            // Try to get large icon as album art
            Bitmap art = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.graphics.drawable.Icon icon = notification.getLargeIcon();
                if (icon != null) {
                    try {
                        art = icon.loadDrawable(this).getCurrent() instanceof android.graphics.drawable.BitmapDrawable
                            ? ((android.graphics.drawable.BitmapDrawable) icon.loadDrawable(this).getCurrent()).getBitmap()
                            : null;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract bitmap from icon", e);
                    }
                }
            }
            
            return new MusicMetadata(title, text, null, art);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from notification", e);
            return null;
        }
    }
    
    /**
     * Updates color palette and broadcasts to wallpaper service.
     * CRITICAL: Uses multiple broadcast mechanisms for reliability.
     */
    private void updateColorPalette(ColorPalette palette, MusicMetadata metadata) {
        // Throttle updates
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < UPDATE_THROTTLE_MS) {
            Log.d(TAG, "Update throttled, skipping");
            return;
        }
        lastUpdateTime = currentTime;
        
        Log.d(TAG, "=== UPDATING COLOR PALETTE ===");
        Log.d(TAG, "Palette: " + palette);
        
        // 1. Update singleton manager (INSTANT access for wallpaper)
        ColorPaletteManager.getInstance().updatePalette(this, palette);
        Log.d(TAG, "✓ Updated ColorPaletteManager singleton");
        
        // 2. Send LocalBroadcast (for wallpaper service receiver)
        Intent localIntent = new Intent(ACTION_COLOR_PALETTE_CHANGED);
        localIntent.putExtra(EXTRA_COLOR_PALETTE_JSON, palette.toJsonString());
        localIntent.putExtra(EXTRA_MUSIC_METADATA, metadata);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        Log.d(TAG, "✓ Sent LocalBroadcast");
        
        // 3. Send sticky broadcast as fallback
        Intent stickyIntent = new Intent(ACTION_COLOR_PALETTE_CHANGED);
        stickyIntent.putExtra(EXTRA_COLOR_PALETTE_JSON, palette.toJsonString());
        stickyIntent.putExtra(EXTRA_MUSIC_METADATA, metadata);
        sendStickyBroadcast(stickyIntent);
        Log.d(TAG, "✓ Sent sticky broadcast");
        
        Log.d(TAG, "=== PALETTE UPDATE COMPLETE ===");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MusicListenerService destroyed");
        ColorExtractor.clearCache();
    }
}
