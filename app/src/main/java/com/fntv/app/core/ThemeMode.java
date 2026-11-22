package com.fntv.app.core;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 外观：跟随系统 / 浅色 / 深色。
 */
public final class ThemeMode {

    public static final int SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private ThemeMode() {
    }

    public static int current(Context context) {
        int mode = Prefs.integer(context, Prefs.THEME_MODE, SYSTEM);
        return mode < SYSTEM || mode > DARK ? SYSTEM : mode;
    }

    public static void set(Context context, int mode) {
        Prefs.putInt(context, Prefs.THEME_MODE, mode);
        apply(context);
    }

    public static void apply(Context context) {
        int mode = current(context);
        int nightMode;
        if (mode == LIGHT) nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        else if (mode == DARK) nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        else nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    public static String label(Context context, int mode) {
        if (mode == LIGHT) return "浅色";
        if (mode == DARK) return "深色";
        return "跟随系统";
    }

    public static boolean isNight(Context context) {
        int mode = current(context);
        if (mode == LIGHT) return false;
        if (mode == DARK) return true;
        int uiMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
