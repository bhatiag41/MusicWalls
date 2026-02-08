package com.music.wallpaper.utils;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Utility class for animation helpers including color interpolation and easing functions.
 */
public class AnimationHelper {
    
    private static final long DEFAULT_COLOR_TRANSITION_DURATION = 2000; // 2 seconds
    
    /**
     * Creates a ValueAnimator for smooth color transitions.
     *
     * @param fromColor Starting color
     * @param toColor   Ending color
     * @param duration  Animation duration in milliseconds
     * @return Configured ValueAnimator
     */
    @NonNull
    public static ValueAnimator createColorAnimator(@ColorInt int fromColor,
                                                   @ColorInt int toColor,
                                                   long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }
    
    /**
     * Creates a ValueAnimator for color transitions with default duration.
     *
     * @param fromColor Starting color
     * @param toColor   Ending color
     * @return Configured ValueAnimator
     */
    @NonNull
    public static ValueAnimator createColorAnimator(@ColorInt int fromColor,
                                                   @ColorInt int toColor) {
        return createColorAnimator(fromColor, toColor, DEFAULT_COLOR_TRANSITION_DURATION);
    }
    
    /**
     * Interpolates between two colors in HSV color space for better visual results.
     *
     * @param color1   First color
     * @param color2   Second color
     * @param fraction Interpolation fraction (0.0 to 1.0)
     * @return Interpolated color
     */
    @ColorInt
    public static int interpolateColor(@ColorInt int color1,
                                      @ColorInt int color2,
                                      float fraction) {
        float[] hsv1 = new float[3];
        float[] hsv2 = new float[3];
        float[] hsvResult = new float[3];
        
        Color.colorToHSV(color1, hsv1);
        Color.colorToHSV(color2, hsv2);
        
        // Interpolate hue with wrapping (shortest path around color wheel)
        float hueDiff = hsv2[0] - hsv1[0];
        if (hueDiff > 180) {
            hueDiff -= 360;
        } else if (hueDiff < -180) {
            hueDiff += 360;
        }
        hsvResult[0] = (hsv1[0] + hueDiff * fraction) % 360;
        if (hsvResult[0] < 0) hsvResult[0] += 360;
        
        // Interpolate saturation and value linearly
        hsvResult[1] = hsv1[1] + (hsv2[1] - hsv1[1]) * fraction;
        hsvResult[2] = hsv1[2] + (hsv2[2] - hsv1[2]) * fraction;
        
        // Interpolate alpha separately
        int alpha1 = Color.alpha(color1);
        int alpha2 = Color.alpha(color2);
        int alpha = (int) (alpha1 + (alpha2 - alpha1) * fraction);
        
        int rgb = Color.HSVToColor(hsvResult);
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }
    
    /**
     * Ease in-out sine function for smooth breathing animations.
     *
     * @param t Time fraction (0.0 to 1.0)
     * @return Eased value
     */
    public static float easeInOutSine(float t) {
        return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
    }
    
    /**
     * Ease in-out quadratic function.
     *
     * @param t Time fraction (0.0 to 1.0)
     * @return Eased value
     */
    public static float easeInOutQuad(float t) {
        return t < 0.5f 
            ? 2 * t * t 
            : (float) (1 - Math.pow(-2 * t + 2, 2) / 2);
    }
    
    /**
     * Calculates breathing effect value using sine wave.
     * Returns a value that oscillates between 0.0 and 1.0.
     *
     * @param timeMs      Current time in milliseconds
     * @param periodMs    Period of one complete cycle in milliseconds
     * @param minValue    Minimum value (default 0.0)
     * @param maxValue    Maximum value (default 1.0)
     * @return Breathing effect value
     */
    public static float breathingValue(long timeMs, long periodMs, float minValue, float maxValue) {
        float phase = (timeMs % periodMs) / (float) periodMs;
        float sine = (float) Math.sin(phase * 2 * Math.PI);
        // Map from [-1, 1] to [minValue, maxValue]
        return minValue + (maxValue - minValue) * ((sine + 1) / 2);
    }
    
    /**
     * Calculates breathing effect value with default range [0.0, 1.0].
     *
     * @param timeMs   Current time in milliseconds
     * @param periodMs Period of one complete cycle in milliseconds
     * @return Breathing effect value between 0.0 and 1.0
     */
    public static float breathingValue(long timeMs, long periodMs) {
        return breathingValue(timeMs, periodMs, 0f, 1f);
    }
    
    /**
     * Simple FPS calculator using frame time tracking.
     */
    public static class FpsCounter {
        private long lastFrameTime = 0;
        private float currentFps = 60f;
        private static final float SMOOTHING_FACTOR = 0.9f;
        
        /**
         * Updates FPS calculation based on current frame time.
         *
         * @return Current smoothed FPS
         */
        public float update() {
            long currentTime = System.currentTimeMillis();
            if (lastFrameTime != 0) {
                long frameDelta = currentTime - lastFrameTime;
                if (frameDelta > 0) {
                    float instantFps = 1000f / frameDelta;
                    // Smooth the FPS value
                    currentFps = currentFps * SMOOTHING_FACTOR + instantFps * (1 - SMOOTHING_FACTOR);
                }
            }
            lastFrameTime = currentTime;
            return currentFps;
        }
        
        public float getCurrentFps() {
            return currentFps;
        }
        
        public void reset() {
            lastFrameTime = 0;
            currentFps = 60f;
        }
    }
}
