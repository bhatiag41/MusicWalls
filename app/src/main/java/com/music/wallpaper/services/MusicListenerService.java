package com.music.wallpaper.services;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.MusicMetadata;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.utils.ColorExtractor;

import java.util.List;

/**
 * NotificationListenerService that detects music notifications and extracts metadata.
 * Broadcasts color palette changes to the wallpaper service.
 */
public class MusicListenerService extends NotificationListenerService {
    
    private static final String TAG = "MusicListenerService";
    
    public static final String ACTION_COLOR_PALETTE_CHANGED = 
        "com.music.wallpaper.ACTION_COLOR_PALETTE_CHANGED";
    public static final String EXTRA_COLOR_PALETTE_JSON = "color_palette_json";
    public static final String EXTRA_MUSIC_METADATA = "music_metadata";
    
    private static final long THROTTLE_INTERVAL_MS = 2000; // Max 1 update per 2 seconds
    private long lastUpdateTime = 0;
    private ColorPalette lastPalette = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MusicListenerService created");
    }
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        
        try {
            // Check if this is a music notification
            if (!isMusicNotification(sbn)) {
                return;
            }
            
            // Throttle updates to avoid excessive processing
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < THROTTLE_INTERVAL_MS) {
                Log.d(TAG, "Throttling update (too soon)");
                return;
            }
            
            // Extract metadata from notification
            MusicMetadata metadata = extractMetadataFromNotification(sbn);
            if (metadata == null) {
                Log.d(TAG, "Could not extract metadata from notification");
                return;
            }
            
            Log.d(TAG, "Extracted music metadata: " + metadata);
            
            // Extract colors from album art
            Bitmap albumArt = metadata.getAlbumArtBitmap();
            if (albumArt != null) {
                ColorPalette palette = ColorExtractor.extractPalette(albumArt);
                
                // Only broadcast if palette changed
                if (!palette.equals(lastPalette)) {
                    broadcastColorPaletteChange(palette, metadata);
                    lastPalette = palette;
                    lastUpdateTime = currentTime;
                    Log.d(TAG, "Broadcasted color palette change: " + palette);
                }
            } else {
                // No album art, use default palette
                ColorPalette defaultPalette = ColorPalette.getDefaultPalette();
                if (!defaultPalette.equals(lastPalette)) {
                    broadcastColorPaletteChange(defaultPalette, metadata);
                    lastPalette = defaultPalette;
                    lastUpdateTime = currentTime;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        
        try {
            // Check if a music notification was removed
            if (isMusicNotification(sbn)) {
                Log.d(TAG, "Music notification removed, checking for active music");
                
                // Check if there are any other active music sessions
                if (!hasActiveMusicSession()) {
                    // No active music, revert to default palette
                    ColorPalette defaultPalette = ColorPalette.getDefaultPalette();
                    broadcastColorPaletteChange(defaultPalette, null);
                    lastPalette = defaultPalette;
                    Log.d(TAG, "No active music, reverted to default palette");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification removal", e);
        }
    }
    
    /**
     * Checks if the notification is from a music app.
     */
    private boolean isMusicNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        
        // Load user settings to check enabled apps
        WallpaperSettings settings = WallpaperSettings.loadFromPreferences(this);
        
        // Check if this app is enabled for music detection
        return settings.isMusicAppEnabled(packageName);
    }
    
    /**
     * Extracts music metadata from notification.
     */
    private MusicMetadata extractMetadataFromNotification(StatusBarNotification sbn) {
        try {
            android.app.Notification notification = sbn.getNotification();
            if (notification == null) return null;
            
            android.os.Bundle extras = notification.extras;
            if (extras == null) return null;
            
            // Extract basic metadata from notification extras
            String title = extras.getString(android.app.Notification.EXTRA_TITLE);
            String text = extras.getString(android.app.Notification.EXTRA_TEXT);
            String subText = extras.getString(android.app.Notification.EXTRA_SUB_TEXT);
            
            // Try to extract album art from notification
            Bitmap albumArt = null;
            
            // Try to get large icon first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Icon largeIcon = notification.getLargeIcon();
                if (largeIcon != null) {
                    try {
                        albumArt = largeIcon.loadDrawable(this).getCurrent()
                            instanceof android.graphics.drawable.BitmapDrawable
                                ? ((android.graphics.drawable.BitmapDrawable) 
                                    largeIcon.loadDrawable(this).getCurrent()).getBitmap()
                                : null;
                    } catch (Exception e) {
                        Log.w(TAG, "Could not load large icon as bitmap", e);
                    }
                }
            }
            
            // Try MediaSession as a more reliable source
            MusicMetadata mediaSessionMetadata = extractFromMediaSession();
            if (mediaSessionMetadata != null) {
                // Prefer MediaSession data if available
                if (mediaSessionMetadata.hasAlbumArt()) {
                    albumArt = mediaSessionMetadata.getAlbumArtBitmap();
                }
                return new MusicMetadata(
                    mediaSessionMetadata.getTrackTitle(),
                    mediaSessionMetadata.getArtistName(),
                    mediaSessionMetadata.getAlbumName(),
                    albumArt
                );
            }
            
            // Fallback to notification extras
            return new MusicMetadata(
                title,
                text, // Often contains artist name
                subText, // Often contains album name
                albumArt
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata", e);
            return null;
        }
    }
    
    /**
     * Extracts metadata from active MediaSession (more reliable than notification).
     */
    private MusicMetadata extractFromMediaSession() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return null;
            }
            
            MediaSessionManager sessionManager = 
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            
            if (sessionManager == null) return null;
            
            List<MediaController> controllers = 
                sessionManager.getActiveSessions(
                    new android.content.ComponentName(this, MusicListenerService.class)
                );
            
            if (controllers == null || controllers.isEmpty()) {
                return null;
            }
            
            // Use the first active controller
            MediaController controller = controllers.get(0);
            MediaMetadata metadata = controller.getMetadata();
            
            if (metadata == null) return null;
            
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            
            // Fallback to artwork if album art not available
            if (albumArt == null) {
                albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            
            return new MusicMetadata(title, artist, album, albumArt);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from MediaSession", e);
            return null;
        }
    }
    
    /**
     * Checks if there's any active music session.
     */
    private boolean hasActiveMusicSession() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return false;
            }
            
            MediaSessionManager sessionManager = 
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            
            if (sessionManager == null) return false;
            
            List<MediaController> controllers = 
                sessionManager.getActiveSessions(
                    new android.content.ComponentName(this, MusicListenerService.class)
                );
            
            return controllers != null && !controllers.isEmpty();
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking active sessions", e);
            return false;
        }
    }
    
    /**
     * Broadcasts color palette change to wallpaper service.
     */
    private void broadcastColorPaletteChange(ColorPalette palette, MusicMetadata metadata) {
        Intent intent = new Intent(ACTION_COLOR_PALETTE_CHANGED);
        intent.putExtra(EXTRA_COLOR_PALETTE_JSON, palette.toJsonString());
        if (metadata != null) {
            intent.putExtra(EXTRA_MUSIC_METADATA, metadata);
        }
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MusicListenerService destroyed");
        ColorExtractor.clearCache();
    }
}
