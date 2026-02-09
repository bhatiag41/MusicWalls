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
        
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.holder = surfaceHolder;
            
            Log.d(TAG, "Engine onCreate called");
            
            handler = new Handler();
            
            // Register listener
            ColorPaletteManager.getInstance().addListener(this);
            registerColorPaletteReceiver();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "Surface created");
            
            // Get surface dimensions
            android.graphics.Rect frame = holder.getSurfaceFrame();
            int width = frame.width();
            int height = frame.height();
            
            Log.d(TAG, "Dimensions: " + width + "x" + height);
            
            // Initialize renderer with dimensions and colors
            java.util.List<Integer> colors = loadColorsFromPreferences();
            if (colors == null || colors.isEmpty()) {
                colors = getDefaultColors();
            }
            
            // Convert List to ArrayList for renderer compatibility if needed, 
            // though renderer should accept List interface. 
            // The user's prompt used ArrayList, so we'll ensure we pass something compatible.
            // Our WallpaperRenderer doesn't take colors in constructor anymore, but setters.
            // We'll adapt the user's logic to our existing architecture while keeping the robust flow.
            
            renderer = new WallpaperRenderer();
            renderer.setSurfaceSize(width, height);
            renderer.setColorPalette(new ColorPalette(colors));
            
            // Start drawing immediately
            drawFrame();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "Surface changed: " + width + "x" + height);
            if (renderer != null) {
                renderer.setSurfaceSize(width, height);
            }
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            
            if (visible) {
                Log.d(TAG, "Wallpaper visible - start drawing");
                drawFrame();
            } else {
                Log.d(TAG, "Wallpaper hidden - stop drawing");
                handler.removeCallbacks(drawRunner);
            }
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(drawRunner);
            Log.d(TAG, "Surface destroyed");
        }
        
        @Override
        public void onDestroy() {
             super.onDestroy();
             handler.removeCallbacks(drawRunner);
             ColorPaletteManager.getInstance().removeListener(this);
             unregisterColorPaletteReceiver();
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
