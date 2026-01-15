package com.example.gt6driver.util;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public final class DeviceInfo {

    private static String cachedDeviceName;

    private DeviceInfo() {}

    public static String getDeviceName(Context context) {
        if (cachedDeviceName != null && !cachedDeviceName.trim().isEmpty()) {
            return cachedDeviceName;
        }

        String name = null;

        // 1) Most common on many devices
        name = safeGetGlobal(context, "device_name");
        if (isGood(name)) return cachedDeviceName = name.trim();

        // 2) Some Samsung / OEM builds store it here instead
        name = safeGetSystem(context, "device_name");
        if (isGood(name)) return cachedDeviceName = name.trim();

        // 3) Sometimes tied to Bluetooth name
        name = safeGetSecure(context, "bluetooth_name");
        if (isGood(name)) return cachedDeviceName = name.trim();

        // 4) Fallback: manufacturer + model (always available)
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        String fallback = (manufacturer + " " + model).trim();

        cachedDeviceName = fallback.isEmpty() ? "Android Device" : fallback;
        return cachedDeviceName;
    }

    private static boolean isGood(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim());
    }

    private static String safeGetGlobal(Context ctx, String key) {
        try { return Settings.Global.getString(ctx.getContentResolver(), key); }
        catch (Exception ignored) { return null; }
    }

    private static String safeGetSystem(Context ctx, String key) {
        try { return Settings.System.getString(ctx.getContentResolver(), key); }
        catch (Exception ignored) { return null; }
    }

    private static String safeGetSecure(Context ctx, String key) {
        try { return Settings.Secure.getString(ctx.getContentResolver(), key); }
        catch (Exception ignored) { return null; }
    }
}

