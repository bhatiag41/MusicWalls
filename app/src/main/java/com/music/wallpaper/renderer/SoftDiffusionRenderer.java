package com.music.wallpaper.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.utils.AnimationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Soft diffusion renderer for watercolor-style gradients.
 * Creates cloud-like, seamlessly blended color washes with no hard edges.
 */
public class SoftDiffusionRenderer {
    
    private static final int NUM_COLOR_CLOUDS = 4; // Reduced for performance
    private static final float CLOUD_RADIUS_MULTIPLIER = 2.0f;
    private static final float DRIFT_SPEED = 0.08f;
    private static final float PULSE_SPEED = 0.04f;
    private static final float PULSE_AMPLITUDE = 0.05f;
    
    private final List<ColorCloud> colorClouds;
    private final Paint cloudPaint;
    private final Paint blendPaint;
    private final Random random;
    
    private int width;
    private int height;
    private long startTime;
    private ColorPalette currentPalette;
    private ColorPalette targetPalette;
    private float transitionProgress = 1.0f;
    private static final float TRANSITION_DURATION = 3000f;
    
    public SoftDiffusionRenderer() {
        this.colorClouds = new ArrayList<>();
        this.cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.blendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.random = new Random();
        this.startTime = System.currentTimeMillis();
        
        cloudPaint.setDither(true);
        blendPaint.setDither(true);
    }
    
    /**
     * Initialize renderer with screen dimensions.
     */
    public void initialize(int width, int height) {
        this.width = width;
        this.height = height;
        
        colorClouds.clear();
        // Create initial color clouds
        for (int i = 0; i < NUM_COLOR_CLOUDS; i++) {
            colorClouds.add(new ColorCloud(width, height, random));
        }
    }
    
    /**
     * Set color palette with smooth transition.
     */
    public void setPalette(ColorPalette palette) {
        if (palette == null) return;
        
        if (currentPalette == null) {
            currentPalette = palette;
            updateCloudColors();
        } else {
            targetPalette = palette;
            transitionProgress = 0f;
        }
    }
    
    /**
     * Draw soft diffused gradients.
     */
    public void draw(Canvas canvas, long deltaTime) {
        if (width <= 0 || height <= 0 || currentPalette == null) return;
        
        float time = (System.currentTimeMillis() - startTime) / 1000f;
        
        // Handle palette transition
        if (transitionProgress < 1.0f) {
            transitionProgress += deltaTime / TRANSITION_DURATION;
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                currentPalette = targetPalette;
                updateCloudColors();
            }
        }
        
        // Clear with a solid base color derived from the palette
        int baseColor = getBaseColor();
        canvas.drawColor(baseColor);
        
        // Draw each color cloud
        for (int i = 0; i < colorClouds.size(); i++) {
            ColorCloud cloud = colorClouds.get(i);
            cloud.update(time);
            
            // Get color for this cloud
            int cloudColor = getCloudColor(i, time);
            
            // Draw cloud
            drawSoftCloud(canvas, cloud, cloudColor);
        }
        
        // Apply subtle vignette to darken edges
        drawVignette(canvas);
    }
    
    /**
     * Draw a single soft color cloud.
     */
    private void drawSoftCloud(Canvas canvas, ColorCloud cloud, int color) {
        float radius = cloud.getRadius();
        
        // Use normal blending with lower opacity to avoid white-out
        int alpha = (int) (225 * cloud.getOpacity());
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        
        // Create radial gradient
        int centerColor = Color.argb(alpha, red, green, blue);
        int edgeColor = Color.argb(0, red, green, blue);
        
        RadialGradient gradient = new RadialGradient(
            cloud.x,
            cloud.y,
            radius,
            new int[]{centerColor, edgeColor},
            new float[]{0.0f, 1.0f},
            Shader.TileMode.CLAMP
        );
        
        cloudPaint.setShader(gradient);
        // Using SRC_OVER (default) instead of SCREEN to prevent excessive brightening
        cloudPaint.setXfermode(null); 
        
        canvas.drawCircle(cloud.x, cloud.y, radius, cloudPaint);
    }
    
    /**
     * Get validated color for cloud.
     */
    private int getCloudColor(int index, float time) {
        if (currentPalette == null) return Color.CYAN;
        
        List<Integer> colors = currentPalette.getAllColors();
        if (colors.isEmpty()) return Color.CYAN;
        
        int baseColor = colors.get(index % colors.size());
        
        // Handle transition
        if (transitionProgress < 1.0f && targetPalette != null) {
            List<Integer> targetColors = targetPalette.getAllColors();
            if (!targetColors.isEmpty()) {
                int targetColor = targetColors.get(index % targetColors.size());
                baseColor = AnimationHelper.interpolateColor(baseColor, targetColor, transitionProgress);
            }
        }
        
        // Ensure color isn't too dark or too light
        return clampLuminance(baseColor);
    }
    
    /**
     * Get base background color.
     */
    private int getBaseColor() {
        if (currentPalette == null) return Color.rgb(18, 18, 18);

        int base = currentPalette.getDominantColor();

        if (transitionProgress < 1.0f && targetPalette != null) {
            base = AnimationHelper.interpolateColor(
                    base,
                    targetPalette.getDominantColor(),
                    transitionProgress
            );
        }

        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);

        hsv[1] *= 0.9f;   // keep saturation
        hsv[2] *= 0.55f;  // dark-mode sweet spot

        return Color.HSVToColor(hsv);
    }
    /**
     * Draw subtle vignette effect.
     */
    private void drawVignette(Canvas canvas) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.max(width, height) * 0.9f;
        
        RadialGradient vignette = new RadialGradient(
            centerX,
            centerY,
            radius,
            new int[]{Color.TRANSPARENT, Color.argb(80, 0, 0, 0)}, // Darker vignette
            new float[]{0.6f, 1.0f},
            Shader.TileMode.CLAMP
        );
        
        blendPaint.setShader(vignette);
        canvas.drawRect(0, 0, width, height, blendPaint);
        blendPaint.setShader(null);
    }
    
    private void updateCloudColors() {
        // Colors updated on fly
    }
    
    /**
     * Clamps luminance to stay within aesthetic range (not too dark, not too white).
     */
    private int clampLuminance(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        hsv[1] = Math.max(0.55f, hsv[1]); // richer colors
        hsv[2] = Math.max(0.4f, Math.min(0.95f, hsv[2]));

        return Color.HSVToColor(hsv);
    }
    
    /**
     * Individual color cloud.
     */
    private static class ColorCloud {
        float x, y;
        float baseX, baseY;
        float radius;
        float driftPhaseX, driftPhaseY;
        float pulsePhase;
        float baseRadius;
        float opacity;
        
        ColorCloud(int width, int height, Random random) {
            this.baseX = random.nextFloat() * width;
            this.baseY = random.nextFloat() * height;
            this.x = baseX;
            this.y = baseY;
            
            this.baseRadius = Math.max(width, height) * CLOUD_RADIUS_MULTIPLIER;
            this.radius = baseRadius;
            
            this.driftPhaseX = random.nextFloat() * (float) Math.PI * 2;
            this.driftPhaseY = random.nextFloat() * (float) Math.PI * 2;
            this.pulsePhase = random.nextFloat() * (float) Math.PI * 2;
            
            this.opacity = 0.5f + random.nextFloat() * 0.3f;
        }
        
        void update(float time) {
            float driftX = (float) Math.sin(time * DRIFT_SPEED + driftPhaseX);
            float driftY = (float) Math.cos(time * DRIFT_SPEED + driftPhaseY);
            
            float maxDrift = Math.max(radius * 0.15f, 50f);
            x = baseX + driftX * maxDrift;
            y = baseY + driftY * maxDrift;
            
            float pulse = (float) Math.sin(time * PULSE_SPEED + pulsePhase);
            radius = baseRadius * (1f + pulse * PULSE_AMPLITUDE);
        }
        
        float getRadius() {
            return radius;
        }
        
        float getOpacity() {
            return opacity;
        }
    }
}
