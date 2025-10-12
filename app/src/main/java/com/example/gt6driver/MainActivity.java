// app/src/main/java/com/example/gt6driver/MainActivity.java
package com.example.gt6driver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.LookupService;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GT6-Worker"; // filter this in Logcat

    private Spinner spinnerEvent;
    private Spinner spinnerDriver;
    private MaterialButton btnSubmit;
    private ProgressBar progress;

    private ArrayAdapter<EventItem> eventAdapter;
    private ArrayAdapter<String> driverNamesAdapter;

    private final List<EventItem> events = new ArrayList<>();
    private final List<DriverItem> drivers = new ArrayList<>();

    private static final String STATE_EVENT_POS = "state_event_pos";
    private static final String STATE_DRIVER_POS = "state_driver_pos";

    private ActivityResultLauncher<String[]> permissionLauncher;

    // WorkManager init guard
    private static volatile boolean sWMInited = false;
    // one-time kickoff guard for sync
    private static volatile boolean sSyncStarted = false;

    // ----- Static hardcoded driver list -----
    private static final DriverItem[] STATIC_DRIVERS = new DriverItem[] {
            new DriverItem(-1, "Select Driver…"),
            new DriverItem(2,  "Dave Kirk"),
            new DriverItem(3,  "Michael Cooper"),
            new DriverItem(4,  "Greg Schupfer"),
            new DriverItem(5,  "Jeff Hammond"),
            new DriverItem(6,  "Scott Thomas"),
            new DriverItem(7,  "Collin Flatt"),
            new DriverItem(8,  "Jack Conroy"),
            new DriverItem(9,  "Bob Hoffman"),
            new DriverItem(10, "Dave Lightburne"),
            new DriverItem(11, "Robert \"Bobby\" Owens"),
            new DriverItem(12, "John Kindell"),
            new DriverItem(13, "Jan Kindell"),
            new DriverItem(14, "Theo Lander"),
            new DriverItem(15, "Mike Berry"),
            new DriverItem(16, "Mike Padilla"),
            new DriverItem(17, "Gary Dallmer"),
            new DriverItem(18, "Brooke Barr"),
            new DriverItem(19, "Dan Sundstrum"),
            new DriverItem(20, "Jennifer Ringle"),
            new DriverItem(21, "Curt Warner"),
            new DriverItem(31, "Deane Chenoweth"),
            new DriverItem(32, "Julie Lander"),
            new DriverItem(33, "Mike Hannam"),
            new DriverItem(34, "Andy Leoni"),
            new DriverItem(35, "Adam Bruder"),
            new DriverItem(36, "Andy Tolmachoff"),
            new DriverItem(37, "Arthur Avery Jr"),
            new DriverItem(38, "Bernhard Neumann"),
            new DriverItem(39, "Bill Caldwell"),
            new DriverItem(40, "Bob Fouraker"),
            new DriverItem(41, "Bob Reece"),
            new DriverItem(42, "Bob Kussard"),
            new DriverItem(43, "Brett Mcgee"),
            new DriverItem(44, "Brett Williams"),
            new DriverItem(45, "Bryce Albertson"),
            new DriverItem(46, "Cal Hiebert"),
            new DriverItem(47, "Chad Erwin"),
            new DriverItem(48, "Charlie Hughes"),
            new DriverItem(49, "Cherie Costello"),
            new DriverItem(50, "Chip Tally"),
            new DriverItem(51, "Christopher Hays"),
            new DriverItem(52, "Cyrus Ringle"),
            new DriverItem(53, "Dan Martinez"),
            new DriverItem(54, "Dan Forseth"),
            new DriverItem(55, "Dave Klumpp"),
            new DriverItem(56, "Dave Bryce"),
            new DriverItem(57, "David Baker"),
            new DriverItem(58, "David Zazueta"),
            new DriverItem(59, "David Breen"),
            new DriverItem(60, "Dennis Bruder"),
            new DriverItem(61, "Dirk Matthews"),
            new DriverItem(62, "Doug Hollingstead"),
            new DriverItem(63, "Earnie Lipps"),
            new DriverItem(64, "Ed Brodzinski"),
            new DriverItem(65, "Ed Dominguez"),
            new DriverItem(66, "Ernie Corral"),
            new DriverItem(67, "Farrel Rasner"),
            new DriverItem(68, "Gabriel Ellington"),
            new DriverItem(69, "Garry Eastwood"),
            new DriverItem(70, "Gene Lusian"),
            new DriverItem(71, "Gerry Callisen"),
            new DriverItem(72, "Gilbert Montez"),
            new DriverItem(73, "Gina Squires"),
            new DriverItem(74, "Jeff Schultz"),
            new DriverItem(75, "Jeff Brainard"),
            new DriverItem(76, "Jim Domenoe"),
            new DriverItem(77, "Jim Costello"),
            new DriverItem(78, "Jimmy Dustman"),
            new DriverItem(79, "Joe Noah"),
            new DriverItem(80, "Joe Borson"),
            new DriverItem(81, "John Gordon"),
            new DriverItem(82, "John Mihalka"),
            new DriverItem(83, "John Miller"),
            new DriverItem(84, "Ken Maki"),
            new DriverItem(85, "Kyndal Schultz"),
            new DriverItem(86, "Lloyd Buelt"),
            new DriverItem(87, "Loren Powell"),
            new DriverItem(88, "Marcos Aguilar"),
            new DriverItem(89, "Martin Amaya Jr."),
            new DriverItem(90, "Martin Amaya Sr."),
            new DriverItem(91, "Marty Bellanca"),
            new DriverItem(92, "Marty Lea"),
            new DriverItem(93, "Matt Yare"),
            new DriverItem(94, "Mike Stone"),
            new DriverItem(95, "Mike Cullen"),
            new DriverItem(96, "Mike Hill"),
            new DriverItem(97, "Mike Kniskern"),
            new DriverItem(98, "Mike Evans"),
            new DriverItem(99, "Mike Samer"),
            new DriverItem(100, "Mike Cooper"),
            new DriverItem(101, "Mike Dustman"),
            new DriverItem(102, "Nathaniel Anderson"),
            new DriverItem(103, "Pete Pelletier"),
            new DriverItem(104, "Pete Bergmann"),
            new DriverItem(105, "Pete Carpenter"),
            new DriverItem(106, "Peter Kirdan"),
            new DriverItem(107, "Phil Miller"),
            new DriverItem(108, "Randy Lea"),
            new DriverItem(109, "Randy Solesbee"),
            new DriverItem(110, "Rick Bell"),
            new DriverItem(111, "Rick Eckenrode"),
            new DriverItem(112, "Roland Smith"),
            new DriverItem(113, "Ron Miller"),
            new DriverItem(114, "Ron Jones"),
            new DriverItem(115, "Ryan Ringle"),
            new DriverItem(116, "Ryan Kasprzyk"),
            new DriverItem(117, "Scott Jaeckels"),
            new DriverItem(118, "Scott Tinius"),
            new DriverItem(119, "Sean McNulty"),
            new DriverItem(120, "Steve Squires"),
            new DriverItem(121, "Steve Medina"),
            new DriverItem(122, "Steve Abbit"),
            new DriverItem(123, "Steven Montez"),
            new DriverItem(124, "Terry Crawford"),
            new DriverItem(125, "Troy Bales"),
            new DriverItem(126, "Walt Miller"),
            new DriverItem(127, "Walt Brodzinski"),
            new DriverItem(128, "Hector Quinones"),
            new DriverItem(129, "Vince Fernandez"),
            new DriverItem(130, "Brian Stevens"),
            new DriverItem(131, "Steve Oestreich"),
            new DriverItem(132, "Eric Carlson"),
            new DriverItem(133, "Darryl Toupkin"),
            new DriverItem(134, "Doyle Gaines"),
            new DriverItem(135, "Dave Neumeyer"),
            new DriverItem(136, "Martin Chambers"),
            new DriverItem(137, "Kris Vesely"),
            new DriverItem(138, "Roman Chiago"),
            new DriverItem(139, "Donnie Balentine"),
            new DriverItem(140, "Michael Denny"),
            new DriverItem(141, "John Veith"),
            new DriverItem(142, "Troy Pabst"),
            new DriverItem(143, "Ken Erickson"),
            new DriverItem(144, "Joe Quintanares"),
            new DriverItem(145, "Steffany Stanfield"),
            new DriverItem(146, "Jim Ryan"),
            new DriverItem(147, "Blake Barnett"),
            new DriverItem(148, "Katherine Cox"),
            new DriverItem(149, "Craig Hamre"),
            new DriverItem(150, "Steve Shelly"),
            new DriverItem(151, "Matt Russell"),
            new DriverItem(152, "George Hammond"),
            new DriverItem(153, "Roland Bullerkist"),
            new DriverItem(154, "John Stock"),
            new DriverItem(155, "Ron Perry"),
            new DriverItem(156, "John Rorquist"),
            new DriverItem(157, "Carleton Wahl"),
            new DriverItem(158, "Chuck Williamson"),
            new DriverItem(159, "Lynette Cox"),
            new DriverItem(160, "Sean Pearce"),
            new DriverItem(161, "Rachel Hobbs"),
            new DriverItem(162, "Jon Basham"),
            new DriverItem(163, "Jonathan Hess"),
            new DriverItem(164, "Steve Klein"),
            new DriverItem(165, "David Lugo"),
            new DriverItem(166, "Andy Russell"),
            new DriverItem(167, "Chip Delano"),
            new DriverItem(168, "Tony Goe"),
            new DriverItem(169, "Mike Nolan"),
            new DriverItem(170, "Paul Schoenborn"),
            new DriverItem(171, "Lincoln Belt"),
            new DriverItem(172, "Phil Souza"),
            new DriverItem(173, "Clayton Miller"),
            new DriverItem(174, "Andrew Frederick"),
            new DriverItem(175, "Edward Corona"),
            new DriverItem(176, "Cameron Kittle"),
            new DriverItem(177, "Randy Thompson"),
            new DriverItem(178, "Ken Anderson"),
            new DriverItem(179, "Paul Jacobson"),
            new DriverItem(180, "Brian Kotula"),
            new DriverItem(181, "Bryant Miller"),
            new DriverItem(182, "Mike Richardson"),
            new DriverItem(183, "David Schalles"),
            new DriverItem(184, "Aaron Frederick"),
            new DriverItem(185, "Josh Huggett"),
            new DriverItem(186, "Katelyn Collison"),
            new DriverItem(187, "Revè Osheel"),
            new DriverItem(188, "Lexi Lunquist"),
            new DriverItem(189, "Cash Berger"),
            new DriverItem(190, "Jeni Clayton"),
            new DriverItem(191, "Shane McNulty"),
            new DriverItem(192, "Landon Halonen"),
            new DriverItem(193, "Daunte Messina"),
            new DriverItem(194, "Maya Basham"),
            new DriverItem(195, "Jake Houk"),
    };

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

        spinnerEvent = findViewById(R.id.spinnerEvent);
        spinnerDriver = findViewById(R.id.spinnerDriver);
        btnSubmit    = findViewById(R.id.btnSubmit);
        progress     = findViewById(R.id.progress);

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

        // EVENT spinner
        events.clear();
        events.add(EventItem.placeholder());
        eventAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, events);
        eventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEvent.setAdapter(eventAdapter);

        // DRIVER spinner (static list)
        buildDriversFromStatic();

        ArrayList<String> driverNames = new ArrayList<>(drivers.size());
        for (DriverItem d : drivers) driverNames.add(d.name);

        driverNamesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, driverNames);
        driverNamesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDriver.setAdapter(driverNamesAdapter);

        AdapterView.OnItemSelectedListener selListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSubmitEnabled();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                updateSubmitEnabled();
            }
        };
        spinnerEvent.setOnItemSelectedListener(selListener);
        spinnerDriver.setOnItemSelectedListener(selListener);

        btnSubmit.setOnClickListener(v -> {
            EventItem selectedEvent = (EventItem) spinnerEvent.getSelectedItem();
            int pos = spinnerDriver.getSelectedItemPosition();
            DriverItem selectedDriver = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;

            if (!isValid(selectedEvent) || !isValid(selectedDriver)) {
                Toast.makeText(this, "Please select both EVENT and DRIVER.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, LookupActivity.class);
            intent.putExtra("eventName", selectedEvent.name);
            intent.putExtra("eventId", selectedEvent.id);
            intent.putExtra("driver", selectedDriver.name);
            intent.putExtra("driverNumber", selectedDriver.number);

            // Persist current driver selection for later screens
            com.example.gt6driver.session.CurrentSelection.get()
                    .setDriver(selectedDriver.number, selectedDriver.name);

            startActivity(intent);
        });

        if (savedInstanceState != null) {
            int evPos = savedInstanceState.getInt(STATE_EVENT_POS, 0);
            int drPos = savedInstanceState.getInt(STATE_DRIVER_POS, 0);
            spinnerEvent.setSelection(Math.max(0, evPos));
            spinnerDriver.setSelection(Math.max(0, drPos));
        } else {
            spinnerEvent.setSelection(0);
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

    // ===== WorkManager verbose init =====
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_EVENT_POS, spinnerEvent.getSelectedItemPosition());
        outState.putInt(STATE_DRIVER_POS, spinnerDriver.getSelectedItemPosition());
    }

    private void updateSubmitEnabled() {
        EventItem ev = (EventItem) spinnerEvent.getSelectedItem();
        int pos = spinnerDriver.getSelectedItemPosition();
        DriverItem dr = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;
        btnSubmit.setEnabled(isValid(ev) && isValid(dr));
    }

    private boolean isValid(EventItem ev) {
        return ev != null && ev.id > 0;
    }

    private boolean isValid(DriverItem dr) {
        return dr != null && dr.number > 0 && dr.name != null && !dr.name.startsWith("Select ");
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        spinnerEvent.setEnabled(!loading);
        spinnerDriver.setEnabled(!loading);
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
                        events.add(EventItem.placeholder());
                        events.addAll(fetched);
                        eventAdapter.notifyDataSetChanged();
                        updateSubmitEnabled();
                    } else {
                        Toast.makeText(MainActivity.this, "No events found.", Toast.LENGTH_SHORT).show();
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

    // ===================== DRIVER LOADING (STATIC) =====================
    private void buildDriversFromStatic() {
        drivers.clear();
        for (DriverItem d : STATIC_DRIVERS) {
            drivers.add(d);
        }
        Toast.makeText(this, "Loaded " + Math.max(0, drivers.size() - 1) + " drivers (static)", Toast.LENGTH_SHORT).show();
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
        static EventItem placeholder() { return new EventItem(-1, "Select Event…"); }
        @Override public String toString() { return name; }
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












