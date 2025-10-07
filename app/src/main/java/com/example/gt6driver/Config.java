// app/src/main/java/com/example/gt6driver/Config.java
package com.example.gt6driver;

import android.content.Context;
import android.text.TextUtils;
import androidx.preference.PreferenceManager;

public final class Config {
    private Config(){}

    public static String dirPrefix(Context c) {
        return get(c, "dir_prefix", "GT6");
    }
    public static String videoBaseName(Context c) {
        return safeBase(get(c, "video_name", "intake"));
    }
    public static String vinBaseName(Context c) {
        return safeBase(get(c, "vin_name", "vin"));
    }
    public static String keyBaseName(Context c) {
        return safeBase(get(c, "keycheck_name", "keycheck_intake"));
    }
    public static String mileageBaseName(Context c) {
        return safeBase(get(c, "mileage_name", "mileage_intake"));
    }
    public static boolean appendTimestamp(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c)
                .getBoolean("append_timestamp", false);
    }

    private static String get(Context c, String k, String d) {
        return PreferenceManager.getDefaultSharedPreferences(c).getString(k, d);
    }
    private static String safeBase(String s) {
        if (TextUtils.isEmpty(s)) return "file";
        // strip extensions and spaces
        s = s.trim();
        int dot = s.lastIndexOf('.');
        return (dot > 0) ? s.substring(0, dot) : s;
    }
}
