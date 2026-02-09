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
        
        private WallpaperRenderer renderer;
        private Handler handler;
        private boolean visible = false;
        private SurfaceHolder holder;
        
        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };
        
        // SharedPreference Listener for robust updates
        private android.content.SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.holder = surfaceHolder;
            
            Log.d(TAG, "Engine onCreate called");
            
            handler = new Handler();
            
            // Register listener
            // Register listener
            ColorPaletteManager.getInstance().addListener(this);
            registerColorPaletteReceiver();
            registerPrefsListener();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "onSurfaceCreated");
            this.holder = holder;
            if (renderer == null) {
                renderer = new WallpaperRenderer();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
            this.holder = holder;
            if (renderer != null) {
                renderer.setSurfaceSize(width, height);
            }
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                // Initial draw
                drawFrame();
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        private void registerPrefsListener() {
            android.content.SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE);
            prefsListener = (sharedPreferences, key) -> {
                if ("current_color_palette_json".equals(key) || "color_count".equals(key)) {
                    Log.d(TAG, "Prefs changed: " + key + ", reloading colors");
                    java.util.List<Integer> colors = loadColorsFromPreferences();
                    if (colors != null && !colors.isEmpty() && renderer != null) {
                        renderer.setColorPalette(new ColorPalette(colors));
                    }
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        }

        @Override
        public void onDestroy() {
             super.onDestroy();
             handler.removeCallbacks(drawRunner);
             ColorPaletteManager.getInstance().removeListener(this);
             unregisterColorPaletteReceiver();
             if (prefsListener != null) {
                 getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                     .unregisterOnSharedPreferenceChangeListener(prefsListener);
             }
             Log.d(TAG, "Engine destroyed");
        }

        private void drawFrame() {
            if (!visible) return;
            
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    // CRITICAL: Draw something to verify it works
                    // First test with simple color fill like user requested
                    // but we will trust renderer if initialized
                    
                    if (renderer != null) {
                        renderer.draw(canvas);
                    } else {
                        Log.e(TAG, "Renderer is null!");
                        canvas.drawColor(android.graphics.Color.BLUE); // Error color
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error drawing: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }
            
            // Schedule next frame (30 FPS for battery efficiency as requested)
            handler.removeCallbacks(drawRunner);
            if (visible) {
                handler.postDelayed(drawRunner, 33);
            }
        }
        
        private java.util.List<Integer> getDefaultColors() {
             java.util.List<Integer> colors = new java.util.ArrayList<>();
            colors.add(android.graphics.Color.parseColor("#B23A6F")); // dark pink
            colors.add(android.graphics.Color.parseColor("#7A3FA0")); // deep purple
            colors.add(android.graphics.Color.parseColor("#2F4FA8")); // muted blue
            colors.add(android.graphics.Color.parseColor("#1E7F86")); // dark teal
            colors.add(android.graphics.Color.parseColor("#9C7A1E")); // warm dark yellow

             return colors;
        }
        
        private java.util.List<Integer> loadColorsFromPreferences() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE);
                int count = prefs.getInt("color_count", 0);
                if (count > 0) {
                    java.util.List<Integer> colors = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        colors.add(prefs.getInt("color_" + i, 0));
                    }
                    return colors;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading colors", e);
            }
            return null;
        }

        @Override
        public void onColorPaletteChanged(ColorPalette newPalette) {
            Log.d(TAG, "=== PALETTE CHANGED ===");
            if (renderer != null) {
                renderer.setColorPalette(newPalette);
            }
        }
        
        // Broadcast Receiver Implementation
        private BroadcastReceiver colorPaletteReceiver;
        
        private void registerColorPaletteReceiver() {
            colorPaletteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                     if ("com.music.wallpaper.COLOR_PALETTE_CHANGED".equals(intent.getAction())) {
                        String paletteJson = intent.getStringExtra("color_palette_json");
                        if (paletteJson != null) {
                            ColorPalette palette = ColorPalette.fromJsonString(paletteJson);
                            if (renderer != null) renderer.setColorPalette(palette);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter("com.music.wallpaper.COLOR_PALETTE_CHANGED");
            LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this).registerReceiver(colorPaletteReceiver, filter);
        }
        
        private void unregisterColorPaletteReceiver() {
             if (colorPaletteReceiver != null) {
                LocalBroadcastManager.getInstance(LiveMusicWallpaperService.this).unregisterReceiver(colorPaletteReceiver);
            }
        }
    }
}
