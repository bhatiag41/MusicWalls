package com.music.wallpaper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.music.wallpaper.ui.OnboardingActivity;
import com.music.wallpaper.ui.PreviewActivity;
import com.music.wallpaper.ui.SettingsActivity;
import com.music.wallpaper.utils.PermissionManager;

/**
 * Main landing screen activity.
 */
public class MainActivity extends AppCompatActivity {
    
    private static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";
    
    private TextView permissionStatus;
    private MaterialButton btnEnableNotification;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if onboarding completed
        if (!isOnboardingCompleted()) {
            startOnboarding();
            return;
        }
        
        // Make window transparent
        android.view.Window w = getWindow();
        w.setFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, 
                   android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupClickListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    private void initializeViews() {
        permissionStatus = findViewById(R.id.permissionStatus);
        btnEnableNotification = findViewById(R.id.btnEnableNotification);
        
        // Update current music text
        TextView musicText = findViewById(R.id.currentMusicText);
        musicText.setSelected(true); // Enable marquee
    }
    
    private void setupClickListeners() {
        btnEnableNotification.setOnClickListener(v -> {
            PermissionManager.openNotificationListenerSettings(this);
        });
        
        // Set Wallpaper Card
        findViewById(R.id.btnSetWallpaper).setOnClickListener(v -> {
            PermissionManager.openLiveWallpaperSettings(this);
        });
        
        // Preview Card
        findViewById(R.id.btnPreview).setOnClickListener(v -> {
            Intent intent = new Intent(this, PreviewActivity.class);
            startActivity(intent);
        });
        
        // Settings Button
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }
    
    private void updatePermissionStatus() {
        boolean isEnabled = PermissionManager.isNotificationListenerEnabled(this);
        
        if (isEnabled) {
            permissionStatus.setText(R.string.main_permission_granted);
            permissionStatus.setTextColor(getResources().getColor(R.color.md_theme_light_secondary, getTheme()));
            btnEnableNotification.setEnabled(false);
        } else {
            permissionStatus.setText(R.string.main_permission_denied);
            permissionStatus.setTextColor(getResources().getColor(R.color.md_theme_light_error, getTheme()));
            btnEnableNotification.setEnabled(true);
        }
    }
    
    private boolean isOnboardingCompleted() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false);
    }
    
    private void startOnboarding() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}