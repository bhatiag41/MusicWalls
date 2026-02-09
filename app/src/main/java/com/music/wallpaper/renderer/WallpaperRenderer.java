package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.utils.AnimationHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer that creates soft, drifting color blobs for a watercolor effect.
 * Implements the "Color Blob" design pattern requested for softer, non-circular rendering.
 */
public class WallpaperRenderer {
    
    private static final String TAG = "WallpaperRenderer";
    
    private List<ColorBlob> colorBlobs;
    private final Paint paint;
    private final Paint basePaint;
    private final AnimationHelper.FpsCounter fpsCounter;
    
    private int width;
    private int height;
    private ColorPalette currentPalette;
    
    // Settings
    private static final int BLOB_COUNT = 7;
    private static final float BLOB_SPEED_FACTOR = 0.0003f;
    
    public WallpaperRenderer() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setDither(true);
        // Use SCREEN mode for light, airy color mixing as requested
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        
        basePaint = new Paint();
        basePaint.setColor(Color.parseColor("#F5F5F5")); // Light base
        
        fpsCounter = new AnimationHelper.FpsCounter();
        colorBlobs = new ArrayList<>();
    }
    
    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
        initializeBlobs();
    }
    
    public void setColorPalette(ColorPalette palette) {
        this.currentPalette = palette;
        if (width > 0 && height > 0) {
            refreshBlobColors();
        }
    }
    
    public void setSettings(WallpaperSettings settings) {
        // Optional: adjust speed/blur based on settings
    }
    
    private void initializeBlobs() {
        colorBlobs.clear();
        
        List<Integer> colors = new ArrayList<>();
        if (currentPalette != null) {
            colors = currentPalette.getAllColors();
        }
        
        if (colors.isEmpty()) {
            // Default colors if none provided
            colors.add(Color.parseColor("#FF6B9D"));
            colors.add(Color.parseColor("#C96DD8"));
            colors.add(Color.parseColor("#6B8DD8"));
            colors.add(Color.parseColor("#5FDDE5"));
            colors.add(Color.parseColor("#FFC947"));
        }
        
        // Create one blob for each color
        for (int i = 0; i < colors.size(); i++) {
            ColorBlob blob = new ColorBlob();
            
            // Distribute blobs across screen
            // Use grid-like distribution for better coverage
            switch(i % 5) {
                case 0: blob.x = 0.2f; blob.y = 0.3f; break;
                case 1: blob.x = 0.7f; blob.y = 0.2f; break;
                case 2: blob.x = 0.5f; blob.y = 0.5f; break;
                case 3: blob.x = 0.3f; blob.y = 0.7f; break;
                case 4: blob.x = 0.8f; blob.y = 0.8f; break;
                default: 
                    blob.x = (float) Math.random();
                    blob.y = (float) Math.random();
            }
            
            // Large radius for soft coverage
            float maxDim = Math.max(width, height);
            blob.radius = maxDim * 0.6f; // 60% of screen width
            blob.color = colors.get(i);
            
            // Very slow movement
            blob.vx = 0.0001f * (float)(Math.random() - 0.5);
            blob.vy = 0.0001f * (float)(Math.random() - 0.5);
            blob.phase = (float)(Math.random() * Math.PI * 2);
            
            colorBlobs.add(blob);
        }

    }
    
    // Removed unused resetBlob 
    
    private void refreshBlobColors() {
        if (colorBlobs.isEmpty() || currentPalette == null) {
             initializeBlobs(); // Re-init if empty
             return;
        }
        
        List<Integer> colors = currentPalette.getAllColors();
        if (colors.isEmpty()) return;
        
        for (int i = 0; i < colorBlobs.size(); i++) {
            colorBlobs.get(i).color = colors.get(i % colors.size());
        }
    }
    
    public void draw(Canvas canvas) {
        // FIRST: Verify canvas is not null
        if (canvas == null) {
            return;
        }
        
        // Get canvas dimensions
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        
        // Draw base color - AMOLED BLACK
        canvas.drawColor(Color.BLACK); 
        
        // Verify we have color blobs
        if (colorBlobs == null || colorBlobs.isEmpty()) {
            // Draw test gradient to verify rendering works
            Paint testPaint = new Paint();
            android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                0, 0, canvasWidth, canvasHeight,
                Color.parseColor("#FF6B9D"),
                Color.parseColor("#5FDDE5"),
                Shader.TileMode.CLAMP
            );
            testPaint.setShader(gradient);
            canvas.drawRect(0, 0, canvasWidth, canvasHeight, testPaint);
            
            // Try to init blobs for next time
            if (width > 0 && height > 0) initializeBlobs();
            return;
        }
        
        fpsCounter.update();
        
        // Draw each color blob
        Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blobPaint.setDither(true);
        
        for (int i = 0; i < colorBlobs.size(); i++) {
            ColorBlob blob = colorBlobs.get(i);
            
            updateBlobPosition(blob);
            
            // Calculate actual pixel positions
            float centerX = blob.x * canvasWidth;
            float centerY = blob.y * canvasHeight;
            
            // Create radial gradient
            // FOR DARK MODE: Use higher opacity center to pop against black
            int[] colors = new int[] {
                setAlpha(blob.color, 200), // 80% opacity at center for vibrancy
                setAlpha(blob.color, 100), // 40% opacity at middle
                setAlpha(blob.color, 0)    // Transparent at edge
            };
            
            RadialGradient gradient = new RadialGradient(
                centerX, centerY,
                blob.radius,
                colors,
                new float[] {0f, 0.6f, 1.0f},
                Shader.TileMode.CLAMP
            );
            
            blobPaint.setShader(gradient);
            
            // Use SCREEN or ADD for glowing effect on black
            blobPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            
            // Draw the blob
            canvas.drawCircle(centerX, centerY, blob.radius, blobPaint);
        }
        
        // Update FPS logic if needed or debug info
    }
    
    private int setAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    
    // ... rest of class ...
    
    private void updateBlobPosition(ColorBlob blob) {
        blob.x += blob.vx;
        blob.y += blob.vy;
        
        // Wrap around edges
        if (blob.x > 1.2f) blob.x = -0.2f;
        if (blob.x < -0.2f) blob.x = 1.2f;
        if (blob.y > 1.2f) blob.y = -0.2f;
        if (blob.y < -0.2f) blob.y = 1.2f;
    }

    private static class ColorBlob {
        float x, y;    // Normalized 0-1
        float vx, vy;
        float radius;
        int color;
        float phase;
    }
}
