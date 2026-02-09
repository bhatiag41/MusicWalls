package com.music.wallpaper.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.music.wallpaper.R;
import com.music.wallpaper.utils.BlurHelper;

/**
 * MaterialCardView with glassmorphism (frosted glass) effect.
 * Automatically blurs background content for premium aesthetic.
 */
public class GlassCardView extends MaterialCardView {
    
    private Paint noisePaint;
    private Bitmap noiseBitmap;
    private Paint glassTintPaint;
    private int surfaceColor;
    private int borderColor;
    
    public GlassCardView(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    public GlassCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public GlassCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // Initialize simple paints for high performance
        glassTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassTintPaint.setStyle(Paint.Style.FILL);
        
        // Load colors from resources
        surfaceColor = context.getColor(com.music.wallpaper.R.color.glass_surface);
        borderColor = context.getColor(com.music.wallpaper.R.color.glass_border);
        
        glassTintPaint.setColor(surfaceColor);
        
        // Remove elevation shadow for pure glass look
        setCardElevation(0);
        setMaxCardElevation(0);
        
        // Set rounded corners
        setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        
        // Set stroke/border
        setStrokeColor(borderColor);
        setStrokeWidth((int) getResources().getDimension(R.dimen.button_corner_width));
        
        // Transparent background
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        
        // Create noise texture
        createNoiseTexture();
    }
    
    private void createNoiseTexture() {
        // Create a small repeatable noise texture
        int size = 64;
        noiseBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        
        java.util.Random random = new java.util.Random();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                // Subtle static noise
                int alpha = random.nextInt(20); // 0-20 alpha
                noiseBitmap.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255));
            }
        }
        
        noisePaint = new Paint();
        android.graphics.Shader shader = new android.graphics.BitmapShader(noiseBitmap, 
                android.graphics.Shader.TileMode.REPEAT, 
                android.graphics.Shader.TileMode.REPEAT);
        noisePaint.setShader(shader);
        noisePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN));
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 1. Draw Glass Tint
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), getRadius(), getRadius(), glassTintPaint);
        
        // 2. Draw Noise Overlay (Frosted effect)
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), getRadius(), getRadius(), noisePaint);
        
        // 3. Draw content (handled by super)
        super.onDraw(canvas);
    }

    // Unused method stubs to maintain API compatibility if called from elsewhere
    public void setBlurEnabled(boolean enabled) {}
    public void refreshBlur() {}
}
