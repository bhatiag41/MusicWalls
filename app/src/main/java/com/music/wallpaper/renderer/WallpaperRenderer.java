package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.Log;

import androidx.annotation.NonNull;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.utils.AnimationHelper;

/**
 * Core rendering engine for drawing animated wallpaper on Canvas.
 * Implements smooth gradient animations with breathing/pulsing effects and multiple texture modes.
 */
public class WallpaperRenderer {
    
    private static final String TAG = "WallpaperRenderer";
    
    // Animation constants
    private static final long DEFAULT_BREATHING_PERIOD_MS = 4000; // 4 seconds per cycle
    
    // Current state
    private ColorPalette currentPalette;
    private ColorPalette targetPalette;
    private float colorTransitionProgress = 1.0f; // 1.0 = fully transitioned
    private long colorTransitionStartTime = 0;
    private static final long COLOR_TRANSITION_DURATION = 2000; // 2 seconds
    
    private WallpaperSettings settings;
    private final AnimationHelper.FpsCounter fpsCounter;
    
    // Paint objects (reused for performance)
    private final Paint gradientPaint;
    private final Paint debugPaint;
    
    // Canvas dimensions
    private int width;
    private int height;
    
    public WallpaperRenderer() {
        this.currentPalette = ColorPalette.getDefaultPalette();
        this.targetPalette = this.currentPalette;
        this.fpsCounter = new AnimationHelper.FpsCounter();
        
        // Initialize reusable Paint objects
        this.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setStyle(Paint.Style.FILL);
        
        this.debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint.setColor(Color.WHITE);
        debugPaint.setTextSize(32);
        debugPaint.setShadowLayer(4, 2, 2, Color.BLACK);
    }
    
    /**
     * Updates the target color palette with smooth transition.
     *
     * @param newPalette New color palette to transition to
     */
    public void setColorPalette(@NonNull ColorPalette newPalette) {
        if (!newPalette.equals(targetPalette)) {
            this.currentPalette = getCurrentInterpolatedPalette();
            this.targetPalette = newPalette;
            this.colorTransitionProgress = 0.0f;
            this.colorTransitionStartTime = System.currentTimeMillis();
            Log.d(TAG, "Starting color transition to: " + newPalette);
        }
    }
    
    /**
     * Updates settings for the renderer.
     *
     * @param settings Wallpaper settings
     */
    public void setSettings(@NonNull WallpaperSettings settings) {
        this.settings = settings;
    }
    
    /**
     * Updates surface dimensions.
     *
     * @param width  Surface width
     * @param height Surface height
     */
    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    /**
     * Draws the wallpaper to the canvas.
     *
     * @param canvas Canvas to draw on
     */
    public void draw(@NonNull Canvas canvas) {
        if (canvas == null || width == 0 || height == 0) {
            return;
        }
        
        // Update color transition
        updateColorTransition();
        
        // Get current interpolated palette
        ColorPalette palette = getCurrentInterpolatedPalette();
        
        // Get current time for animations
        long currentTime = System.currentTimeMillis();
        
        // Calculate breathing effect
        float speedFactor = settings != null 
            ? settings.getAnimationSpeedFactor() 
            : 1.0f;
        long breathingPeriod = (long) (DEFAULT_BREATHING_PERIOD_MS / speedFactor);
        float breathingValue = AnimationHelper.breathingValue(currentTime, breathingPeriod);
        
        // Render based on texture mode
        String textureMode = settings != null 
            ? settings.getTextureMode() 
            : WallpaperSettings.DEFAULT_TEXTURE_MODE;
        
        switch (textureMode) {
            case WallpaperSettings.TEXTURE_ACRYLIC:
                drawAcrylicEffect(canvas, palette, breathingValue);
                break;
            case WallpaperSettings.TEXTURE_GLASS:
                drawGlassEffect(canvas, palette, breathingValue);
                break;
            case WallpaperSettings.TEXTURE_GRADIENT_BLUR:
            default:
                drawGradientBlur(canvas, palette, breathingValue);
                break;
        }
        
        // Draw debug overlay if enabled
        if (settings != null && settings.isDebugMode()) {
            drawDebugOverlay(canvas, palette);
        }
        
        // Update FPS counter
        fpsCounter.update();
    }
    
    /**
     * Updates color transition progress.
     */
    private void updateColorTransition() {
        if (colorTransitionProgress < 1.0f) {
            long elapsed = System.currentTimeMillis() - colorTransitionStartTime;
            colorTransitionProgress = Math.min(1.0f, elapsed / (float) COLOR_TRANSITION_DURATION);
        }
    }
    
    /**
     * Gets the current interpolated palette based on transition progress.
     */
    @NonNull
    private ColorPalette getCurrentInterpolatedPalette() {
        if (colorTransitionProgress >= 1.0f) {
            return targetPalette;
        }
        
        // Interpolate between current and target palettes
        float easedProgress = AnimationHelper.easeInOutSine(colorTransitionProgress);
        
        int dominantColor = AnimationHelper.interpolateColor(
            currentPalette.getDominantColor(),
            targetPalette.getDominantColor(),
            easedProgress
        );
        
        int vibrantColor = AnimationHelper.interpolateColor(
            currentPalette.getVibrantColor(),
            targetPalette.getVibrantColor(),
            easedProgress
        );
        
        int mutedColor = AnimationHelper.interpolateColor(
            currentPalette.getMutedColor(),
            targetPalette.getMutedColor(),
            easedProgress
        );
        
        int darkVibrantColor = AnimationHelper.interpolateColor(
            currentPalette.getDarkVibrantColor(),
            targetPalette.getDarkVibrantColor(),
            easedProgress
        );
        
        int lightVibrantColor = AnimationHelper.interpolateColor(
            currentPalette.getLightVibrantColor(),
            targetPalette.getLightVibrantColor(),
            easedProgress
        );
        
        return new ColorPalette(
            dominantColor,
            vibrantColor,
            mutedColor,
            darkVibrantColor,
            lightVibrantColor
        );
    }
    
    /**
     * Draws acrylic effect (frosted glass with noise texture).
     */
    private void drawAcrylicEffect(Canvas canvas, ColorPalette palette, float breathingValue) {
        // Draw radial gradient with breathing effect
        float intensity = settings != null ? settings.getColorIntensityFactor() : 0.8f;
        
        float centerX = width / 2f;
        float centerY = height / 2f + (breathingValue - 0.5f) * height * 0.1f;
        float radius = (float) Math.max(width, height) * (0.8f + breathingValue * 0.3f);
        
        int[] colors = applyIntensity(palette.getExtendedGradientColors(), intensity);
        
        RadialGradient gradient = new RadialGradient(
            centerX, centerY, radius,
            colors,
            new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
            Shader.TileMode.CLAMP
        );
        
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, gradientPaint);
        
        // Add subtle noise overlay (simulated with semi-transparent dots)
        Paint noisePaint = new Paint();
        noisePaint.setAlpha(20);
        for (int i = 0; i < 100; i++) {
            float x = (float) (Math.random() * width);
            float y = (float) (Math.random() * height);
            float size = (float) (Math.random() * 3 + 1);
            noisePaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, size, noisePaint);
        }
    }
    
    /**
     * Draws glass effect (transparent with subtle reflections).
     */
    private void drawGlassEffect(Canvas canvas, ColorPalette palette, float breathingValue) {
        float intensity = settings != null ? settings.getColorIntensityFactor() : 0.8f;
        
        // Draw diagonal linear gradient
        int[] colors = applyIntensity(palette.getGradientColors(), intensity);
        
        float offsetX = breathingValue * width * 0.2f;
        float offsetY = breathingValue * height * 0.2f;
        
        LinearGradient gradient = new LinearGradient(
            offsetX, offsetY,
            width - offsetX, height - offsetY,
            colors,
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, gradientPaint);
        
        // Add glass reflections (semi-transparent white gradients)
        Paint reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reflectionPaint.setAlpha(30);
        reflectionPaint.setColor(Color.WHITE);
        
        float reflectionY = height * 0.3f + breathingValue * height * 0.1f;
        canvas.drawRect(0, reflectionY, width, reflectionY + 100, reflectionPaint);
    }
    
    /**
     * Draws gradient blur effect (smooth color transitions).
     */
    private void drawGradientBlur(Canvas canvas, ColorPalette palette, float breathingValue) {
        float intensity = settings != null ? settings.getColorIntensityFactor() : 0.8f;
        
        // Draw multi-stop radial gradient
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = (float) Math.sqrt(width * width + height * height) / 2f;
        radius = radius * (0.9f + breathingValue * 0.2f);
        
        int[] colors = applyIntensity(palette.getExtendedGradientColors(), intensity);
        
        RadialGradient gradient = new RadialGradient(
            centerX, centerY, radius,
            colors,
            new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
            Shader.TileMode.CLAMP
        );
        
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, gradientPaint);
    }
    
    /**
     * Applies color intensity factor to color array.
     */
    private int[] applyIntensity(int[] colors, float intensity) {
        int[] result = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            
            // Adjust towards/away from gray based on intensity
            int gray = (r + g + b) / 3;
            r = (int) (gray + (r - gray) * intensity);
            g = (int) (gray + (g - gray) * intensity);
            b = (int) (gray + (b - gray) * intensity);
            
            result[i] = Color.rgb(r, g, b);
        }
        return result;
    }
    
    /**
     * Draws debug overlay with FPS and color information.
     */
    private void drawDebugOverlay(Canvas canvas, ColorPalette palette) {
        float fps = fpsCounter.getCurrentFps();
        
        debugPaint.setTextSize(32);
        canvas.drawText("FPS: " + String.format("%.1f", fps), 20, 50, debugPaint);
        
        debugPaint.setTextSize(24);
        canvas.drawText("Palette: " + palette.toString(), 20, 90, debugPaint);
        canvas.drawText("Transition: " + String.format("%.0f%%", colorTransitionProgress * 100), 
                       20, 120, debugPaint);
        
        if (settings != null) {
            canvas.drawText("Mode: " + settings.getTextureMode(), 20, 150, debugPaint);
            canvas.drawText("Speed: " + settings.getAnimationSpeed(), 20, 180, debugPaint);
        }
    }
    
    public float getCurrentFps() {
        return fpsCounter.getCurrentFps();
    }
}
