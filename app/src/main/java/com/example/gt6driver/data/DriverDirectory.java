package com.example.gt6driver.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central directory for numeric driver codes → driver names.
 * Now loaded from API (and optionally cached).
 */
public final class DriverDirectory {

    /** Simple pair for iteration/populating UI. */
    public static class Entry {
        public final int number;
        public final String name;
        public Entry(int number, String name) {
            this.number = number;
            this.name = name;
        }
        @Override public String toString() { return name; }
    }

    // Runtime map
    private static final SparseArray<String> MAP = new SparseArray<>();
    private static Map<Integer, String> UNMODIFIABLE = Collections.unmodifiableMap(new HashMap<>());

    // Cache
    private static final String PREFS = "gt6_driver_directory";
    private static final String KEY_JSON = "drivers_json_v1";
    private static volatile boolean sLoadedFromCache = false;

    private DriverDirectory() {}

    /** Call once early (MainActivity is perfect) to restore cached directory if present. */
    public static synchronized void initFromCache(Context ctx) {
        if (sLoadedFromCache) return;
        sLoadedFromCache = true;

        if (ctx == null) return;
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_JSON, null);
        if (json == null || json.trim().isEmpty()) return;

        try {
            Type t = new TypeToken<List<CachedDriver>>() {}.getType();
            List<CachedDriver> list = new Gson().fromJson(json, t);
            if (list == null || list.isEmpty()) return;

            replaceAllInternal(list, /*save*/ false, ctx);
        } catch (Exception ignored) {
        }
    }

    /** Replace directory contents from API results (also updates cache). */
    public static synchronized void replaceAll(Context ctx, List<DriverRecord> apiDrivers) {
        if (apiDrivers == null) apiDrivers = new ArrayList<>();
        List<CachedDriver> list = new ArrayList<>(apiDrivers.size());

        for (DriverRecord d : apiDrivers) {
            if (d == null) continue;
            if (d.driverNumber <= 0) continue;

            String fullName = buildFullName(d.firstName, d.lastName);
            if (fullName.trim().isEmpty()) {
                fullName = "Driver " + d.driverNumber;
            }
            list.add(new CachedDriver(d.driverNumber, fullName));
        }

        replaceAllInternal(list, /*save*/ true, ctx);
    }

    // ---- Compatibility methods (same as your old class) ----

    /** Returns the driver name for an id, or null if unknown. */
    @Nullable public static synchronized String nameFor(int id) {
        return MAP.get(id);
    }

    /** True if we have a record for the id. */
    public static synchronized boolean contains(int id) {
        return MAP.get(id) != null;
    }

    /** Unmodifiable view if you ever want to enumerate. */
    public static synchronized Map<Integer, String> asMap() {
        return UNMODIFIABLE;
    }

    /** Returns all drivers as an iterable list of (number, name) pairs. */
    public static synchronized List<Entry> entries() {
        List<Entry> list = new ArrayList<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) {
            int key = MAP.keyAt(i);
            list.add(new Entry(key, MAP.get(key)));
        }
        return list;
    }

    /** Convenience: just the names (unordered). */
    public static synchronized List<String> names() {
        List<String> out = new ArrayList<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) out.add(MAP.valueAt(i));
        return out;
    }

    // ---- Internal helpers ----

    private static String buildFullName(String first, String last) {
        String f = (first == null) ? "" : first.trim();
        String l = (last == null) ? "" : last.trim();
        String full = (f + " " + l).trim();
        // Normalize spacing
        return full.replaceAll("\\s+", " ");
    }

    private static void replaceAllInternal(List<CachedDriver> list, boolean save, Context ctx) {
        MAP.clear();

        // fill MAP
        for (CachedDriver d : list) {
            if (d == null) continue;
            if (d.number <= 0) continue;
            if (d.name == null) continue;
            MAP.put(d.number, d.name.trim());
        }

        // build UNMODIFIABLE snapshot
        Map<Integer, String> tmp = new HashMap<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) {
            int key = MAP.keyAt(i);
            tmp.put(key, MAP.get(key));
        }
        UNMODIFIABLE = Collections.unmodifiableMap(tmp);

        // cache
        if (save && ctx != null) {
            try {
                String json = new Gson().toJson(list);
                ctx.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_JSON, json)
                        .apply();
            } catch (Exception ignored) {}
        }
    }

    /** Minimal record format for cache storage. */
    private static class CachedDriver {
        final int number;
        final String name;
        CachedDriver(int number, String name) { this.number = number; this.name = name; }
    }

    /**
     * API-facing record (you can also make this a top-level model if you prefer).
     * Matches your JSON sample.
     */
    public static class DriverRecord {
        public int driverNumber;
        public String firstName;
        public String lastName;
        public String contactId;
        public String mobilePhone;
    }
}

