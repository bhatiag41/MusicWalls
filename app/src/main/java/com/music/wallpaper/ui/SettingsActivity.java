package com.music.wallpaper.ui;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.music.wallpaper.R;
import com.music.wallpaper.models.WallpaperSettings;

/**
 * Settings activity using PreferenceFragmentCompat.
 */
public class SettingsActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.settings_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Load settings fragment
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settingsContainer, new SettingsFragment())
            .commit();
    }
    
    /**
     * Settings Fragment.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            
            // Setup reset defaults button
            Preference resetPref = findPreference("reset_defaults");
            if (resetPref != null) {
                resetPref.setOnPreferenceClickListener(preference -> {
                    showResetConfirmDialog();
                    return true;
                });
            }
        }
        
        private void showResetConfirmDialog() {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_reset_title)
                .setMessage(R.string.dialog_reset_message)
                .setPositiveButton(R.string.dialog_reset_confirm, (dialog, which) -> {
                    WallpaperSettings.resetToDefaults(requireContext());
                    // Reload preferences
                    setPreferencesFromResource(R.xml.preferences, null);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
        }
    }
}
