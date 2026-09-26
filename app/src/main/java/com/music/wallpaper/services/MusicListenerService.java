package com.music.wallpaper.services;

import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.music.wallpaper.managers.ColorPaletteManager;
import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.MusicMetadata;
import com.music.wallpaper.models.WallpaperPreferences;
import com.music.wallpaper.palette.PaletteExtractor;

import java.util.List;

/**
 * Notification listener service that detects music playback and extracts color palettes.
 *
 * Two-track detection strategy:
 *   1. onNotificationPosted  — fires when the music app posts/updates its notification.
 *   2. MediaController.Callback.onMetadataChanged — fires on every track change even when
 *      the app reuses the same notification slot (auto-advance, crossfade, etc.).
 *
 * Dedup uses three signals (title + artist + album-art pixel hash) so a new song with the
 * same title still triggers a color update.  If art arrives null we retry once after 1 s.
 */
public class MusicListenerService extends NotificationListenerService {

    private static final String TAG = "MusicListenerService";

    public static final String ACTION_COLOR_PALETTE_CHANGED = "com.music.wallpaper.COLOR_PALETTE_CHANGED";
    public static final String EXTRA_COLOR_PALETTE_JSON     = "color_palette_json";
    public static final String EXTRA_MUSIC_METADATA         = "music_metadata";
    public static final String EXTRA_TRACK_TITLE            = "track_title";
    public static final String EXTRA_ARTIST_NAME            = "artist_name";

    private WallpaperPreferences settings;
    private long lastUpdateTime = 0;
    private static final long UPDATE_THROTTLE_MS = 500;

    // 3-signal dedup: title + artist + art pixel-hash
    private String lastTrackTitle = null;
    private String lastArtistName = null;
    private int    lastArtHash    = 0;

    // Art-null retry: schedule once if art not yet delivered
    private boolean artRetryPending = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Active MediaController + its callback (kept to unregister cleanly)
    private MediaController          activeController = null;
    private MediaController.Callback mcCallback       = null;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        settings = WallpaperPreferences.Companion.load(this);
        Log.d(TAG, "MusicListenerService created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        attachMediaControllerCallback();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        detachMediaControllerCallback();
        Log.d(TAG, "MusicListenerService destroyed");
    }

    // -----------------------------------------------------------------------
    // MediaController callback
    // Fires on every metadata change including auto-advance to the next song,
    // even when no new/updated notification is posted.
    // -----------------------------------------------------------------------

    private void attachMediaControllerCallback() {
        try {
            MediaSessionManager mgr =
                    (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (mgr == null) return;

            List<MediaController> controllers = mgr.getActiveSessions(
                    new android.content.ComponentName(this, MusicListenerService.class));
            if (controllers == null || controllers.isEmpty()) return;

            MediaController controller = controllers.get(0);
            if (controller.equals(activeController)) return; // already watching this session

            detachMediaControllerCallback();
            activeController = controller;

            mcCallback = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    if (metadata == null) return;
                    settings = WallpaperPreferences.Companion.load(MusicListenerService.this);
                    processMediaMetadata(metadata);
                }
            };

            controller.registerCallback(mcCallback, mainHandler);
            Log.d(TAG, "MediaController callback attached: " + controller.getPackageName());

            // Process whatever is currently playing so we don't miss a mid-song attach
            MediaMetadata current = controller.getMetadata();
            if (current != null) processMediaMetadata(current);

        } catch (Exception e) {
            Log.e(TAG, "Error attaching MediaController callback", e);
        }
    }

    private void detachMediaControllerCallback() {
        if (activeController != null && mcCallback != null) {
            try { activeController.unregisterCallback(mcCallback); } catch (Exception ignored) {}
        }
        activeController = null;
        mcCallback       = null;
    }

    // -----------------------------------------------------------------------
    // Notification events (fallback + session re-attach trigger)
    // -----------------------------------------------------------------------

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            settings = WallpaperPreferences.Companion.load(this);
            if (!isMusicNotification(sbn)) return;

            Log.d(TAG, "Music notification posted: " + sbn.getPackageName());

            // Re-attach callback: the session token may have rotated
            attachMediaControllerCallback();

            MusicMetadata metadata = extractMetadata(sbn);
            if (metadata == null) {
                Log.w(TAG, "Failed to extract metadata from notification");
                return;
            }

            processTrackChange(
                    metadata.getTrackTitle(),
                    metadata.getArtistName(),
                    metadata.getAlbumArtBitmap());

        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isMusicNotification(sbn)) {
            Log.d(TAG, "Music notification removed: " + sbn.getPackageName());
            // Intentionally retain the last palette — do not revert to default
        }
    }

    // -----------------------------------------------------------------------
    // Core: handle a metadata update from MediaController
    // -----------------------------------------------------------------------

    private void processMediaMetadata(MediaMetadata metadata) {
        try {
            String title  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            Bitmap art    = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);

            if (title == null && artist == null) return;

            processTrackChange(title, artist, art);

        } catch (Exception e) {
            Log.e(TAG, "Error processing MediaController metadata", e);
        }
    }

    // -----------------------------------------------------------------------
    // 3-signal dedup + art-null retry
    // -----------------------------------------------------------------------

    private void processTrackChange(String title, String artist, Bitmap art) {
        int artHash = (art != null) ? quickArtHash(art) : 0;

        boolean sameTitle  = safeEquals(title,  lastTrackTitle);
        boolean sameArtist = safeEquals(artist, lastArtistName);
        boolean sameArt    = (artHash == lastArtHash); // Allows null (0) to match null (0)

        if (sameTitle && sameArtist && sameArt) {
            // Already processed this state, skip to prevent lag
            return;
        }

        // Only save to SharedPreferences and broadcast text if the text actually changed
        if (!sameTitle || !sameArtist) {
            saveTrackInfoToPrefs(title, artist);
            Intent trackIntent = new Intent(ACTION_COLOR_PALETTE_CHANGED);
            trackIntent.putExtra(EXTRA_TRACK_TITLE, title);
            trackIntent.putExtra(EXTRA_ARTIST_NAME, artist);
            LocalBroadcastManager.getInstance(this).sendBroadcast(trackIntent);
        }

        lastTrackTitle = title;
        lastArtistName = artist;
        lastArtHash    = artHash;

        if (art == null) {
            // Art hasn't loaded yet — retry once after 1 second
            if (!artRetryPending) {
                artRetryPending = true;
                mainHandler.postDelayed(() -> {
                    artRetryPending = false;
                    Bitmap retryArt = fetchArtFromActiveSession();
                    if (retryArt != null) {
                        Log.d(TAG, "Art arrived on retry, extracting colors");
                        lastArtHash = quickArtHash(retryArt);
                        extractAndBroadcast(retryArt);
                    } else {
                        Log.d(TAG, "Art still null on retry for: " + title);
                    }
                }, 1000);
            }
            return;
        }

        artRetryPending = false;
        lastArtHash = artHash;
        extractAndBroadcast(art);
    }

    private static final java.util.concurrent.ExecutorService backgroundExecutor = 
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void extractAndBroadcast(Bitmap art) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_THROTTLE_MS) return;
        lastUpdateTime = now;

        backgroundExecutor.execute(() -> {
            ColorPalette palette = PaletteExtractor.extractPalette(art);
            Log.d(TAG, "Extracted palette: " + palette);
            
            // Post broadcast back to main thread or let it broadcast from bg thread (both are fine)
            mainHandler.post(() -> broadcastPalette(palette, art));
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** 5-pixel XOR hash — fast album-art change fingerprint. */
    private int quickArtHash(Bitmap bmp) {
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            return bmp.getPixel(0, 0)
                 ^ bmp.getPixel(w - 1, 0)
                 ^ bmp.getPixel(0, h - 1)
                 ^ bmp.getPixel(w - 1, h - 1)
                 ^ bmp.getPixel(w / 2, h / 2);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private Bitmap fetchArtFromActiveSession() {
        try {
            if (activeController != null) {
                MediaMetadata md = activeController.getMetadata();
                if (md != null) {
                    Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    return art;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isMusicNotification(StatusBarNotification sbn) {
        return settings.isMusicAppEnabled(sbn.getPackageName().toLowerCase());
    }

    private MusicMetadata extractMetadata(StatusBarNotification sbn) {
        MusicMetadata md = extractFromMediaSession();
        return (md != null) ? md : extractFromNotification(sbn);
    }

    private MusicMetadata extractFromMediaSession() {
        try {
            MediaSessionManager mgr =
                    (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (mgr == null) return null;

            List<MediaController> controllers = mgr.getActiveSessions(
                    new android.content.ComponentName(this, MusicListenerService.class));
            if (controllers == null || controllers.isEmpty()) return null;

            MediaController controller = controllers.get(0);
            MediaMetadata metadata     = controller.getMetadata();
            if (metadata == null) return null;

            String title  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            String album  = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap art    = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);

            return new MusicMetadata(title, artist, album, art);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from MediaSession", e);
            return null;
        }
    }

    private MusicMetadata extractFromNotification(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification.extras == null) return null;

            String title = notification.extras.getString(Notification.EXTRA_TITLE);
            String text  = notification.extras.getString(Notification.EXTRA_TEXT);
            Bitmap art   = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.graphics.drawable.Icon icon = notification.getLargeIcon();
                if (icon != null) {
                    try {
                        android.graphics.drawable.Drawable d = icon.loadDrawable(this);
                        if (d instanceof android.graphics.drawable.BitmapDrawable) {
                            art = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                        }
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

    private void broadcastPalette(ColorPalette palette, Bitmap art) {
        Log.d(TAG, "=== UPDATING COLOR PALETTE ===");

        saveColorsToPrefs(palette);
        ColorPaletteManager.getInstance().updatePalette(this, palette);
        if (art != null) ColorPaletteManager.getInstance().updateArtwork(this, art);

        // LocalBroadcast → SettingsViewModel / MainActivity (same process)
        Intent local = new Intent(ACTION_COLOR_PALETTE_CHANGED);
        local.putExtra(EXTRA_COLOR_PALETTE_JSON, palette.toJsonString());
        local.putIntegerArrayListExtra("colors", (java.util.ArrayList<Integer>) palette.getAllColors());
        LocalBroadcastManager.getInstance(this).sendBroadcast(local);

        // Global broadcast → WallpaperService (may run in separate process context)
        Intent global = new Intent(ACTION_COLOR_PALETTE_CHANGED);
        global.putExtra(EXTRA_COLOR_PALETTE_JSON, palette.toJsonString());
        global.putIntegerArrayListExtra("colors", (java.util.ArrayList<Integer>) palette.getAllColors());
        sendStickyBroadcast(global);

        Log.d(TAG, "=== PALETTE UPDATE COMPLETE (" + palette.getAllColors().size() + " colors) ===");
    }

    private void saveColorsToPrefs(ColorPalette palette) {
        android.content.SharedPreferences.Editor editor =
                getSharedPreferences("WallpaperPrefs", MODE_PRIVATE).edit();
        List<Integer> colors = palette.getAllColors();
        editor.putInt("color_count", colors.size());
        for (int i = 0; i < colors.size(); i++) editor.putInt("color_" + i, colors.get(i));
        editor.apply();
    }

    private void saveTrackInfoToPrefs(String title, String artist) {
        getSharedPreferences("WallpaperPrefs", MODE_PRIVATE).edit()
                .putString("last_track_title", title)
                .putString("last_artist_name", artist)
                .apply();
    }
}
