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
        
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupClickListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }
    
    private void initializeViews() {
        permissionStatus = findViewById(R.id.permissionStatus);
        btnEnableNotification = findViewById(R.id.btnEnableNotification);
    }
    
    private void setupClickListeners() {
        btnEnableNotification.setOnClickListener(v -> {
            PermissionManager.openNotificationListenerSettings(this);
        });
        
        MaterialButton btnSetWallpaper = findViewById(R.id.btnSetWallpaper);
        btnSetWallpaper.setOnClickListener(v -> {
            PermissionManager.openLiveWallpaperSettings(this);
        });
        
        MaterialButton btnPreview = findViewById(R.id.btnPreview);
        btnPreview.setOnClickListener(v -> {
            Intent intent = new Intent(this, PreviewActivity.class);
            startActivity(intent);
        });
        
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
            permissionStatus.setTextColor(getColor(R.color.successColor));
            btnEnableNotification.setEnabled(false);
        } else {
            permissionStatus.setText(R.string.main_permission_denied);
            permissionStatus.setTextColor(getColor(R.color.errorColor));
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