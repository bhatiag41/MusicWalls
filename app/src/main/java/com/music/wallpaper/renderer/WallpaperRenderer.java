package com.music.wallpaper.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.music.wallpaper.models.ColorPalette;
import com.music.wallpaper.models.WallpaperSettings;
import com.music.wallpaper.utils.AnimationHelper;

/**
 * Main wallpaper renderer that orchestrates fluid gradient animation and texture effects.
 * Integrates FluidGradientRenderer and TextureManager for premium visual effects.
 */
public class WallpaperRenderer {
    
    private static final String TAG = "WallpaperRenderer";
    
    private final FluidGradientRenderer fluidRenderer;
    private final TextureManager textureManager;
    private final Paint debugPaint;
    private final AnimationHelper.FpsCounter fpsCounter;
    
    private int width;
    private int height;
    private WallpaperSettings settings;
    private ColorPalette currentPalette;
    private boolean showDebugInfo = false;
    
    public WallpaperRenderer() {
        fluidRenderer = new FluidGradientRenderer();
        textureManager = new TextureManager();
        debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint.setColor(Color.WHITE);
        debugPaint.setTextSize(32);
        debugPaint.setShadowLayer(4, 2, 2, Color.BLACK);
        fpsCounter = new AnimationHelper.FpsCounter();
        
        currentPalette = ColorPalette.getDefaultPalette();
    }
    
    /**
     * Sets the surface size.
     */
    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
        fluidRenderer.setSurfaceSize(width, height);
        textureManager.setSurfaceSize(width, height);
    }
    
    /**
     * Sets the wallpaper settings.
     */
    public void setSettings(WallpaperSettings settings) {
        this.settings = settings;
        
        if (settings != null) {
            // Update texture mode based on settings
       String textureMode = settings.getTextureMode();
            TextureManager.TextureMode mode;
            switch (textureMode.toLowerCase()) {
                case "acrylic":
                    mode = TextureManager.TextureMode.ACRYLIC;
                    break;
                case "glass":
                    mode = TextureManager.TextureMode.GLASS;
                    break;
                case "gradient_blur":
                    mode = TextureManager.TextureMode.GRADIENT_BLUR;
                    break;
                case "none":
                default:
                    mode = TextureManager.TextureMode.NONE;
                    break;
            }
            textureManager.setTextureMode(mode);
            
            // Update debug mode
            showDebugInfo = settings.isDebugMode();
        }
    }
    
    /**
     * Sets the color palette.
     */
    public void setColorPalette(ColorPalette palette) {
        if (palette != null && !palette.equals(currentPalette)) {
            currentPalette = palette;
            fluidRenderer.setColorPalette(palette);
        }
    }
    
    /**
     * Draws the wallpaper.
     */
    public void draw(Canvas canvas) {
        if (canvas == null || width == 0 || height == 0) {
            return;
        }
        
        // Update FPS counter
        fpsCounter.update();
        
        // 1. Draw fluid gradient animation (base layer)
        fluidRenderer.draw(canvas);
        
        // 2. Apply texture overlay
        if (settings != null) {
            textureManager.applyTexture(canvas);
        }
        
        // 3. Draw debug info if enabled
        if (showDebugInfo) {
            drawDebugInfo(canvas);
        }
    }
    
    /**
     * Draws debug information overlay.
     */
    private void drawDebugInfo(Canvas canvas) {
        float y = 50;
        float lineHeight = 40;
        
        // FPS
        debugPaint.setColor(Color.WHITE);
        canvas.drawText("FPS: " + fpsCounter.getCurrentFps(), 20, y, debugPaint);
        y += lineHeight;
        
        // Resolution
        canvas.drawText("Resolution: " + width + "x" + height, 20, y, debugPaint);
        y += lineHeight;
        
        // Palette info
        if (currentPalette != null) {
            canvas.drawText("Palette: " + Integer.toHexString(currentPalette.getDominantColor()), 20, y, debugPaint);
            y += lineHeight;
        }
        
        // Texture mode
        if (settings != null) {
            canvas.drawText("Texture: " + settings.getTextureMode(), 20, y, debugPaint);
            y += lineHeight;
        }
    }
    
    /**
     * Releases resources.
     */
    public void release() {
        textureManager.release();
    }
}
