package com.music.wallpaper.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.music.wallpaper.R;
import com.music.wallpaper.managers.ColorPaletteManager;
import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.renderer.WallpaperRenderer;
import com.music.wallpaper.utils.PermissionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Preview screen — shows the live wallpaper animation before applying.
 *
 * "Default Color" button cycles through 4 built-in palettes and saves the
 * chosen one so the wallpaper uses it when no music is playing.
 *
 * "Set Wallpaper" opens the system live wallpaper picker.
 */
public class PreviewActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String PREF_DEFAULT_PALETTE_INDEX = "default_palette_index";

    private WallpaperRenderer renderer;
    private SurfaceView surfaceView;
    private Thread renderThread;
    private volatile boolean isRendering = false;

    // Built-in default palettes (same as LiveMusicWallpaperService)
    private final List<ColorPalette> defaultPalettes = buildDefaultPalettes();
    private int defaultPaletteIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        renderer = new WallpaperRenderer();

        // Apply current settings
        WallpaperSettings settings = WallpaperSettings.loadFromPreferences(this);
        renderer.setSettings(settings);

        // Load saved default palette index
        defaultPaletteIndex = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE)
                .getInt(PREF_DEFAULT_PALETTE_INDEX, 0);

        // Show the last music-derived palette if available, else the saved default
        ColorPalette currentPalette = loadLastMusicPaletteFromPrefs();
        if (currentPalette == null) {
            currentPalette = defaultPalettes.get(defaultPaletteIndex);
        }
        renderer.setColorPalette(currentPalette);

        // Default Color button — cycles through built-in palettes and saves choice
        findViewById(R.id.btnDefaultColor).setOnClickListener(v -> cycleDefaultColor());

        // Set Wallpaper button — opens system live wallpaper picker
        findViewById(R.id.btnApplyWallpaper).setOnClickListener(v ->
                PermissionManager.openLiveWallpaperSettings(this));
    }

    private void cycleDefaultColor() {
        defaultPaletteIndex = (defaultPaletteIndex + 1) % defaultPalettes.size();
        ColorPalette chosen = defaultPalettes.get(defaultPaletteIndex);
        renderer.setColorPalette(chosen);

        // Persist so the wallpaper service uses this palette when idle
        getSharedPreferences("WallpaperPrefs", MODE_PRIVATE).edit()
                .putInt(PREF_DEFAULT_PALETTE_INDEX, defaultPaletteIndex)
                .apply();
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
                    if (canvas != null) renderer.draw(canvas);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        try { surfaceView.getHolder().unlockCanvasAndPost(canvas); }
                        catch (Exception ignored) {}
                    }
                }
                try {
                    // Adaptive: fast during transitions, slow when static
                    Thread.sleep(renderer.isAnimating() ? 33 : 200);
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
            renderThread.interrupt();
            try { renderThread.join(1000); } catch (InterruptedException ignored) {}
            renderThread = null;
        }
    }

    private ColorPalette loadLastMusicPaletteFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE);
            int count = prefs.getInt("color_count", 0);
            if (count > 0) {
                List<Integer> colors = new ArrayList<>();
                for (int i = 0; i < count; i++) colors.add(prefs.getInt("color_" + i, 0));
                return new ColorPalette(colors);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<ColorPalette> buildDefaultPalettes() {
        List<ColorPalette> p = new ArrayList<>();
        // Purple / pink / blue
        p.add(new ColorPalette(
                Color.parseColor("#B23A6F"), Color.parseColor("#7A3FA0"),
                Color.parseColor("#2F4FA8"), Color.parseColor("#1E7F86"),
                Color.parseColor("#9C7A1E")));
        // Ocean blues
        p.add(new ColorPalette(
                Color.parseColor("#0D47A1"), Color.parseColor("#1565C0"),
                Color.parseColor("#1976D2"), Color.parseColor("#00838F"),
                Color.parseColor("#006064")));
        // Warm sunset
        p.add(new ColorPalette(
                Color.parseColor("#BF360C"), Color.parseColor("#E64A19"),
                Color.parseColor("#F57C00"), Color.parseColor("#FF8F00"),
                Color.parseColor("#6A1B9A")));
        // Forest greens
        p.add(new ColorPalette(
                Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32"),
                Color.parseColor("#388E3C"), Color.parseColor("#00695C"),
                Color.parseColor("#004D40")));
        return p;
    }
}
