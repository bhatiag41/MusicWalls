package com.music.wallpaper.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.music.wallpaper.MainActivity;
import com.music.wallpaper.R;

/**
 * Simple onboarding activity.
 * For this implementation, it immediately completes onboarding and goes to MainActivity.
 * In a full implementation, this would have ViewPager2 with multiple screens.
 */
public class OnboardingActivity extends AppCompatActivity {
    
    private static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // For simplified implementation, mark onboarding as complete and proceed
        // In full version, this would show ViewPager2 with onboarding screens
        completeOnboarding();
    }
    
    private void completeOnboarding() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, true).apply();
        
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
