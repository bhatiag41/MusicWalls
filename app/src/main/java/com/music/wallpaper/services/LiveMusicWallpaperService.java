package com.music.wallpaper.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.music.wallpaper.managers.ColorPaletteManager;
import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.renderer.WallpaperRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Battery-optimized live wallpaper service.
 *
 * Key improvements:
 *  - HandlerThread: draw loop runs off the UI thread
 *  - Adaptive FPS: 30fps during color transitions, 2fps when static
 *  - Cycler: only runs before any music has ever been detected; permanently
 *    stops once a song palette arrives
 *  - Global + Local broadcast receivers for cross-process color updates
 *  - Settings changes propagated to renderer immediately
 */
public class LiveMusicWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallpaperService";

    // SharedPrefs key to remember if music was ever received (survives restarts)
    static final String PREF_MUSIC_EVER_RECEIVED = "music_ever_received";

    @Override
    public Engine onCreateEngine() {
        return new MusicWallpaperEngine();
    }

    private class MusicWallpaperEngine extends Engine
            implements ColorPaletteManager.ColorPaletteListener {

        private WallpaperRenderer renderer;
        private HandlerThread handlerThread;
        private Handler handler;
        private boolean visible = false;
        private SurfaceHolder holder;

        // Whether any music palette has ever been received (persisted)
        private boolean musicEverReceived = false;

        // Cycler — only active when musicEverReceived == false
        private static final long CYCLE_INTERVAL_MS = 10_000; // 10 seconds
        private final List<ColorPalette> defaultCyclePalettes = buildDefaultCyclePalettes();
        private int cycleIndex = 0;

        // Adaptive FPS
        private static final long FRAME_ACTIVE_MS  = 33;   // ~30fps during transitions
        private static final long FRAME_IDLE_MS    = 500;  // ~2fps when static

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        private final Runnable colorCycleRunner = new Runnable() {
            @Override
            public void run() {
                if (!visible || musicEverReceived) return; // stop if music ever played
                cycleIndex = (cycleIndex + 1) % defaultCyclePalettes.size();
                if (renderer != null) {
                    renderer.setColorPalette(defaultCyclePalettes.get(cycleIndex));
                }
                handler.postDelayed(this, CYCLE_INTERVAL_MS);
            }
        };

        private android.content.SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
        private BroadcastReceiver globalColorReceiver;
        private BroadcastReceiver localColorReceiver;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.holder = surfaceHolder;

            // Load persisted flag
            musicEverReceived = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                    .getBoolean(PREF_MUSIC_EVER_RECEIVED, false);

            // Use a dedicated background thread for drawing
            handlerThread = new HandlerThread("WallpaperDrawThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            ColorPaletteManager.getInstance().addListener(this);
            registerLocalColorReceiver();
            registerGlobalColorReceiver();
            registerPrefsListener();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            this.holder = holder;
            if (renderer == null) {
                renderer = new WallpaperRenderer();
            }
            WallpaperSettings settings =
                    WallpaperSettings.loadFromPreferences(LiveMusicWallpaperService.this);
            renderer.setSettings(settings);

            // Restore last saved music palette
            ColorPalette savedPalette = loadLastPaletteFromPrefs();
            if (savedPalette != null) {
                renderer.setColorPalette(savedPalette);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.holder = holder;
            if (renderer != null) renderer.setSurfaceSize(width, height);
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                handler.post(drawRunner);
                // Start cycler only if music has never been received
                if (!musicEverReceived) {
                    handler.postDelayed(colorCycleRunner, CYCLE_INTERVAL_MS);
                }
            } else {
                handler.removeCallbacks(drawRunner);
                handler.removeCallbacks(colorCycleRunner);
            }
        }

        private void registerPrefsListener() {
            prefsListener = (sharedPreferences, key) -> {
                if ("current_color_palette_json".equals(key) || "color_count".equals(key)) {
                    ColorPalette palette = loadLastPaletteFromPrefs();
                    if (palette != null && renderer != null) renderer.setColorPalette(palette);
                } else if (WallpaperSettings.KEY_ANIMATION_SPEED.equals(key)
                        || WallpaperSettings.KEY_COLOR_INTENSITY.equals(key)
                        || WallpaperSettings.KEY_BLUR_AMOUNT.equals(key)
                        || WallpaperSettings.KEY_TEXTURE_MODE.equals(key)) {
                    if (renderer != null) {
                        WallpaperSettings s =
                                WallpaperSettings.loadFromPreferences(LiveMusicWallpaperService.this);
                        renderer.setSettings(s);
                    }
                }
            };
            getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(prefsListener);
            androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(LiveMusicWallpaperService.this)
                    .registerOnSharedPreferenceChangeListener(prefsListener);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawRunner);
            handler.removeCallbacks(colorCycleRunner);
            handlerThread.quitSafely();
            ColorPaletteManager.getInstance().removeListener(this);
            unregisterLocalColorReceiver();
            unregisterGlobalColorReceiver();
            if (prefsListener != null) {
                getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                        .unregisterOnSharedPreferenceChangeListener(prefsListener);
                androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(LiveMusicWallpaperService.this)
                        .unregisterOnSharedPreferenceChangeListener(prefsListener);
            }
        }

        private void drawFrame() {
            if (!visible) return;

            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && renderer != null) {
                    renderer.draw(canvas);
                }
            } catch (Exception e) {
                Log.e(TAG, "Draw error: " + e.getMessage());
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); }
                    catch (Exception e) { Log.e(TAG, "Unlock error", e); }
                }
            }

            // Adaptive FPS: fast during transitions, slow when static
            handler.removeCallbacks(drawRunner);
            if (visible) {
                long delay = (renderer != null && renderer.isAnimating())
                        ? FRAME_ACTIVE_MS : FRAME_IDLE_MS;
                handler.postDelayed(drawRunner, delay);
            }
        }

        // ── Music palette received ────────────────────────────────────────────

        private void onMusicPaletteReceived(ColorPalette palette) {
            if (palette == null) return;
            // Mark music as ever received — persisted so cycler never starts again
            if (!musicEverReceived) {
                musicEverReceived = true;
                getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                        .edit().putBoolean(PREF_MUSIC_EVER_RECEIVED, true).apply();
            }
            // Stop cycler permanently
            handler.removeCallbacks(colorCycleRunner);

            if (renderer != null) renderer.setColorPalette(palette);
        }

        @Override
        public void onColorPaletteChanged(ColorPalette newPalette) {
            onMusicPaletteReceived(newPalette);
        }

        // ── Broadcast receivers ───────────────────────────────────────────────

        private void registerLocalColorReceiver() {
            localColorReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (MusicListenerService.ACTION_COLOR_PALETTE_CHANGED.equals(intent.getAction())) {
                        applyPaletteFromIntent(intent);
                    }
                }
            };
            LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                    .registerReceiver(localColorReceiver,
                            new IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED));
        }

        private void unregisterLocalColorReceiver() {
            if (localColorReceiver != null) {
                LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                        .unregisterReceiver(localColorReceiver);
            }
        }

        private void registerGlobalColorReceiver() {
            globalColorReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (MusicListenerService.ACTION_COLOR_PALETTE_CHANGED.equals(intent.getAction())) {
                        applyPaletteFromIntent(intent);
                    }
                }
            };
            registerReceiver(globalColorReceiver,
                    new IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED));
        }

        private void unregisterGlobalColorReceiver() {
            if (globalColorReceiver != null) {
                try { unregisterReceiver(globalColorReceiver); }
                catch (Exception e) { Log.w(TAG, "Unregister global receiver", e); }
            }
        }

        private void applyPaletteFromIntent(Intent intent) {
            String json = intent.getStringExtra(MusicListenerService.EXTRA_COLOR_PALETTE_JSON);
            if (json != null) {
                ColorPalette palette = ColorPalette.fromJsonString(json);
                onMusicPaletteReceived(palette);
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private ColorPalette loadLastPaletteFromPrefs() {
            try {
                android.content.SharedPreferences prefs =
                        getSharedPreferences("WallpaperPrefs", MODE_PRIVATE);
                int count = prefs.getInt("color_count", 0);
                if (count > 0) {
                    List<Integer> colors = new ArrayList<>();
                    for (int i = 0; i < count; i++) colors.add(prefs.getInt("color_" + i, 0));
                    return new ColorPalette(colors);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading palette", e);
            }
            return null;
        }

        private List<ColorPalette> buildDefaultCyclePalettes() {
            List<ColorPalette> p = new ArrayList<>();
            p.add(new ColorPalette(
                    android.graphics.Color.parseColor("#B23A6F"),
                    android.graphics.Color.parseColor("#7A3FA0"),
                    android.graphics.Color.parseColor("#2F4FA8"),
                    android.graphics.Color.parseColor("#1E7F86"),
                    android.graphics.Color.parseColor("#9C7A1E")));
            p.add(new ColorPalette(
                    android.graphics.Color.parseColor("#0D47A1"),
                    android.graphics.Color.parseColor("#1565C0"),
                    android.graphics.Color.parseColor("#1976D2"),
                    android.graphics.Color.parseColor("#00838F"),
                    android.graphics.Color.parseColor("#006064")));
            p.add(new ColorPalette(
                    android.graphics.Color.parseColor("#BF360C"),
                    android.graphics.Color.parseColor("#E64A19"),
                    android.graphics.Color.parseColor("#F57C00"),
                    android.graphics.Color.parseColor("#FF8F00"),
                    android.graphics.Color.parseColor("#6A1B9A")));
            p.add(new ColorPalette(
                    android.graphics.Color.parseColor("#1B5E20"),
                    android.graphics.Color.parseColor("#2E7D32"),
                    android.graphics.Color.parseColor("#388E3C"),
                    android.graphics.Color.parseColor("#00695C"),
                    android.graphics.Color.parseColor("#004D40")));
            return p;
        }
    }
}
