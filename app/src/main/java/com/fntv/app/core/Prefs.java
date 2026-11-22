package com.fntv.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 统一的偏好存储入口，避免散落的 key 字符串。
 */
public final class Prefs {

    private static final String NAME = "fntv_prefs";

    public static final String HOST = "host";
    public static final String USER = "user";
    public static final String PASS = "pass";
    public static final String REMEMBER = "remember";
    public static final String LOGIN_MODE = "login_mode";
    public static final String FNID_HOST = "fnid_host";
    public static final String FNID_USER = "fnid_user";
    public static final String FNID_PASS = "fnid_pass";
    public static final String FNID_REMEMBER = "fnid_remember";
    public static final String THEME_MODE = "theme_mode";
    public static final String DECODER = "decoder_mode";
    public static final String SEEK_STEP = "seek_step";
    public static final String BUFFER_TIME = "buffer_time";
    public static final String DANMU_URL = "danmu_url";
    public static final String TOKEN = "auth_token";
    /** 详情页选集视图：true = 海报，false = 列表 */
    public static final String EPISODE_POSTER_MODE = "episode_poster_mode";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String str(Context c, String key, String def) {
        return get(c).getString(key, def);
    }

    public static void put(Context c, String key, String value) {
        get(c).edit().putString(key, value).apply();
    }

    public static int integer(Context c, String key, int def) {
        return get(c).getInt(key, def);
    }

    public static void putInt(Context c, String key, int value) {
        get(c).edit().putInt(key, value).apply();
    }

    public static boolean bool(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    public static void putBool(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static String host(Context c) {
        return str(c, HOST, "").replaceAll("/+$", "");
    }

    public static String user(Context c) {
        return str(c, USER, "");
    }
}
