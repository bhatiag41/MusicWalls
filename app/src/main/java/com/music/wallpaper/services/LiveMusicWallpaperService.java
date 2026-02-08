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

/**
 * Live Wallpaper Service that renders animated wallpaper based on music colors.
 * Enhanced with multiple update mechanisms for instant color changes.
 */
public class LiveMusicWallpaperService extends WallpaperService {
    
    private static final String TAG = "LiveWallpaperService";
    
    @Override
    public Engine onCreateEngine() {
        return new MusicWallpaperEngine();
    }
    
    /**
     * Custom Engine implementation for music-synced wallpaper.
     */
    private class MusicWallpaperEngine extends Engine implements ColorPaletteManager.ColorPaletteListener {
        
        private static final int TARGET_FPS = 60;
        private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;
        private static final long POLLING_INTERVAL_MS = 2000; // Poll every 2 seconds as fallback
        
        private final WallpaperRenderer renderer;
        private final Handler handler;
        private final HandlerThread renderThread;
        
        private boolean visible = false;
        private BroadcastReceiver colorPaletteReceiver;
        
        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };
        
        private final Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkForPaletteUpdates();
                if (visible) {
                    handler.postDelayed(this, POLLING_INTERVAL_MS);
                }
            }
        };
        
        public MusicWallpaperEngine() {
            super();
            
            // Create rendering thread
            renderThread = new HandlerThread("WallpaperRenderThread");
            renderThread.start();
            handler = new Handler(renderThread.getLooper());
            
            // Create renderer
            renderer = new WallpaperRenderer();
            
            // Load settings
            WallpaperSettings settings = WallpaperSettings.loadFromPreferences(
                LiveMusicWallpaperService.this
            );
            renderer.setSettings(settings);
            
            // Load current palette from manager
            ColorPalette currentPalette = ColorPaletteManager.getInstance()
                .getCurrentPalette(LiveMusicWallpaperService.this);
            renderer.setColorPalette(currentPalette);
            
            Log.d(TAG, "MusicWallpaperEngine created with palette: " + currentPalette);
        }
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            
            // Register as listener to ColorPaletteManager (INSTANT updates)
            ColorPaletteManager.getInstance().addListener(this);
            Log.d(TAG, "✓ Registered ColorPaletteManager listener");
            
            // Register broadcast receiver for color palette updates
            registerColorPaletteReceiver();
            
            // Start polling as fallback mechanism
            handler.post(pollingRunnable);
            
            setTouchEventsEnabled(false); // Disable touch events for battery savings
            
            Log.d(TAG, "Engine onCreate");
        }
        
        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "Surface created");
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            
            renderer.setSurfaceSize(width, height);
            
            Log.d(TAG, "Surface changed: " + width + "x" + height);
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            
            if (visible) {
                // Reload settings in case they changed
                WallpaperSettings settings = WallpaperSettings.loadFromPreferences(
                    LiveMusicWallpaperService.this
                );
                renderer.setSettings(settings);
                
                // Check for palette updates
                checkForPaletteUpdates();
                
                // Start rendering
                scheduleNextFrame();
                
                // Start polling
                handler.post(pollingRunnable);
                
                Log.d(TAG, "Wallpaper visible, starting rendering");
            } else {
                // Stop rendering to save battery
                handler.removeCallbacks(drawRunnable);
                handler.removeCallbacks(pollingRunnable);
                Log.d(TAG, "Wallpaper hidden, stopping rendering");
            }
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            
            visible = false;
            handler.removeCallbacks(drawRunnable);
            handler.removeCallbacks(pollingRunnable);
            
            Log.d(TAG, "Surface destroyed");
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            
            // Unregister from ColorPaletteManager
            ColorPaletteManager.getInstance().removeListener(this);
            Log.d(TAG, "✓ Unregistered ColorPaletteManager listener");
            
            // Unregister broadcast receiver
            unregisterColorPaletteReceiver();
            
            // Stop rendering
            visible = false;
            handler.removeCallbacks(drawRunnable);
            handler.removeCallbacks(pollingRunnable);
            
            // Quit rendering thread
            if (renderThread != null) {
                renderThread.quitSafely();
            }
            
            Log.d(TAG, "Engine destroyed");
        }
        
        /**
         * ColorPaletteManager listener callback - INSTANT updates!
         */
        @Override
        public void onColorPaletteChanged(ColorPalette newPalette) {
            Log.d(TAG, "=== PALETTE CHANGED (ColorPaletteManager) ===");
            Log.d(TAG, "New palette: " + newPalette);
            renderer.setColorPalette(newPalette);
            forceRedrawImmediate();
        }
        
        /**
         * Registers broadcast receiver for color palette updates.
         */
        private void registerColorPaletteReceiver() {
            colorPaletteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (MusicListenerService.ACTION_COLOR_PALETTE_CHANGED.equals(intent.getAction())) {
                        Log.d(TAG, "=== PALETTE CHANGED (Broadcast) ===");
                        
                        String paletteJson = intent.getStringExtra(
                            MusicListenerService.EXTRA_COLOR_PALETTE_JSON
                        );
                        
                        if (paletteJson != null) {
                            ColorPalette palette = ColorPalette.fromJsonString(paletteJson);
                            Log.d(TAG, "Received palette: " + palette);
                            renderer.setColorPalette(palette);
                            forceRedrawImmediate();
                        }
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED);
            LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                .registerReceiver(colorPaletteReceiver, filter);
            
            Log.d(TAG, "✓ Registered broadcast receiver");
        }
        
        /**
         * Unregisters broadcast receiver.
         */
        private void unregisterColorPaletteReceiver() {
            if (colorPaletteReceiver != null) {
                try {
                    LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                        .unregisterReceiver(colorPaletteReceiver);
                    Log.d(TAG, "✓ Unregistered broadcast receiver");
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering receiver", e);
                }
                colorPaletteReceiver = null;
            }
        }
        
        /**
         * Checks for palette updates from ColorPaletteManager (polling fallback).
         */
        private void checkForPaletteUpdates() {
            try {
                ColorPalette currentPalette = ColorPaletteManager.getInstance()
                    .getCurrentPalette(LiveMusicWallpaperService.this);
                renderer.setColorPalette(currentPalette);
                // Don't log here to avoid spam
            } catch (Exception e) {
                Log.e(TAG, "Error checking palette updates", e);
            }
        }
        
        /**
         * Forces an immediate redraw (called when palette changes).
         */
        private void forceRedrawImmediate() {
            handler.removeCallbacks(drawRunnable);
            handler.post(drawRunnable);
            Log.d(TAG, "✓ Forced immediate redraw");
        }
        
        /**
         * Draws a single frame.
         */
        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            if (holder == null) {
                return;
            }
            
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    // Draw wallpaper
                    renderer.draw(canvas);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error drawing frame", e);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }
            
            // Schedule next frame if still visible
            if (visible) {
                scheduleNextFrame();
            }
        }
        
        /**
         * Schedules the next frame to maintain target FPS.
         */
        private void scheduleNextFrame() {
            handler.removeCallbacks(drawRunnable);
            handler.postDelayed(drawRunnable, FRAME_TIME_MS);
        }
    }
}
