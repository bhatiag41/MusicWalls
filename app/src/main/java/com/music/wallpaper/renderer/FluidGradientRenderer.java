package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.music.wallpaper.models.ColorPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Advanced fluid gradient renderer with multiple moving color blobs.
 * Creates organic, flowing animations using Perlin noise and radial gradients.
 */
public class FluidGradientRenderer {
    
    private static final int BLOB_COUNT = 5;
    private static final float MIN_BLOB_RADIUS_FACTOR = 0.3f;  // 30% of screen
    private static final float MAX_BLOB_RADIUS_FACTOR = 0.6f;  // 60% of screen
    private static final float MOVEMENT_SPEED = 0.0003f;        // Base movement speed
    private static final float BREATHING_AMPLITUDE = 0.15f;      // 15% size variation
    private static final float BREATHING_SPEED = 0.5f;           // Breathing frequency
    
    private final List<ColorBlob> blobs;
    private final Paint paint;
    private final Random random;
    
    private int width;
    private int height;
    private long startTime;
    private ColorPalette currentPalette;
    private ColorPalette targetPalette;
    private float transitionProgress = 1.0f; // 1.0 = transition complete
    private static final float TRANSITION_SPEED = 0.015f; // Transition duration ~2-3 seconds
    
    /**
     * Represents a single color blob with position, velocity, and animation phase.
     */
    private static class ColorBlob {
        float x, y;                  // Position (0-1 normalized)
        float vx, vy;                // Velocity
        float baseRadius;            // Base size
        int color;                   // Current color
        int targetColor;             // Target color for transitions
        float phase;                 // Animation phase offset
        float noiseOffsetX;          // Perlin noise offset for X
        float noiseOffsetY;          // Perlin noise offset for Y
        PorterDuff.Mode blendMode;  // Blend mode for this blob
        
        ColorBlob(float x, float y, float radius, int color, float phase) {
            this.x = x;
            this.y = y;
            this.baseRadius = radius;
            this.color = color;
            this.targetColor = color;
            this.phase = phase;
            this.noiseOffsetX = (float) Math.random() * 1000;
            this.noiseOffsetY = (float) Math.random() * 1000;
            
            // Assign blend mode for variety
            PorterDuff.Mode[] modes = {
                PorterDuff.Mode.SCREEN,
                PorterDuff.Mode.MULTIPLY,
                PorterDuff.Mode.OVERLAY
            };
            this.blendMode = modes[(int) (Math.random() * modes.length)];
        }
    }
    
    public FluidGradientRenderer() {
        blobs = new ArrayList<>();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        random = new Random();
        startTime = System.currentTimeMillis();
        
        currentPalette = ColorPalette.getDefaultPalette();
        targetPalette = currentPalette;
    }
    
    /**
     * Sets the surface size and initializes blobs.
     */
    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
        initializeBlobs();
    }
    
    /**
     * Sets a new color palette with smooth transition.
     */
    public void setColorPalette(ColorPalette palette) {
        if (palette == null || palette.equals(currentPalette)) {
            return;
        }
        
        // Start transition to new palette
        targetPalette = palette;
        transitionProgress = 0.0f;
        
        // Update target colors for blobs
        int[] colors = palette.getGradientColors();
        for (int i = 0; i < blobs.size() && i < colors.length; i++) {
            blobs.get(i).targetColor = colors[i];
        }
    }
    
    /**
     * Initializes color blobs with random positions and colors.
     */
    private void initializeBlobs() {
        blobs.clear();
        
        int[] colors = currentPalette.getGradientColors();
        
        for (int i = 0; i < BLOB_COUNT; i++) {
            float x = random.nextFloat();
            float y = random.nextFloat();
            
            float radiusFactor = MIN_BLOB_RADIUS_FACTOR + 
                random.nextFloat() * (MAX_BLOB_RADIUS_FACTOR - MIN_BLOB_RADIUS_FACTOR);
            float radius = Math.min(width, height) * radiusFactor;
            
            int color = colors[i % colors.length];
            float phase = random.nextFloat() * (float) Math.PI * 2;
            
            blobs.add(new ColorBlob(x, y, radius, color, phase));
        }
    }
    
    /**
     * Draws the fluid gradient animation.
     */
    public void draw(Canvas canvas) {
        if (width == 0 || height == 0) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        float time = (currentTime - startTime) / 1000.0f;
        
        // Update color transition
        if (transitionProgress < 1.0f) {
            transitionProgress = Math.min(1.0f, transitionProgress + TRANSITION_SPEED);
            
            // Update current palette colors
            if (transitionProgress >= 1.0f) {
                currentPalette = targetPalette;
            }
        }
        
        // Clear canvas with base color
        int baseColor = interpolateColor(
            currentPalette.getDominantColor(),
            targetPalette.getDominantColor(),
            transitionProgress
        );
        canvas.drawColor(adjustAlpha(baseColor, 255));
        
        // Draw each blob
        for (ColorBlob blob : blobs) {
            updateBlob(blob, time);
            drawBlob(canvas, blob, time);
        }
    }
    
    /**
     * Updates blob position using organic movement.
     */
    private void updateBlob(ColorBlob blob, float time) {
        // Use Perlin-like noise for organic movement
        // Simplified version using sin/cos with varying frequencies
        float noiseX = (float) Math.sin(time * MOVEMENT_SPEED + blob.noiseOffsetX) * 0.5f +
                      (float) Math.sin(time * MOVEMENT_SPEED * 1.7f + blob.noiseOffsetX * 1.3f) * 0.3f +
                      (float) Math.sin(time * MOVEMENT_SPEED * 2.3f + blob.noiseOffsetX * 1.7f) * 0.2f;
        
        float noiseY = (float) Math.cos(time * MOVEMENT_SPEED + blob.noiseOffsetY) * 0.5f +
                      (float) Math.cos(time * MOVEMENT_SPEED * 1.5f + blob.noiseOffsetY * 1.5f) * 0.3f +
                      (float) Math.cos(time * MOVEMENT_SPEED * 2.1f + blob.noiseOffsetY * 1.9f) * 0.2f;
        
        // Update position
        blob.x += noiseX * 0.0001f;
        blob.y += noiseY * 0.0001f;
        
        // Wrap around edges for infinite movement
        blob.x = (blob.x + 1) % 1;
        blob.y = (blob.y + 1) % 1;
        
        // Interpolate color if transitioning
        if (transitionProgress < 1.0f) {
            blob.color = interpolateColor(blob.color, blob.targetColor, TRANSITION_SPEED * 3);
        }
    }
    
    /**
     * Draws a single blob with radial gradient.
     */
    private void drawBlob(Canvas canvas, ColorBlob blob, float time) {
        // Calculate breathing effect
        float breathingFactor = 1.0f + 
            BREATHING_AMPLITUDE * (float) Math.sin(time * BREATHING_SPEED + blob.phase);
        float currentRadius = blob.baseRadius * breathingFactor;
        
        // Calculate screen position
        float screenX = blob.x * width;
        float screenY = blob.y * height;
        
        // Create radial gradient
        int centerColor = blob.color;
        int edgeColor = adjustAlpha(blob.color, 0);
        
        RadialGradient gradient = new RadialGradient(
            screenX, screenY, currentRadius,
            new int[]{centerColor, edgeColor},
            new float[]{0.0f, 1.0f},
            Shader.TileMode.CLAMP
        );
        
        paint.setShader(gradient);
        
        // Apply blend mode
        paint.setXfermode(new PorterDuffXfermode(blob.blendMode));
        
        // Draw circle
        canvas.drawCircle(screenX, screenY, currentRadius, paint);
        
        // Reset blend mode
        paint.setXfermode(null);
    }
    
    /**
     * Interpolates between two colors.
     */
    private int interpolateColor(int colorA, int colorB, float fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        
        int aA = Color.alpha(colorA);
        int rA = Color.red(colorA);
        int gA = Color.green(colorA);
        int bA = Color.blue(colorA);
        
        int aB = Color.alpha(colorB);
        int rB = Color.red(colorB);
        int gB = Color.green(colorB);
        int bB = Color.blue(colorB);
        
        int a = (int) (aA + (aB - aA) * fraction);
        int r = (int) (rA + (rB - rA) * fraction);
        int g = (int) (gA + (gB - gA) * fraction);
        int b = (int) (bA + (bB - bA) * fraction);
        
        return Color.argb(a, r, g, b);
    }
    
    /**
     * Adjusts the alpha channel of a color.
     */
    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
