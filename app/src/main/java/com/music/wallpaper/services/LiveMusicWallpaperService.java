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

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.renderer.WallpaperRenderer;

/**
 * Live Wallpaper Service that renders animated wallpaper based on music colors.
 * Receives color palette updates from MusicListenerService and delegates rendering to WallpaperRenderer.
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
    private class MusicWallpaperEngine extends Engine {
        
        private static final int TARGET_FPS = 60;
        private static final long FRAME_TIME_MS = 1000 / TARGET_FPS;
        
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
            
            Log.d(TAG, "MusicWallpaperEngine created");
        }
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            
            // Register broadcast receiver for color palette updates
            registerColorPaletteReceiver();
            
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
                // Start rendering
                scheduleNextFrame();
                Log.d(TAG, "Wallpaper visible, starting rendering");
            } else {
                // Stop rendering to save battery
                handler.removeCallbacks(drawRunnable);
                Log.d(TAG, "Wallpaper hidden, stopping rendering");
            }
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            
            visible = false;
            handler.removeCallbacks(drawRunnable);
            
            Log.d(TAG, "Surface destroyed");
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            
            // Unregister broadcast receiver
            unregisterColorPaletteReceiver();
            
            // Stop rendering
            visible = false;
            handler.removeCallbacks(drawRunnable);
            
            // Quit rendering thread
            if (renderThread != null) {
                renderThread.quitSafely();
            }
            
            Log.d(TAG, "Engine destroyed");
        }
        
        /**
         * Registers broadcast receiver for color palette updates.
         */
        private void registerColorPaletteReceiver() {
            colorPaletteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (MusicListenerService.ACTION_COLOR_PALETTE_CHANGED.equals(intent.getAction())) {
                        String paletteJson = intent.getStringExtra(
                            MusicListenerService.EXTRA_COLOR_PALETTE_JSON
                        );
                        
                        if (paletteJson != null) {
                            ColorPalette palette = ColorPalette.fromJsonString(paletteJson);
                            renderer.setColorPalette(palette);
                            Log.d(TAG, "Received new color palette: " + palette);
                        }
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED);
            LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                .registerReceiver(colorPaletteReceiver, filter);
            
            Log.d(TAG, "Registered color palette receiver");
        }
        
        /**
         * Unregisters broadcast receiver.
         */
        private void unregisterColorPaletteReceiver() {
            if (colorPaletteReceiver != null) {
                try {
                    LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this)
                        .unregisterReceiver(colorPaletteReceiver);
                    Log.d(TAG, "Unregistered color palette receiver");
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering receiver", e);
                }
                colorPaletteReceiver = null;
            }
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
