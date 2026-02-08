package com.music.wallpaper.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.music.wallpaper.R;
import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.renderer.WallpaperRenderer;
import com.music.wallpaper.utils.PermissionManager;

/**
 * Preview activity to see wallpaper before setting it.
 */
public class PreviewActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    
    private WallpaperRenderer renderer;
    private SurfaceView surfaceView;
    private Thread renderThread;
    private boolean isRendering = false;
    
    // Sample color palettes for testing
    private final ColorPalette[] samplePalettes = {
        ColorPalette.getDefaultPalette(),
        new ColorPalette(0xFF1E88E5, 0xFF42A5F5, 0xFF90CAF9, 0xFF0D47A1, 0xFFBBDEFB),
        new ColorPalette(0xFFE53935, 0xFFEF5350, 0xFFEF9A9A, 0xFFB71C1C, 0xFFFFCDD2),
        new ColorPalette(0xFF43A047, 0xFF66BB6A, 0xFFA5D6A7, 0xFF1B5E20, 0xFFC8E6C9),
        new ColorPalette(0xFFEC407A, 0xFFF06292, 0xFFF8BBD0, 0xFFC2185B, 0xFFF48FB1)
    };
    
    private int currentPaletteIndex = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);
        
        renderer = new WallpaperRenderer();
        WallpaperSettings settings = WallpaperSettings.loadFromPreferences(this);
        renderer.setSettings(settings);
        
        MaterialButton btnCycleSample = findViewById(R.id.btnCycleSample);
        btnCycleSample.setOnClickListener(v -> cycleSamplePalette());
        
        MaterialButton btnApplyWallpaper = findViewById(R.id.btnApplyWallpaper);
        btnApplyWallpaper.setOnClickListener(v -> {
            PermissionManager.openLiveWallpaperSettings(this);
        });
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopRendering();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceView.getHolder().getSurface().isValid()) {
            startRendering();
        }
    }
    
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        startRendering();
    }
    
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        renderer.setSurfaceSize(width, height);
    }
    
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopRendering();
    }
    
    private void startRendering() {
        if (isRendering) return;
        
        isRendering = true;
        renderThread = new Thread(() -> {
            while (isRendering) {
                Canvas canvas = null;
                try {
                    canvas = surfaceView.getHolder().lockCanvas();
                    if (canvas != null) {
                        canvas.drawColor(Color.BLACK);
                        renderer.draw(canvas);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        try {
                            surfaceView.getHolder().unlockCanvasAndPost(canvas);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                try {
                    Thread.sleep(16); // ~60fps
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        renderThread.start();
    }
    
    private void stopRendering() {
        isRendering = false;
        if (renderThread != null) {
            try {
                renderThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            renderThread = null;
        }
    }
    
    private void cycleSamplePalette() {
        currentPaletteIndex = (currentPaletteIndex + 1) % samplePalettes.length;
        renderer.setColorPalette(samplePalettes[currentPaletteIndex]);
    }
}
