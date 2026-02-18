package com.music.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.music.wallpaper.services.MusicListenerService;
import com.music.wallpaper.ui.OnboardingActivity;
import com.music.wallpaper.ui.PreviewActivity;
import com.music.wallpaper.ui.SettingsActivity;
import com.music.wallpaper.utils.PermissionManager;

/**
 * Main landing screen.
 * Shows current playing track (from SharedPrefs + live broadcast) and
 * provides navigation to preview/settings.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";

    private TextView permissionStatus;
    private MaterialButton btnEnableNotification;
    private TextView currentMusicText;

    // Live-update receiver for currently playing track
    private BroadcastReceiver trackUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isOnboardingCompleted()) {
            startOnboarding();
            return;
        }

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
        loadLastTrackInfo();       // Refresh from prefs in case music changed while paused
        registerTrackReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterTrackReceiver();
    }

    private void initializeViews() {
        permissionStatus  = findViewById(R.id.permissionStatus);
        btnEnableNotification = findViewById(R.id.btnEnableNotification);
        currentMusicText  = findViewById(R.id.currentMusicText);
        currentMusicText.setSelected(true); // enable marquee scrolling
    }

    private void setupClickListeners() {
        btnEnableNotification.setOnClickListener(v ->
                PermissionManager.openNotificationListenerSettings(this));

        // Set Wallpaper card → opens Preview first
        findViewById(R.id.btnSetWallpaper).setOnClickListener(v ->
                startActivity(new Intent(this, PreviewActivity.class)));

        // Settings button
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ── Current music display ─────────────────────────────────────────────────

    /** Load last known track from SharedPrefs (persisted by MusicListenerService). */
    private void loadLastTrackInfo() {
        SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", MODE_PRIVATE);
        String title  = prefs.getString("last_track_title", null);
        String artist = prefs.getString("last_artist_name", null);
        updateMusicText(title, artist);
    }

    private void updateMusicText(String title, String artist) {
        if (currentMusicText == null) return;
        if (title != null && !title.isEmpty()) {
            String display = artist != null && !artist.isEmpty()
                    ? title + " — " + artist
                    : title;
            currentMusicText.setText(display);
        } else {
            currentMusicText.setText(getString(R.string.main_no_music));
        }
    }

    /** Register a local broadcast receiver so the UI updates instantly when a new song plays. */
    private void registerTrackReceiver() {
        if (trackUpdateReceiver != null) return;
        trackUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String title  = intent.getStringExtra(MusicListenerService.EXTRA_TRACK_TITLE);
                String artist = intent.getStringExtra(MusicListenerService.EXTRA_ARTIST_NAME);
                updateMusicText(title, artist);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                trackUpdateReceiver,
                new IntentFilter(MusicListenerService.ACTION_COLOR_PALETTE_CHANGED));
    }

    private void unregisterTrackReceiver() {
        if (trackUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(trackUpdateReceiver);
            trackUpdateReceiver = null;
        }
    }

    // ── Permission status ─────────────────────────────────────────────────────

    private void updatePermissionStatus() {
        boolean isEnabled = PermissionManager.isNotificationListenerEnabled(this);
        if (isEnabled) {
            permissionStatus.setText(R.string.main_permission_granted);
            permissionStatus.setTextColor(
                    getResources().getColor(R.color.md_theme_light_secondary, getTheme()));
            btnEnableNotification.setEnabled(false);
        } else {
            permissionStatus.setText(R.string.main_permission_denied);
            permissionStatus.setTextColor(
                    getResources().getColor(R.color.md_theme_light_error, getTheme()));
            btnEnableNotification.setEnabled(true);
        }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    private boolean isOnboardingCompleted() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_ONBOARDING_COMPLETED, false);
    }

    private void startOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }
}