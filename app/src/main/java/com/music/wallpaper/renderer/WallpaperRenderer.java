package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.Log;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Robust Renderer Rewritten for Stability.
 * Features:
 * - Explicit logging for debugging
 * - Simplified drawing pipeline
 * - Guaranteed fallback colors
 * - Debug indicator to prove rendering
 */
public class WallpaperRenderer {
    
    private static final String TAG = "WallpaperRenderer";
    
    // Core Data
    private List<ColorBlob> colorBlobs;
    private int width = 0;
    private int height = 0;
    private ColorPalette currentPalette;
    
    // Painting Tools
    private final Paint blobPaint;
    private final Paint debugPaint;
    private final Random random = new Random();
    
    public WallpaperRenderer() {
        Log.d(TAG, "Creating new WallpaperRenderer");
        
        colorBlobs = new ArrayList<>();
        
        // Initialize Paints
        blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blobPaint.setDither(true);
        blobPaint.setStyle(Paint.Style.FILL);
        
        debugPaint = new Paint();
        debugPaint.setColor(Color.RED);
        debugPaint.setStyle(Paint.Style.FILL);
    }
    
    /**
     * Set the dimensions of the render surface.
     * This triggers blob initialization.
     */
    public void setSurfaceSize(int width, int height) {
        Log.d(TAG, "setSurfaceSize: " + width + "x" + height);
        this.width = width;
        this.height = height;
        
        // Re-initialize blobs with new dimensions
        initializeBlobs();
    }
    
    /**
     * Update the color palette.
     */
    public void setColorPalette(ColorPalette palette) {
        Log.d(TAG, "setColorPalette: " + (palette != null ? palette.getAllColors().size() + " colors" : "null"));
        this.currentPalette = palette;
        
        if (colorBlobs.isEmpty() || width <= 0) {
            // Deferred init
            return;
        }
        
        // Refresh targets
        List<Integer> colors = (palette != null) ? palette.getAllColors() : new ArrayList<>();
        if (colors.isEmpty()) colors = getDefaultColors();
        
        for (int i = 0; i < colorBlobs.size(); i++) {
            colorBlobs.get(i).targetColor = colors.get(i % colors.size());
        }
        Log.d(TAG, "Updated targets for " + colorBlobs.size() + " blobs");
    }
    
    public void setSettings(WallpaperSettings settings) {
        // Placeholder
    }
    
    /**
     * Core initialization logic.
     * Guaranteed to produce blobs if width/height are valid.
     */
    private void initializeBlobs() {
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Skipping init: Invalid dimensions");
            return;
        }
        
        Log.d(TAG, "Initializing Blobs...");
        colorBlobs.clear();
        
        List<Integer> colors;
        if (currentPalette != null && !currentPalette.getAllColors().isEmpty()) {
            colors = currentPalette.getAllColors();
        } else {
            colors = getDefaultColors();
        }
        
        Log.d(TAG, "Using " + colors.size() + " base colors");
        
        // Create 7 blobs for good coverage
        for (int i = 0; i < 7; i++) {
            ColorBlob blob = new ColorBlob();
            
            // Grid-like random distribution
            blob.x = (random.nextFloat() * 0.8f) + 0.1f; // Keep away from extreme edges
            blob.y = (random.nextFloat() * 0.8f) + 0.1f;
            
            // Movement vectors
            blob.vx = (random.nextFloat() - 0.5f) * 0.001f;
            blob.vy = (random.nextFloat() - 0.5f) * 0.001f;
            
            // Size: 50% to 90% of screen width
            float baseSize = Math.min(width, height);
            blob.radius = baseSize * (0.5f + random.nextFloat() * 0.4f);
            
            // Colors
            int color = colors.get(i % colors.size());
            blob.color = color;
            blob.targetColor = color;
            
            colorBlobs.add(blob);
        }
        
        Log.d(TAG, "Initialized " + colorBlobs.size() + " blobs");
    }
    
    private List<Integer> getDefaultColors() {
        List<Integer> defaults = new ArrayList<>();
        defaults.add(Color.parseColor("#00E5FF")); // Cyan
        defaults.add(Color.parseColor("#D500F9")); // Purple
        defaults.add(Color.parseColor("#76FF03")); // Lime
        defaults.add(Color.parseColor("#FFC400")); // Amber
        defaults.add(Color.parseColor("#F50057")); // Pink
        return defaults;
    }
    
    /**
     * MAIN DRAW METHOD
     */
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        
        // 1. Fill Background (Clear previous frame)
        canvas.drawColor(Color.BLACK);
        
        // 2. Safety Check
        if (width <= 0 || height <= 0) {
            width = canvas.getWidth();
            height = canvas.getHeight();
            initializeBlobs();
        }
        
        if (colorBlobs.isEmpty()) {
            initializeBlobs();
            // If still empty, draw emergency fallback
            if (colorBlobs.isEmpty()) {
                Log.e(TAG, "Emergency: No blobs to draw!");
                canvas.drawColor(Color.DKGRAY);
                return;
            }
        }
        
        // 3. Draw Blobs
        for (ColorBlob blob : colorBlobs) {
            // Update Physics
            updateBlobPosition(blob);
            updateBlobColor(blob);
            
            // Convert normalized to pixels
            float px = blob.x * width;
            float py = blob.y * height;
            
            // Valid Radius check
            if (blob.radius <= 1f) blob.radius = 100f;
            
            // Gradient Setup
            // Center = Opaque Color, Edge = Transparent
            int c = blob.color;
            int startColor = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c));
            int midColor   = Color.argb(128, Color.red(c), Color.green(c), Color.blue(c));
            int endColor   = Color.argb(0,   Color.red(c), Color.green(c), Color.blue(c));
            
            RadialGradient gradient = new RadialGradient(
                px, py, 
                blob.radius,
                new int[] { startColor, midColor, endColor },
                new float[] { 0.0f, 0.6f, 1.0f },
                Shader.TileMode.CLAMP
            );
            
            blobPaint.setShader(gradient);
            
            // Draw
            canvas.drawCircle(px, py, blob.radius, blobPaint);
        }
        
        // 4. Debug Indicator (Remove in production, keep for user diagnosis)
        // Red dot in top-left corner proves draw() is called and canvas is working
        // canvas.drawRect(0, 0, 20, 20, debugPaint); 
    }
    
    private void updateBlobPosition(ColorBlob blob) {
        blob.x += blob.vx;
        blob.y += blob.vy;
        
        // Bounce off walls (or wrap) - using Bounce for visibility
        if (blob.x < -0.2f || blob.x > 1.2f) blob.vx *= -1;
        if (blob.y < -0.2f || blob.y > 1.2f) blob.vy *= -1;
    }
    
    private void updateBlobColor(ColorBlob blob) {
        if (blob.color == blob.targetColor) return;
        
        float fraction = 0.05f; // 5% per frame
        
        int c1 = blob.color;
        int c2 = blob.targetColor;
        
        int a = (int) (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * fraction);
        int r = (int) (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * fraction);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * fraction);
        int b = (int) (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * fraction);
        
        blob.color = Color.argb(a, r, g, b);
        
        // Snap if close
        if (Math.abs(Color.red(c1) - Color.red(c2)) < 5 &&
            Math.abs(Color.green(c1) - Color.green(c2)) < 5 &&
            Math.abs(Color.blue(c1) - Color.blue(c2)) < 5) {
            blob.color = blob.targetColor;
        }
    }
    
    // Simple POJO
    private static class ColorBlob {
        float x, y;
        float vx, vy;
        float radius;
        int color;
        int targetColor;
    }
}
