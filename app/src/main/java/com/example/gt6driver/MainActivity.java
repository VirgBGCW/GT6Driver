package com.example.gt6driver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.gt6driver.data.DriverDirectory;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.LookupService;
import com.example.gt6driver.util.DeviceInfo;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GT6-Worker"; // filter this in Logcat

    // ✅ Event grid
    private RecyclerView rvEvents;
    private EventButtonAdapter eventButtonAdapter;

    // ✅ Driver spinner
    private Spinner spinnerDriver;

    private MaterialButton btnSubmit;
    private ProgressBar progress;

    // ✅ Header labels
    private TextView tvDeviceName;
    private TextView tvLocalVideos;
    private TextView tvVersion;

    private ArrayAdapter<String> driverNamesAdapter;

    private final List<EventItem> events = new ArrayList<>();
    private final List<DriverItem> drivers = new ArrayList<>();

    // ✅ selection state
    @Nullable private EventItem selectedEvent = null;

    private static final String STATE_EVENT_ID = "state_event_id";
    private static final String STATE_DRIVER_POS = "state_driver_pos";
    private int pendingRestoreEventId = -1;

    private ActivityResultLauncher<String[]> permissionLauncher;

    // WorkManager init guard
    private static volatile boolean sWMInited = false;
    // one-time kickoff guard for sync
    private static volatile boolean sSyncStarted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---------- Initialize WorkManager logging (no Application class needed) ----------
        initWorkManagerVerboseOnce();

        // ---------- Force-correct upload config at app start ----------
        // Container URL MUST include /driver. SAS must be query ONLY (no leading '?').
        com.example.gt6driver.sync.GT6MediaSync.setContainerUrl(
                this, "https://stgt6driverappprod.blob.core.windows.net/driver");
        // TODO: replace with your real SAS (no leading '?')
        com.example.gt6driver.sync.GT6MediaSync.setSas(
                this, "si=driver&spr=https&sv=2024-11-04&sr=c&sig=bkDZ74H2Fwmznej2B86lmh3eJXfQ9nI0csLwS8ixyN8%3D");
        Log.i(TAG, "Main: configured container=/driver and SAS (redacted).");

        rvEvents      = findViewById(R.id.rvEvents);
        spinnerDriver = findViewById(R.id.spinnerDriver);
        btnSubmit     = findViewById(R.id.btnSubmit);
        progress      = findViewById(R.id.progress);

        // ✅ Header labels
        tvDeviceName  = findViewById(R.id.tvDeviceName);
        tvLocalVideos = findViewById(R.id.tvLocalVideos);
        tvVersion     = findViewById(R.id.tvVersion);

        // ✅ Refresh labels on first load
        refreshHeaderLabels();

        // Keep submit button above system bars / IME
        ConstraintLayout root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(sys.bottom, ime.bottom);
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) btnSubmit.getLayoutParams();
            int baseXmlMarginPx = dp(32);
            lp.bottomMargin = baseXmlMarginPx + bottomInset + dp(16);
            btnSubmit.setLayoutParams(lp);
            return insets;
        });

        // ✅ Event grid (2 per row)
        rvEvents.setLayoutManager(new GridLayoutManager(this, 2));
        eventButtonAdapter = new EventButtonAdapter();
        rvEvents.setAdapter(eventButtonAdapter);

        // DRIVER spinner
        buildDriversFromDirectory();

        ArrayList<String> driverNames = new ArrayList<>(drivers.size());
        for (DriverItem d : drivers) driverNames.add(d.name);

        driverNamesAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_black,
                driverNames
        );
        driverNamesAdapter.setDropDownViewResource(R.layout.spinner_item_black);
        spinnerDriver.setAdapter(driverNamesAdapter);

        AdapterView.OnItemSelectedListener selListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSubmitEnabled();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                updateSubmitEnabled();
            }
        };
        spinnerDriver.setOnItemSelectedListener(selListener);

        btnSubmit.setOnClickListener(v -> {
            EventItem selectedEventLocal = selectedEvent;
            int pos = spinnerDriver.getSelectedItemPosition();
            DriverItem selectedDriver = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;

            if (!isValid(selectedEventLocal) || !isValid(selectedDriver)) {
                Toast.makeText(this, "Please select both EVENT and DRIVER.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, LookupActivity.class);
            intent.putExtra("eventName", selectedEventLocal.name);
            intent.putExtra("eventId", selectedEventLocal.id);
            intent.putExtra("driver", selectedDriver.name);
            intent.putExtra("driverNumber", selectedDriver.number);

            // Persist current driver selection for later screens
            com.example.gt6driver.session.CurrentSelection.get()
                    .setDriver(selectedDriver.number, selectedDriver.name);

            startActivity(intent);
        });

        if (savedInstanceState != null) {
            pendingRestoreEventId = savedInstanceState.getInt(STATE_EVENT_ID, -1);
            int drPos = savedInstanceState.getInt(STATE_DRIVER_POS, 0);
            spinnerDriver.setSelection(Math.max(0, drPos));
        } else {
            spinnerDriver.setSelection(0);
        }

        // Permissions → start service → kick first scan (once) AFTER permissions granted
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> ensurePermissionsAndStartSync()
        );
        ensurePermissionsAndStartSync();

        loadEventsFromApi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ✅ Refresh device name + version + local videos each time foregrounded
        refreshHeaderLabels();
    }

    // ✅ refresh header labels in one place
    private void refreshHeaderLabels() {
        if (tvDeviceName != null) {
            String deviceName = DeviceInfo.getDeviceName(this);
            tvDeviceName.setText(deviceName);
            Log.i(TAG, "Main: refreshed deviceName=" + deviceName);
        }
        if (tvVersion != null) {
            tvVersion.setText(getVersionDisplayText());
        }

        // ✅ Refresh local videos count (async so UI doesn't hitch)
        refreshLocalVideoCountAsync();
    }

    // ===================== LOCAL VIDEO COUNT =====================

    private void refreshLocalVideoCountAsync() {
        if (tvLocalVideos == null) return;

        tvLocalVideos.setText("Local Videos —");

        new Thread(() -> {
            int count = countMp4sInMoviesGT6();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (tvLocalVideos == null) return;

                if (count <= 0) {
                    tvLocalVideos.setText("No Local Videos");
                } else if (count == 1) {
                    tvLocalVideos.setText("Local Videos: 1");
                } else {
                    tvLocalVideos.setText("Local Videos: " + count);
                }
            });
        }).start();
    }

    private int countMp4sInMoviesGT6() {
        try {
            File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File gt6Dir = new File(moviesDir, "GT6");
            if (!gt6Dir.exists() || !gt6Dir.isDirectory()) return 0;
            return countMp4Recursive(gt6Dir);
        } catch (Exception e) {
            Log.w(TAG, "Local video count failed", e);
            return 0;
        }
    }

    private int countMp4Recursive(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int total = 0;
        for (File f : files) {
            if (f == null) continue;
            if (f.isDirectory()) {
                total += countMp4Recursive(f);
            } else {
                String name = f.getName();
                if (name != null && name.toLowerCase().endsWith(".mp4")) {
                    total++;
                }
            }
        }
        return total;
    }

    // ===================== VERSION =====================

    // ✅ show versionName + versionCode (supports all API levels)
    private String getVersionDisplayText() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = (pi.versionName != null) ? pi.versionName : "";
            long versionCode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ? pi.getLongVersionCode()
                    : pi.versionCode;

            if (versionName.isEmpty()) return "Build " + versionCode;
            return "v" + versionName + " (" + versionCode + ")";
        } catch (Exception e) {
            return "";
        }
    }

    // ===================== WORKMANAGER INIT =====================

    private void initWorkManagerVerboseOnce() {
        if (sWMInited) return;
        sWMInited = true;
        try {
            Configuration cfg = new Configuration.Builder()
                    .setMinimumLoggingLevel(Log.VERBOSE)
                    .build();
            WorkManager.initialize(getApplicationContext(), cfg);
            Log.i(TAG, "Main: WorkManager initialized (VERBOSE).");
        } catch (IllegalStateException already) {
            Log.i(TAG, "Main: WorkManager already initialized.");
        }
    }

    // ===== one-time kickoff guard =====
    private static void maybeStartInitialSync(android.content.Context appCtx) {
        if (sSyncStarted) return;
        sSyncStarted = true;

        // Clean up any old chains that can leave new work BLOCKED
        WorkManager wm = WorkManager.getInstance(appCtx);
        wm.cancelUniqueWork("gt6_scan_serial");
        wm.cancelUniqueWork("gt6_content_watch");
        wm.cancelAllWorkByTag("gt6_scan_now");
        wm.cancelAllWorkByTag("gt6_content_triggered");
        wm.pruneWork();

        // Enqueue fresh
        com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(appCtx);
        com.example.gt6driver.sync.GT6MediaSync.enqueueContentTriggers(appCtx);
        Log.i(TAG, "Main: enqueued initial scan + content triggers.");

        // Optional: quick debug dump to Logcat (tag: GT6-Worker)
        new Thread(() -> {
            try {
                for (WorkInfo wi : WorkManager.getInstance(appCtx)
                        .getWorkInfosForUniqueWork("gt6_scan_serial").get()) {
                    Log.i(TAG, "POST-ENQUEUE WM gt6_scan_serial → " + wi.getId()
                            + " state=" + wi.getState() + " tags=" + wi.getTags());
                }
                for (WorkInfo wi : WorkManager.getInstance(appCtx)
                        .getWorkInfosForUniqueWork("gt6_content_watch").get()) {
                    Log.i(TAG, "POST-ENQUEUE WM gt6_content_watch → " + wi.getId()
                            + " state=" + wi.getState() + " tags=" + wi.getTags());
                }
            } catch (Exception e) {
                Log.w(TAG, "WM debug fetch failed", e);
            }
        }).start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_EVENT_ID, selectedEvent != null ? selectedEvent.id : -1);
        outState.putInt(STATE_DRIVER_POS, spinnerDriver.getSelectedItemPosition());
    }

    private void updateSubmitEnabled() {
        EventItem ev = selectedEvent;
        int pos = spinnerDriver.getSelectedItemPosition();
        DriverItem dr = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;
        btnSubmit.setEnabled(isValid(ev) && isValid(dr));
    }

    private boolean isValid(@Nullable EventItem ev) {
        return ev != null && ev.id > 0;
    }

    private boolean isValid(@Nullable DriverItem dr) {
        return dr != null && dr.number > 0 && dr.name != null && !dr.name.startsWith("Select ");
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        spinnerDriver.setEnabled(!loading);
        rvEvents.setEnabled(!loading);
        btnSubmit.setEnabled(!loading && btnSubmit.isEnabled());
    }

    private void loadEventsFromApi() {
        setLoading(true);
        LookupService svc = ApiClient.getMemberApi().create(LookupService.class);

        svc.getAuctionEvents("Auction", true).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                setLoading(false);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Failed to load events (" + response.code() + ").", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JsonElement body = response.body();

                    List<EventItem> fetched = new ArrayList<>();
                    if (body.isJsonArray()) {
                        for (JsonElement topEl : body.getAsJsonArray()) {
                            if (!topEl.isJsonObject()) continue;
                            JsonObject topObj = topEl.getAsJsonObject();
                            if (!topObj.has("lookups") || !topObj.get("lookups").isJsonArray()) continue;

                            for (JsonElement luEl : topObj.getAsJsonArray("lookups")) {
                                if (!luEl.isJsonObject()) continue;
                                JsonObject lu = luEl.getAsJsonObject();

                                String name = getStringOrNull(lu, "name");
                                Integer id = getIntOrNull(lu, "id");
                                if (name != null && !name.trim().isEmpty() && id != null && id > 0) {
                                    fetched.add(new EventItem(id, capFirst(name.trim())));
                                }
                            }
                        }
                    }

                    if (!fetched.isEmpty()) {
                        events.clear();
                        events.addAll(fetched);

                        eventButtonAdapter.setEvents(events);

                        // Restore selection if we have one
                        if (pendingRestoreEventId > 0) {
                            eventButtonAdapter.selectById(pendingRestoreEventId);
                            pendingRestoreEventId = -1;
                        } else {
                            // If nothing restored, keep selection cleared
                            selectedEvent = null;
                            eventButtonAdapter.clearSelection();
                        }

                        updateSubmitEnabled();
                    } else {
                        Toast.makeText(MainActivity.this, "No events found.", Toast.LENGTH_SHORT).show();
                        selectedEvent = null;
                        eventButtonAdapter.setEvents(new ArrayList<>());
                        updateSubmitEnabled();
                    }
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Error reading events.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                setLoading(false);
                Toast.makeText(MainActivity.this, "Network error loading events.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Integer getIntOrNull(JsonObject o, String key) {
        try {
            return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getStringOrNull(JsonObject o, String key) {
        try {
            return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String capFirst(String s) {
        return s.length() > 1 ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : s.toUpperCase();
    }

    private int dp(int dps) {
        return Math.round(getResources().getDisplayMetrics().density * dps);
    }

    // ===================== DRIVER LOADING (from DriverDirectory) =====================
    private void buildDriversFromDirectory() {
        drivers.clear();

        // Placeholder first
        drivers.add(new DriverItem(-1, "Select Driver…"));

        // Pull from shared directory
        List<DriverDirectory.Entry> list = DriverDirectory.entries();
        if (list != null) {

            list.sort((a, b) -> {
                if (a == null || a.name == null) return -1;
                if (b == null || b.name == null) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

            for (DriverDirectory.Entry e : list) {
                drivers.add(new DriverItem(e.number, e.name));
            }
        }

        Toast.makeText(this, "Loaded " + Math.max(0, drivers.size() - 1) + " drivers (directory)", Toast.LENGTH_SHORT).show();
    }

    // ===================== MODELS =====================
    private static class DriverItem {
        final int number;
        final String name;
        DriverItem(int number, String name) { this.number = number; this.name = name; }
        @Override public String toString() { return name; }
    }

    private static class EventItem {
        final int id;
        final String name;
        EventItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    // ===================== EVENT GRID ADAPTER =====================
    private class EventButtonAdapter extends RecyclerView.Adapter<EventButtonAdapter.VH> {

        private final List<EventItem> data = new ArrayList<>();
        private int selectedId = -1;

        void setEvents(@Nullable List<EventItem> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }

        void clearSelection() {
            selectedId = -1;
            notifyDataSetChanged();
        }

        void selectById(int eventId) {
            selectedId = eventId;
            selectedEvent = null;

            for (EventItem e : data) {
                if (e != null && e.id == eventId) {
                    selectedEvent = e;
                    break;
                }
            }

            notifyDataSetChanged();
            updateSubmitEnabled();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_event_button, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            EventItem item = data.get(position);
            h.btn.setText(item.name);

            boolean isSelected = (item.id == selectedId);

            if (isSelected) {
                // ✅ SELECTED: white background, red text
                h.btn.setBackgroundTintList(
                        ContextCompat.getColorStateList(h.btn.getContext(), android.R.color.white)
                );
                h.btn.setTextColor(
                        ContextCompat.getColor(h.btn.getContext(), R.color.redButton)
                );
                h.btn.setAlpha(1f);
            } else {
                // ⬜ UNSELECTED: red background, white text
                h.btn.setBackgroundTintList(
                        ContextCompat.getColorStateList(h.btn.getContext(), R.color.redButton)
                );
                h.btn.setTextColor(
                        ContextCompat.getColor(h.btn.getContext(), android.R.color.white)
                );
                h.btn.setAlpha(0.95f);
            }

            h.btn.setOnClickListener(v -> {
                selectedId = item.id;
                selectedEvent = item;
                notifyDataSetChanged();
                updateSubmitEnabled();
            });
        }


        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            VH(@NonNull View itemView) {
                super(itemView);
                btn = itemView.findViewById(R.id.btnEvent);
            }
        }
    }

    // ===================== PERMISSIONS / SERVICE =====================
    private void ensurePermissionsAndStartSync() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
            return;
        }

        // Permissions ready → start the foreground watcher and kick one scan (once per process)
        startGT6SyncService();

        // Clean up old work that can cause BLOCKED
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.cancelUniqueWork("gt6_scan_serial");
        wm.cancelUniqueWork("gt6_content_watch");
        wm.cancelAllWorkByTag("gt6_scan_now");
        wm.cancelAllWorkByTag("gt6_content_triggered");
        wm.pruneWork();

        maybeStartInitialSync(getApplicationContext());

        // Optional: dump WorkManager state after enqueues
        dumpWM("POST-ENQUEUE");
    }

    private void startGT6SyncService() {
        Intent svc = new Intent(this, com.example.gt6driver.sync.GT6MediaSyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        Log.i(TAG, "Main: started GT6MediaSyncService.");
    }

    // ----- Debug helper: dump your WorkManager jobs to Logcat -----
    private void dumpWM(String label) {
        try {
            ListenableFuture<List<WorkInfo>> f1 =
                    WorkManager.getInstance(getApplicationContext())
                            .getWorkInfosForUniqueWork("gt6_scan_serial");

            ListenableFuture<List<WorkInfo>> f2 =
                    WorkManager.getInstance(getApplicationContext())
                            .getWorkInfosForUniqueWork("gt6_scan_periodic");

            ListenableFuture<List<WorkInfo>> f3 =
                    WorkManager.getInstance(getApplicationContext())
                            .getWorkInfosByTag("gt6_content_triggered");

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    for (WorkInfo wi : f1.get()) {
                        Log.i(TAG, label + " WM gt6_scan_serial → " +
                                wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                    }
                } catch (Exception ignored) {}

                try {
                    for (WorkInfo wi : f2.get()) {
                        Log.i(TAG, label + " WM gt6_scan_periodic → " +
                                wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                    }
                } catch (Exception ignored) {}

                try {
                    for (WorkInfo wi : f3.get()) {
                        Log.i(TAG, label + " WM tag:gt6_content_triggered → " +
                                wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "WM dump failed", e);
        }
    }
}















