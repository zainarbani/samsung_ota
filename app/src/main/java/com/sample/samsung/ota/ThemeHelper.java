package com.sample.samsung.ota;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeHelper {
    private static final String PREF_THEME = "theme_prefs";
    private static final String PREF_NIGHT_MODE = "night_mode";
    private static final int MODE_UNSET = Integer.MIN_VALUE;

    private ThemeHelper() {
    }

    public static void applySavedThemeMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(getSavedOrDefaultNightMode(context));
    }

    public static void toggleTheme(Context context, boolean currentUiDarkMode) {
        int currentMode = getSavedOrDefaultNightMode(context);
        int nextMode;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            nextMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            nextMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nextMode = currentUiDarkMode
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_YES;
        }
        saveNightMode(context, nextMode);
        AppCompatDelegate.setDefaultNightMode(nextMode);
    }

    private static int getSavedOrDefaultNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(PREF_NIGHT_MODE, MODE_UNSET);
        if (saved != MODE_UNSET) {
            return saved;
        }
        int defaultMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        saveNightMode(context, defaultMode);
        return defaultMode;
    }

    private static void saveNightMode(Context context, int mode) {
        context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_NIGHT_MODE, mode)
                .apply();
    }
}
