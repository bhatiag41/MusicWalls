package com.music.wallpaper.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

/**
 * Utility class for managing permissions related to wallpaper and notification access.
 */
public class PermissionManager {
    
    private static final String NOTIFICATION_LISTENER_SETTINGS_ACTION = 
        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
    
    /**
     * Checks if the NotificationListenerService is currently enabled for this app.
     *
     * @param context Android context
     * @return true if notification listener is enabled
     */
    public static boolean isNotificationListenerEnabled(@NonNull Context context) {
        ComponentName componentName = new ComponentName(
            context, 
            "com.music.wallpaper.services.MusicListenerService"
        );
        
        String flat = Settings.Secure.getString(
            context.getContentResolver(),
            "enabled_notification_listeners"
        );
        
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && cn.equals(componentName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Opens the system notification listener settings page.
     * User can enable/disable notification access for this app.
     *
     * @param context Android context
     */
    public static void openNotificationListenerSettings(@NonNull Context context) {
        try {
            Intent intent = new Intent(NOTIFICATION_LISTENER_SETTINGS_ACTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to general settings if specific intent fails
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    
    /**
     * Opens the wallpaper chooser so user can set this app as live wallpaper.
     *
     * @param context Android context
     */
    public static void openWallpaperChooser(@NonNull Context context) {
        try {
            // Try to open the live wallpaper picker
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback if wallpaper picker not available
            e.printStackTrace();
        }
    }
    
    /**
     * Opens the wallpaper settings directly to this app's wallpaper (Android 4.2+).
     * This provides a more direct path than the general wallpaper chooser.
     *
     * @param context Android context
     */
    public static void openLiveWallpaperSettings(@NonNull Context context) {
        try {
            Intent intent = new Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(context, "com.music.wallpaper.services.LiveMusicWallpaperService"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to general wallpaper chooser
            openWallpaperChooser(context);
        }
    }
}
