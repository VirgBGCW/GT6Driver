package com.example.gt6driver;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.gt6driver.data.DriverDirectory;
import com.example.gt6driver.model.MechanicDriverDto;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GT6-Worker";
    private static final String PREF_PRINTER_LANGUAGE = "printer_language";
    private static final String PREF_AUCTION_EXTENDED_PERIOD_DAYS = "auction_extended_period_days";
    private static final String PRINTER_LANGUAGE_ESC_POS = "escpos";
    private static final String PRINTER_LANGUAGE_EPL = "epl";
    private static final String PRINTER_NAME_PREFIX = "SPP-";
    private static final int AUCTION_EXTENDED_PERIOD_30_DAYS = 30;
    private static final int AUCTION_EXTENDED_PERIOD_60_DAYS = 60;
    private static final String[] PRINTER_LANGUAGE_VALUES = {
            PRINTER_LANGUAGE_ESC_POS,
            PRINTER_LANGUAGE_EPL
    };
    private static final String[] PRINTER_LANGUAGE_LABELS = {
            "OLD (ESC/POS)",
            "NEW (BPL-E / EPL)"
    };
    private static final String[] API_ENVIRONMENT_VALUES = {
            ApiClient.ENV_PRODUCTION,
            ApiClient.ENV_UAT
    };
    private static final String[] API_ENVIRONMENT_LABELS = {
            "PRODUCTION",
            "UAT"
    };
    private static final int OPTIONS_DIALOG_BG = Color.WHITE;
    private static final int OPTIONS_DIALOG_TEXT = Color.rgb(17, 24, 39);
    private static final int OPTIONS_DIALOG_MUTED_TEXT = Color.rgb(55, 65, 81);
    private static final int OPTIONS_DIALOG_STROKE = Color.rgb(209, 213, 219);
    private static final int OPTIONS_DIALOG_ACCENT = Color.rgb(220, 53, 69);

    // Event grid
    private RecyclerView rvEvents;
    private EventButtonAdapter eventButtonAdapter;

    // User type + Driver spinners
    private Spinner spinnerUserType;
    private Spinner spinnerDriver;

    private MaterialButton btnSubmit;
    private ProgressBar progress;

    // Header labels
    private TextView tvDeviceName;
    private TextView tvLocalVideos;
    private TextView tvUploadStatus;
    private TextView tvVersion;

    private ArrayAdapter<String> driverNamesAdapter;

    private final List<EventItem> events = new ArrayList<>();
    private final List<DriverItem> drivers = new ArrayList<>();

    @Nullable
    private EventItem selectedEvent = null;

    @Nullable
    private String selectedUserType = null;

    private static final String STATE_EVENT_ID = "state_event_id";
    private static final String STATE_DRIVER_POS = "state_driver_pos";
    private static final String STATE_USER_TYPE_POS = "state_user_type_pos";

    private int pendingRestoreEventId = -1;
    private int pendingRestoreUserTypePos = 0;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> printerPermissionLauncher;
    private String pendingPrinterTestLanguage = null;

    private static volatile boolean sWMInited = false;
    private static volatile boolean sSyncStarted = false;

    private boolean uploadStatusObserversAttached = false;
    private boolean isLoading = false;
    private final ExecutorService printerTestExec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initWorkManagerVerboseOnce();
        ApiClient.configure(this);
        StorageConfig.configureMediaSync(this);

        com.example.gt6driver.sync.GT6MediaSync.setSas(
                this, "si=driver&spr=https&sv=2024-11-04&sr=c&sig=bkDZ74H2Fwmznej2B86lmh3eJXfQ9nI0csLwS8ixyN8%3D");
        Log.i(TAG, "Main: configured storage=" + StorageConfig.driverContainerUrl() + " and SAS (redacted).");

        rvEvents = findViewById(R.id.rvEvents);
        spinnerUserType = findViewById(R.id.spinnerUserType);
        spinnerDriver = findViewById(R.id.spinnerDriver);
        btnSubmit = findViewById(R.id.btnSubmit);
        progress = findViewById(R.id.progress);

        tvDeviceName = findViewById(R.id.tvDeviceName);
        tvLocalVideos = findViewById(R.id.tvLocalVideos);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
        tvVersion = findViewById(R.id.tvVersion);

        ImageView logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnLongClickListener(v -> {
                showOptionsDialog();
                return true;
            });
        }

        refreshHeaderLabels();

        ConstraintLayout root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(sys.bottom, ime.bottom);

            ConstraintLayout.LayoutParams lp =
                    (ConstraintLayout.LayoutParams) btnSubmit.getLayoutParams();
            int baseXmlMarginPx = dp(32);
            lp.bottomMargin = baseXmlMarginPx + bottomInset + dp(16);
            btnSubmit.setLayoutParams(lp);
            return insets;
        });

        rvEvents.setLayoutManager(new GridLayoutManager(this, 2));
        eventButtonAdapter = new EventButtonAdapter();
        rvEvents.setAdapter(eventButtonAdapter);

        initUserTypeSpinner();
        initDriverSpinner();

        spinnerUserType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedUserType = getSelectedUserTypeFromPosition(position);
                updateDriverInputState();
                if (isVideoReviewer(selectedUserType)) {
                    setDriverPlaceholder("User Not Required");
                    clearDriverDirectoryCache();
                } else {
                    setDriverPlaceholder(selectedEvent == null ? "Select Event First" : "Select User Type / Loading…");
                    clearDriverDirectoryCache();
                    tryLoadDrivers();
                }
                updateSubmitEnabled();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedUserType = null;
                updateDriverInputState();
                updateSubmitEnabled();
            }
        });

        spinnerDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSubmitEnabled();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateSubmitEnabled();
            }
        });

        btnSubmit.setOnClickListener(v -> {
            EventItem selectedEventLocal = selectedEvent;
            int pos = spinnerDriver.getSelectedItemPosition();
            DriverItem selectedDriver = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;

            boolean userRequired = !isVideoReviewer(selectedUserType);
            if (!isValid(selectedEventLocal) || !isValidUserType(selectedUserType) || (userRequired && !isValid(selectedDriver))) {
                Toast.makeText(this, userRequired
                        ? "Please select EVENT, USER TYPE, and USER."
                        : "Please select EVENT and USER TYPE.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, LookupActivity.class);
            intent.putExtra("eventName", selectedEventLocal.name);
            intent.putExtra("eventId", selectedEventLocal.id);
            intent.putExtra("userType", selectedUserType);

            if (userRequired && selectedDriver != null) {
                intent.putExtra("driver", selectedDriver.name);

                if (selectedDriver.number != null) {
                    intent.putExtra("driverNumber", selectedDriver.number);
                }

                if (selectedDriver.contactId != null) {
                    intent.putExtra("contactId", selectedDriver.contactId);
                }

                com.example.gt6driver.session.CurrentSelection.get()
                        .setDriver(selectedDriver.number != null ? selectedDriver.number : 0, selectedDriver.name);
            } else {
                intent.putExtra("driver", "Video Reviewer");
                com.example.gt6driver.session.CurrentSelection.get()
                        .setDriver(0, "Video Reviewer");
            }

            startActivity(intent);
        });

        if (savedInstanceState != null) {
            pendingRestoreEventId = savedInstanceState.getInt(STATE_EVENT_ID, -1);
            pendingRestoreUserTypePos = savedInstanceState.getInt(STATE_USER_TYPE_POS, 0);
            int drPos = savedInstanceState.getInt(STATE_DRIVER_POS, 0);

            spinnerUserType.setSelection(Math.max(0, pendingRestoreUserTypePos));
            spinnerDriver.setSelection(Math.max(0, drPos));
        } else {
            spinnerUserType.setSelection(0);
            spinnerDriver.setSelection(0);
        }

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> ensurePermissionsAndStartSync()
        );
        printerPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean ok : result.values()) {
                        granted &= ok != null && ok;
                    }
                    String pendingLanguage = pendingPrinterTestLanguage;
                    pendingPrinterTestLanguage = null;
                    if (granted && pendingLanguage != null) {
                        startPrinterTest(pendingLanguage);
                    } else {
                        Toast.makeText(this, "Bluetooth permission required to print test.", Toast.LENGTH_LONG).show();
                    }
                }
        );
        ensurePermissionsAndStartSync();

        loadEventsFromApi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String previousEnvironment = ApiClient.getEnvironment();
        ApiClient.configure(this);
        boolean environmentChanged = !previousEnvironment.equals(ApiClient.getEnvironment());
        StorageConfig.configureMediaSync(this);
        refreshHeaderLabels();
        refreshUploadStatus();
        if (environmentChanged) {
            com.example.gt6driver.sync.GT6MediaSync.applyConfigAndReenqueue(this);
        }
        if (environmentChanged && eventButtonAdapter != null) {
            reloadApiBackedSelections();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        printerTestExec.shutdownNow();
    }

    private void refreshHeaderLabels() {
        if (tvDeviceName != null) {
            String deviceName = DeviceInfo.getDeviceName(this);
            String environmentLabel = ApiClient.ENV_UAT.equals(ApiClient.getEnvironment()) ? "UAT" : "PROD";
            String printerTypeLabel = currentPrinterTypeLabel();
            tvDeviceName.setText(deviceName + " - " + environmentLabel + " / " + printerTypeLabel);
            Log.i(TAG, "Main: refreshed deviceName=" + deviceName
                    + ", environment=" + environmentLabel
                    + ", printerType=" + printerTypeLabel);
        }
        if (tvVersion != null) {
            tvVersion.setText(getVersionDisplayText());
        }
        refreshLocalVideoCountAsync();
        refreshUploadStatus();
    }

    private void showOptionsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentPrinterLanguage = prefs.getString(PREF_PRINTER_LANGUAGE, PRINTER_LANGUAGE_ESC_POS);
        String currentApiEnvironment = ApiClient.getSavedEnvironment(this);
        int currentAuctionExtendedPeriodDays = getAuctionExtendedPeriodDays(prefs);
        final int[] checkedPrinter = {0};
        final int[] checkedEnvironment = {0};
        final int[] selectedAuctionExtendedPeriodDays = {currentAuctionExtendedPeriodDays};

        for (int i = 0; i < PRINTER_LANGUAGE_VALUES.length; i++) {
            if (PRINTER_LANGUAGE_VALUES[i].equals(currentPrinterLanguage)) {
                checkedPrinter[0] = i;
                break;
            }
        }
        for (int i = 0; i < API_ENVIRONMENT_VALUES.length; i++) {
            if (API_ENVIRONMENT_VALUES[i].equals(currentApiEnvironment)) {
                checkedEnvironment[0] = i;
                break;
            }
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(18), dp(24), dp(8));
        content.setBackground(optionsDialogBackground());

        TextView dialogTitle = new TextView(this);
        dialogTitle.setText("Options");
        dialogTitle.setTextColor(OPTIONS_DIALOG_TEXT);
        dialogTitle.setTextSize(22);
        dialogTitle.setTypeface(dialogTitle.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(dialogTitle);

        TextView dialogSubtitle = new TextView(this);
        dialogSubtitle.setText("Choose which member API host the app should use.");
        dialogSubtitle.setTextColor(OPTIONS_DIALOG_MUTED_TEXT);
        dialogSubtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleLp.setMargins(0, dp(4), 0, dp(8));
        dialogSubtitle.setLayoutParams(subtitleLp);
        content.addView(dialogSubtitle);

        TextView environmentTitle = dialogSectionTitle("API Environment");
        RadioGroup environmentGroup = new RadioGroup(this);
        environmentGroup.setOrientation(RadioGroup.VERTICAL);
        addRadioOptions(environmentGroup, API_ENVIRONMENT_LABELS, checkedEnvironment[0]);
        environmentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int index = radioIndexForCheckedId(group, checkedId);
            if (index >= 0) checkedEnvironment[0] = index;
        });

        TextView printerTitle = dialogSectionTitle("Label Printer Language");
        RadioGroup printerGroup = new RadioGroup(this);
        printerGroup.setOrientation(RadioGroup.VERTICAL);
        addRadioOptions(printerGroup, PRINTER_LANGUAGE_LABELS, checkedPrinter[0]);
        printerGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int index = radioIndexForCheckedId(group, checkedId);
            if (index >= 0) checkedPrinter[0] = index;
        });

        TextView auctionTitle = dialogSectionTitle("Include Auctions");
        LinearLayout auctionWindowRow = new LinearLayout(this);
        auctionWindowRow.setOrientation(LinearLayout.HORIZONTAL);
        auctionWindowRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        auctionWindowRow.setPadding(0, dp(4), 0, dp(4));

        TextView auctionWindowLabel = new TextView(this);
        auctionWindowLabel.setText("Include Auctions (30, 60)");
        auctionWindowLabel.setTextColor(OPTIONS_DIALOG_TEXT);
        auctionWindowLabel.setTextSize(16);
        LinearLayout.LayoutParams auctionLabelLp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        auctionWindowLabel.setLayoutParams(auctionLabelLp);

        Switch auctionWindowSwitch = new Switch(this);
        auctionWindowSwitch.setText(currentAuctionExtendedPeriodDays == AUCTION_EXTENDED_PERIOD_60_DAYS ? "60 days" : "30 days");
        auctionWindowSwitch.setTextColor(OPTIONS_DIALOG_TEXT);
        auctionWindowSwitch.setTextSize(16);
        auctionWindowSwitch.setChecked(currentAuctionExtendedPeriodDays == AUCTION_EXTENDED_PERIOD_60_DAYS);
        auctionWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectedAuctionExtendedPeriodDays[0] = isChecked
                    ? AUCTION_EXTENDED_PERIOD_60_DAYS
                    : AUCTION_EXTENDED_PERIOD_30_DAYS;
            buttonView.setText(isChecked ? "60 days" : "30 days");
        });

        auctionWindowRow.addView(auctionWindowLabel);
        auctionWindowRow.addView(auctionWindowSwitch);

        content.addView(environmentTitle);
        content.addView(environmentGroup);
        content.addView(auctionTitle);
        content.addView(auctionWindowRow);
        content.addView(printerTitle);
        content.addView(printerGroup);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Save", (clickedDialog, which) -> {
                    boolean apiChanged = saveApiEnvironment(checkedEnvironment[0]);
                    boolean auctionWindowChanged = saveAuctionExtendedPeriodDays(
                            prefs,
                            selectedAuctionExtendedPeriodDays[0]
                    );
                    savePrinterLanguage(prefs, checkedPrinter[0]);
                    Toast.makeText(
                            MainActivity.this,
                            "API: " + API_ENVIRONMENT_LABELS[checkedEnvironment[0]]
                                    + " | Auctions: " + selectedAuctionExtendedPeriodDays[0] + " days"
                                    + " | Printer: " + PRINTER_LANGUAGE_LABELS[checkedPrinter[0]],
                            Toast.LENGTH_SHORT
                    ).show();
                    refreshHeaderLabels();
                    if (apiChanged || auctionWindowChanged) {
                        reloadApiBackedSelections();
                    }
                })
                .setNeutralButton("Print Test", (clickedDialog, which) -> {
                    boolean apiChanged = saveApiEnvironment(checkedEnvironment[0]);
                    boolean auctionWindowChanged = saveAuctionExtendedPeriodDays(
                            prefs,
                            selectedAuctionExtendedPeriodDays[0]
                    );
                    savePrinterLanguage(prefs, checkedPrinter[0]);
                    if (apiChanged || auctionWindowChanged) {
                        reloadApiBackedSelections();
                    }
                    refreshHeaderLabels();
                    startPrinterTest(PRINTER_LANGUAGE_VALUES[checkedPrinter[0]]);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(OPTIONS_DIALOG_BG));
            }
            styleDialogButton(dialog, DialogInterface.BUTTON_POSITIVE, OPTIONS_DIALOG_ACCENT);
            styleDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL, OPTIONS_DIALOG_TEXT);
            styleDialogButton(dialog, DialogInterface.BUTTON_NEGATIVE, OPTIONS_DIALOG_MUTED_TEXT);
        });
        dialog.show();
    }

    private TextView dialogSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(OPTIONS_DIALOG_TEXT);
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(14), 0, dp(4));
        title.setLayoutParams(lp);
        return title;
    }

    private GradientDrawable optionsDialogBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(OPTIONS_DIALOG_BG);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), OPTIONS_DIALOG_STROKE);
        return background;
    }

    private void addRadioOptions(RadioGroup group, String[] labels, int checkedIndex) {
        ColorStateList radioTint = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        OPTIONS_DIALOG_ACCENT,
                        OPTIONS_DIALOG_MUTED_TEXT
                }
        );
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTag(i);
            button.setTextColor(OPTIONS_DIALOG_TEXT);
            button.setTextSize(16);
            button.setButtonTintList(radioTint);
            button.setPadding(0, dp(4), 0, dp(4));
            group.addView(button);
            if (i == checkedIndex) {
                group.check(button.getId());
            }
        }
    }

    private void styleDialogButton(AlertDialog dialog, int whichButton, int color) {
        android.widget.Button button = dialog.getButton(whichButton);
        if (button == null) return;
        button.setTextColor(color);
        button.setTextSize(14);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
    }

    private int radioIndexForCheckedId(RadioGroup group, int checkedId) {
        View checked = group.findViewById(checkedId);
        if (checked == null || !(checked.getTag() instanceof Integer)) return -1;
        return (Integer) checked.getTag();
    }

    private void savePrinterLanguage(SharedPreferences prefs, int index) {
        int safeIndex = Math.max(0, Math.min(PRINTER_LANGUAGE_VALUES.length - 1, index));
        prefs.edit()
                .putString(PREF_PRINTER_LANGUAGE, PRINTER_LANGUAGE_VALUES[safeIndex])
                .apply();
    }

    private int getAuctionExtendedPeriodDays(SharedPreferences prefs) {
        int days = prefs.getInt(
                PREF_AUCTION_EXTENDED_PERIOD_DAYS,
                AUCTION_EXTENDED_PERIOD_30_DAYS
        );
        return days == AUCTION_EXTENDED_PERIOD_60_DAYS
                ? AUCTION_EXTENDED_PERIOD_60_DAYS
                : AUCTION_EXTENDED_PERIOD_30_DAYS;
    }

    private boolean saveAuctionExtendedPeriodDays(SharedPreferences prefs, int days) {
        int normalized = days == AUCTION_EXTENDED_PERIOD_60_DAYS
                ? AUCTION_EXTENDED_PERIOD_60_DAYS
                : AUCTION_EXTENDED_PERIOD_30_DAYS;
        int current = getAuctionExtendedPeriodDays(prefs);
        prefs.edit()
                .putInt(PREF_AUCTION_EXTENDED_PERIOD_DAYS, normalized)
                .apply();
        return normalized != current;
    }

    private boolean saveApiEnvironment(int index) {
        int safeIndex = Math.max(0, Math.min(API_ENVIRONMENT_VALUES.length - 1, index));
        String next = API_ENVIRONMENT_VALUES[safeIndex];
        String current = ApiClient.getSavedEnvironment(this);
        ApiClient.saveEnvironment(this, next);
        boolean changed = !next.equals(current);
        StorageConfig.configureMediaSync(this);
        if (changed) {
            com.example.gt6driver.sync.GT6MediaSync.applyConfigAndReenqueue(this);
        }
        return changed;
    }

    private void reloadApiBackedSelections() {
        pendingRestoreEventId = -1;
        selectedEvent = null;
        events.clear();
        if (eventButtonAdapter != null) {
            eventButtonAdapter.setEvents(new ArrayList<>());
            eventButtonAdapter.clearSelection();
        }
        setDriverPlaceholder("Select Event First");
        clearDriverDirectoryCache();
        loadEventsFromApi();
    }

    private void startPrinterTest(String language) {
        if (!ensurePrinterTestPermissions(language)) {
            return;
        }

        printerTestExec.execute(() -> {
            String label = printerLanguageLabel(language);
            try {
                Log.i(TAG, "Starting in-app printer test. language=" + language);
                if (PRINTER_LANGUAGE_ESC_POS.equals(language)) {
                    BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
                    try {
                        esc.connectByNamePrefix(PRINTER_NAME_PREFIX);
                        esc.printText("GT6 PRINTER TEST\nMODE: ESC/POS\nBLUETOOTH: SPP-\n\n");
                    } finally {
                        esc.close();
                    }
                } else {
                    BixolonTsplPrinter printer = new BixolonTsplPrinter();
                    try {
                        printer.connectByNameSmart(PRINTER_NAME_PREFIX);
                        printer.printEplTextTest();
                    } finally {
                        printer.close();
                    }
                }

                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Printer test sent: " + label,
                        Toast.LENGTH_SHORT
                ).show());
            } catch (Exception e) {
                Log.e(TAG, "Printer test failed. language=" + language, e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Printer test failed: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private boolean ensurePrinterTestPermissions(String language) {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            pendingPrinterTestLanguage = language;
            printerPermissionLauncher.launch(needed.toArray(new String[0]));
            return false;
        }
        return true;
    }

    private String printerLanguageLabel(String language) {
        for (int i = 0; i < PRINTER_LANGUAGE_VALUES.length; i++) {
            if (PRINTER_LANGUAGE_VALUES[i].equals(language)) {
                return PRINTER_LANGUAGE_LABELS[i];
            }
        }
        return "OLD (ESC/POS)";
    }

    private String currentPrinterTypeLabel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String language = prefs.getString(PREF_PRINTER_LANGUAGE, PRINTER_LANGUAGE_ESC_POS);
        return PRINTER_LANGUAGE_EPL.equals(language) ? "NEW" : "OLD";
    }

    // ===================== LOCAL VIDEO COUNT =====================

    private void refreshLocalVideoCountAsync() {
        if (tvLocalVideos == null) return;

        tvLocalVideos.setText("Local Videos —");
        tvLocalVideos.setClickable(false);
        tvLocalVideos.setFocusable(false);
        tvLocalVideos.setOnClickListener(null);
        tvLocalVideos.setTextColor(Color.WHITE);

        new Thread(() -> {
            int count = countMp4sInMoviesGT6();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (tvLocalVideos == null) return;

                if (count <= 0) {
                    tvLocalVideos.setText("No Local Videos");
                    tvLocalVideos.setClickable(false);
                    tvLocalVideos.setFocusable(false);
                    tvLocalVideos.setOnClickListener(null);
                    tvLocalVideos.setTextColor(Color.WHITE);
                } else {
                    tvLocalVideos.setText("Local Videos: " + count + " (tap to view)");
                    tvLocalVideos.setClickable(true);
                    tvLocalVideos.setFocusable(true);
                    tvLocalVideos.setTextColor(Color.parseColor("#9AD0FF"));
                    tvLocalVideos.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LocalVideosActivity.class)));
                }
            });
        }).start();
    }

    private void refreshUploadStatus() {
        if (tvUploadStatus == null) return;

        if (!uploadStatusObserversAttached) {
            uploadStatusObserversAttached = true;
            WorkManager wm = WorkManager.getInstance(getApplicationContext());
            observeWorkInfos(wm.getWorkInfosForUniqueWorkLiveData("gt6_scan_serial"));
            observeWorkInfos(wm.getWorkInfosForUniqueWorkLiveData("gt6_scan_periodic"));
            observeWorkInfos(wm.getWorkInfosByTagLiveData("gt6_content_triggered"));
            observeWorkInfos(wm.getWorkInfosByTagLiveData("gt6_scan_now"));
        }

        if (tvUploadStatus.getText() == null || tvUploadStatus.getText().toString().trim().isEmpty()) {
            tvUploadStatus.setText("Upload Status: idle");
        }
    }

    private void observeWorkInfos(LiveData<List<WorkInfo>> liveData) {
        if (liveData == null) return;
        liveData.observe(this, infos -> updateUploadStatusText(infos));
    }

    private void updateUploadStatusText(@Nullable List<WorkInfo> infos) {
        if (tvUploadStatus == null || infos == null || infos.isEmpty()) return;

        WorkInfo running = null;
        WorkInfo enqueued = null;
        WorkInfo succeeded = null;
        for (WorkInfo wi : infos) {
            if (wi == null) continue;
            if (wi.getState() == WorkInfo.State.RUNNING) {
                running = wi;
                break;
            }
            if (wi.getState() == WorkInfo.State.ENQUEUED) enqueued = wi;
            if (wi.getState() == WorkInfo.State.SUCCEEDED) succeeded = wi;
        }

        if (running != null) {
            int processed = running.getProgress().getInt(com.example.gt6driver.sync.MediaUploadWorker.PROGRESS_UPLOADED, 0);
            int total = running.getProgress().getInt(com.example.gt6driver.sync.MediaUploadWorker.PROGRESS_TOTAL, 0);
            int percent = running.getProgress().getInt(com.example.gt6driver.sync.MediaUploadWorker.PROGRESS_PERCENT, 0);
            if (total > 0) {
                tvUploadStatus.setText("Upload Status: " + percent + "% (" + processed + "/" + total + ")");
            } else {
                tvUploadStatus.setText("Upload Status: uploading…");
            }
            return;
        }

        if (enqueued != null) {
            tvUploadStatus.setText("Upload Status: queued");
            return;
        }

        if (succeeded != null) {
            int total = succeeded.getProgress().getInt(com.example.gt6driver.sync.MediaUploadWorker.PROGRESS_TOTAL, 0);
            if (total > 0) {
                tvUploadStatus.setText("Upload Status: 100% (" + total + "/" + total + ")");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Upload Status: idle");
                    }
                    refreshLocalVideoCountAsync();
                }, 1200);
            } else {
                tvUploadStatus.setText("Upload Status: idle");
                refreshLocalVideoCountAsync();
            }
        }
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
            if (f.isDirectory()) total += countMp4Recursive(f);
            else {
                String name = f.getName();
                if (name != null && name.toLowerCase(Locale.US).endsWith(".mp4")) total++;
            }
        }
        return total;
    }

    // ===================== VERSION =====================

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

    private static void maybeStartInitialSync(android.content.Context appCtx) {
        if (sSyncStarted) return;
        sSyncStarted = true;

        WorkManager wm = WorkManager.getInstance(appCtx);
        wm.cancelUniqueWork("gt6_scan_serial");
        wm.cancelUniqueWork("gt6_content_watch");
        wm.cancelAllWorkByTag("gt6_scan_now");
        wm.cancelAllWorkByTag("gt6_content_triggered");
        wm.pruneWork();

        com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(appCtx);
        com.example.gt6driver.sync.GT6MediaSync.enqueueContentTriggers(appCtx);
        Log.i(TAG, "Main: enqueued initial scan + content triggers.");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_EVENT_ID, selectedEvent != null ? selectedEvent.id : -1);
        outState.putInt(STATE_USER_TYPE_POS, spinnerUserType.getSelectedItemPosition());
        outState.putInt(STATE_DRIVER_POS, spinnerDriver.getSelectedItemPosition());
    }

    private void updateSubmitEnabled() {
        EventItem ev = selectedEvent;
        int pos = spinnerDriver.getSelectedItemPosition();
        DriverItem dr = (pos >= 0 && pos < drivers.size()) ? drivers.get(pos) : null;
        boolean userRequired = !isVideoReviewer(selectedUserType);
        btnSubmit.setEnabled(isValid(ev) && isValidUserType(selectedUserType) && (!userRequired || isValid(dr)));
    }

    private boolean isValid(@Nullable EventItem ev) {
        return ev != null && ev.id > 0;
    }

    private boolean isValidUserType(@Nullable String userType) {
        return userType != null
                && !userType.trim().isEmpty()
                && !userType.startsWith("Select ");
    }

    private boolean isVideoReviewer(@Nullable String userType) {
        return userType != null && "Video Reviewer".equalsIgnoreCase(userType.trim());
    }

    private boolean isValid(@Nullable DriverItem dr) {
        return dr != null
                && dr.name != null
                && !dr.name.startsWith("Select ")
                && !dr.name.startsWith("Loading")
                && !dr.name.startsWith("No Users")
                && (
                (dr.number != null && dr.number > 0) ||
                        (dr.contactId != null && !dr.contactId.trim().isEmpty())
        );
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        spinnerUserType.setEnabled(!loading);
        updateDriverInputState();
        rvEvents.setEnabled(!loading);
        updateSubmitEnabled();
    }

    private void updateDriverInputState() {
        if (spinnerDriver == null) return;

        boolean videoReviewer = isVideoReviewer(selectedUserType);
        boolean enabled = !isLoading && !videoReviewer;

        spinnerDriver.setEnabled(enabled);
        spinnerDriver.setClickable(enabled);
        spinnerDriver.setFocusable(enabled);
        spinnerDriver.setAlpha(videoReviewer ? 0.5f : 1.0f);
    }

    // ===================== EVENTS =====================

    private void loadEventsFromApi() {
        final String requestBaseUrl = ApiClient.getCurrentBaseUrl();
        setLoading(true);
        LookupService svc = ApiClient.getMemberApi().create(LookupService.class);

        int auctionExtendedPeriodDays = getAuctionExtendedPeriodDays(
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        Log.i(TAG, "Loading auction events with extendedPeriodInDays=" + auctionExtendedPeriodDays);

        svc.getAuctionEvents("Auction", true, auctionExtendedPeriodDays).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!requestBaseUrl.equals(ApiClient.getCurrentBaseUrl())) return;
                setLoading(false);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this,
                            "Failed to load events (" + response.code() + ").",
                            Toast.LENGTH_SHORT).show();
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
                                    if (id == 624) {
                                        Log.i(TAG, "Skipping filtered auction id=624 (" + name + ")");
                                        continue;
                                    }

                                    long sortMs = extractEventSortTimeMs(lu);
                                    fetched.add(new EventItem(id, capFirst(name.trim()), sortMs));
                                }
                            }
                        }
                    }

                    fetched.sort((a, b) -> {
                        boolean aHas = a.sortTimeMs > 0;
                        boolean bHas = b.sortTimeMs > 0;

                        if (aHas && bHas) return Long.compare(a.sortTimeMs, b.sortTimeMs);
                        if (aHas) return -1;
                        if (bHas) return 1;
                        return a.name.compareToIgnoreCase(b.name);
                    });

                    if (!fetched.isEmpty()) {
                        events.clear();
                        events.addAll(fetched);

                        eventButtonAdapter.setEvents(events);

                        if (pendingRestoreEventId > 0) {
                            eventButtonAdapter.selectById(pendingRestoreEventId);
                            pendingRestoreEventId = -1;
                            tryLoadDrivers();
                        } else {
                            selectedEvent = null;
                            eventButtonAdapter.clearSelection();
                            setDriverPlaceholder("Select Event First");
                            clearDriverDirectoryCache();
                        }

                        updateSubmitEnabled();
                    } else {
                        Toast.makeText(MainActivity.this, "No events found.", Toast.LENGTH_SHORT).show();
                        selectedEvent = null;
                        eventButtonAdapter.setEvents(new ArrayList<>());
                        setDriverPlaceholder("Select Event First");
                        clearDriverDirectoryCache();
                        updateSubmitEnabled();
                    }
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Error reading events.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                if (!requestBaseUrl.equals(ApiClient.getCurrentBaseUrl())) return;
                setLoading(false);
                Toast.makeText(MainActivity.this, "Network error loading events.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private long extractEventSortTimeMs(JsonObject lu) {
        String[] keys = new String[]{"startDate", "eventStartDate", "start", "date", "beginDate", "startTime"};
        for (String k : keys) {
            String s = getStringOrNull(lu, k);
            long t = parseDateToEpochMs(s);
            if (t > 0) return t;
        }

        try {
            if (lu.has("startDate") && lu.get("startDate").isJsonObject()) {
                JsonObject o = lu.getAsJsonObject("startDate");
                String s = getStringOrNull(o, "dateTime");
                long t = parseDateToEpochMs(s);
                if (t > 0) return t;
            }
        } catch (Exception ignored) {
        }

        return 0L;
    }

    private long parseDateToEpochMs(String s) {
        if (s == null) return 0L;
        s = s.trim();
        if (s.isEmpty()) return 0L;

        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(s)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return java.time.LocalDate.parse(s)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            java.time.format.DateTimeFormatter f =
                    java.time.format.DateTimeFormatter.ofPattern("M/d/uuuu");
            return java.time.LocalDate.parse(s, f)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }

        return 0L;
    }

    // ===================== USER TYPE =====================

    private void initUserTypeSpinner() {
        ArrayList<String> userTypes = new ArrayList<>();
        userTypes.add("Select User Type");
        userTypes.add("Driver");
        userTypes.add("Property");
        userTypes.add("Key");
        userTypes.add("Mechanic");
        userTypes.add("Video Reviewer");

        ArrayAdapter<String> userTypeAdapter =
                new ArrayAdapter<>(this, R.layout.spinner_item_black, userTypes);
        userTypeAdapter.setDropDownViewResource(R.layout.spinner_item_black);
        spinnerUserType.setAdapter(userTypeAdapter);

        selectedUserType = null;
    }

    @Nullable
    private String getSelectedUserTypeFromPosition(int position) {
        if (position <= 0) return null;

        String value = (String) spinnerUserType.getItemAtPosition(position);
        if (value == null) return null;

        value = value.trim();
        if (value.isEmpty() || value.startsWith("Select ")) return null;

        return value;
    }

    private void tryLoadDrivers() {
        if (selectedEvent == null || selectedEvent.id <= 0) {
            setDriverPlaceholder("Select Event First");
            clearDriverDirectoryCache();
            return;
        }

        if (!isValidUserType(selectedUserType)) {
            setDriverPlaceholder("Select User Type First");
            clearDriverDirectoryCache();
            return;
        }

        if (isVideoReviewer(selectedUserType)) {
            setDriverPlaceholder("User Not Required");
            clearDriverDirectoryCache();
            return;
        }

        loadDriversFromApi(selectedEvent.id, selectedUserType);
    }

    // ===================== DRIVERS =====================

    private void initDriverSpinner() {
        drivers.clear();
        drivers.add(new DriverItem(-1, null, "Select Event First"));

        ArrayList<String> names = new ArrayList<>();
        names.add("Select Event First");

        driverNamesAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_black, names);
        driverNamesAdapter.setDropDownViewResource(R.layout.spinner_item_black);
        spinnerDriver.setAdapter(driverNamesAdapter);
    }

    private void setDriverPlaceholder(String text) {
        drivers.clear();
        drivers.add(new DriverItem(-1, null, text));

        ArrayList<String> names = new ArrayList<>();
        names.add(text);

        driverNamesAdapter.clear();
        driverNamesAdapter.addAll(names);
        driverNamesAdapter.notifyDataSetChanged();

        spinnerDriver.setSelection(0);
        updateDriverInputState();
        updateSubmitEnabled();
    }

    private void clearDriverDirectoryCache() {
        try {
            DriverDirectory.replaceAllFromDto(MainActivity.this, new ArrayList<>());
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear DriverDirectory cache", e);
        }
    }

    private void loadDriversFromApi(int eventId, @NonNull String userType) {
        if (eventId <= 0) {
            setDriverPlaceholder("Select Event First");
            clearDriverDirectoryCache();
            return;
        }

        if (!isValidUserType(userType)) {
            setDriverPlaceholder("Select User Type First");
            clearDriverDirectoryCache();
            return;
        }

        setDriverPlaceholder("Loading users…");

        final String requestBaseUrl = ApiClient.getCurrentBaseUrl();
        LookupService svc = ApiClient.getMemberApi().create(LookupService.class);

        svc.getMechanicDrivers(userType, eventId).enqueue(new Callback<List<MechanicDriverDto>>() {
            @Override
            public void onResponse(Call<List<MechanicDriverDto>> call,
                                   Response<List<MechanicDriverDto>> response) {
                if (!requestBaseUrl.equals(ApiClient.getCurrentBaseUrl())) return;

                if (response.code() == 404) {
                    setDriverPlaceholder("No Users Found");
                    clearDriverDirectoryCache();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    setDriverPlaceholder("Select User…");
                    clearDriverDirectoryCache();
                    Toast.makeText(MainActivity.this,
                            "Failed to load users",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                List<MechanicDriverDto> fetched = response.body();
                if (fetched == null) fetched = new ArrayList<>();

                try {
                    DriverDirectory.replaceAllFromDto(MainActivity.this, fetched);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to cache users in DriverDirectory", e);
                }

                List<DriverItem> mapped = new ArrayList<>();
                for (MechanicDriverDto d : fetched) {
                    if (d == null) continue;

                    Integer num = d.driverNumber;
                    String contactId = (d.contactId == null) ? null : d.contactId.trim();

                    String first = (d.firstName == null) ? "" : d.firstName.trim();
                    String last = (d.lastName == null) ? "" : d.lastName.trim();
                    String display = (first + " " + last).trim();

                    if (display.isEmpty()) continue;

                    boolean hasDriverNumber = (num != null && num > 0);
                    boolean hasContactId = (contactId != null && !contactId.isEmpty());

                    if (!hasDriverNumber && !hasContactId) continue;

                    mapped.add(new DriverItem(num, contactId, display));
                }

                mapped.sort((a, b) -> {
                    int cmp = a.name.compareToIgnoreCase(b.name);
                    if (cmp != 0) return cmp;

                    int aNum = (a.number != null) ? a.number : 0;
                    int bNum = (b.number != null) ? b.number : 0;
                    return Integer.compare(aNum, bNum);
                });

                drivers.clear();
                if (mapped.isEmpty()) {
                    drivers.add(new DriverItem(-1, null, "No Users Found"));
                } else {
                    drivers.add(new DriverItem(-1, null, "Select User…"));
                    drivers.addAll(mapped);
                }

                ArrayList<String> names = new ArrayList<>(drivers.size());
                for (DriverItem di : drivers) names.add(di.name);

                driverNamesAdapter.clear();
                driverNamesAdapter.addAll(names);
                driverNamesAdapter.notifyDataSetChanged();

                spinnerDriver.setSelection(0);
                updateSubmitEnabled();

                Toast.makeText(MainActivity.this,
                        "Loaded " + mapped.size() + " users",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<List<MechanicDriverDto>> call, Throwable t) {
                if (!requestBaseUrl.equals(ApiClient.getCurrentBaseUrl())) return;
                setDriverPlaceholder("Select User…");
                clearDriverDirectoryCache();
                Toast.makeText(MainActivity.this,
                        "Network error loading users.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===================== JSON HELPERS =====================

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
        return s.length() > 1
                ? Character.toUpperCase(s.charAt(0)) + s.substring(1)
                : s.toUpperCase();
    }

    private int dp(int dps) {
        return Math.round(getResources().getDisplayMetrics().density * dps);
    }

    // ===================== MODELS =====================

    private static class DriverItem {
        @Nullable
        final Integer number;

        @Nullable
        final String contactId;

        final String name;

        DriverItem(@Nullable Integer number, @Nullable String contactId, String name) {
            this.number = number;
            this.contactId = contactId;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class EventItem {
        final int id;
        final String name;
        final long sortTimeMs;

        EventItem(int id, String name, long sortTimeMs) {
            this.id = id;
            this.name = name;
            this.sortTimeMs = sortTimeMs;
        }

        @Override
        public String toString() {
            return name;
        }
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
                h.btn.setBackgroundTintList(
                        ContextCompat.getColorStateList(h.btn.getContext(), android.R.color.white)
                );
                h.btn.setTextColor(
                        ContextCompat.getColor(h.btn.getContext(), R.color.redButton)
                );
                h.btn.setAlpha(1f);
            } else {
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

                tryLoadDrivers();
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
            return;
        }

        startGT6SyncService();

        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.cancelUniqueWork("gt6_scan_serial");
        wm.cancelUniqueWork("gt6_content_watch");
        wm.cancelAllWorkByTag("gt6_scan_now");
        wm.cancelAllWorkByTag("gt6_content_triggered");
        wm.pruneWork();

        maybeStartInitialSync(getApplicationContext());
        dumpWM("POST-ENQUEUE");
    }

    private void startGT6SyncService() {
        Intent svc = new Intent(this, com.example.gt6driver.sync.GT6MediaSyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Log.i(TAG, "Main: started GT6MediaSyncService.");
    }

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
                } catch (Exception ignored) {
                }

                try {
                    for (WorkInfo wi : f2.get()) {
                        Log.i(TAG, label + " WM gt6_scan_periodic → " +
                                wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                    }
                } catch (Exception ignored) {
                }

                try {
                    for (WorkInfo wi : f3.get()) {
                        Log.i(TAG, label + " WM tag:gt6_content_triggered → " +
                                wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "WM dump failed", e);
        }
    }
}
