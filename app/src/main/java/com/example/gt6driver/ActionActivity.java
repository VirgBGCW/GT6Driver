package com.example.gt6driver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.data.DriverDirectory;
import com.example.gt6driver.model.ConsignmentKeyPayload;
import com.example.gt6driver.model.EventVehicleStatusPayload;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.DriverTaskApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Vibrate helpers
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * ActionActivity — NFC REMOVED.
 *
 * Matches updated activity_action.xml:
 *  - VIN label removed: panelVinValue only
 *  - Right-of-image stack: panelLot (top), panelLocationName, panelRowCol
 *  - Added TRANSPORT button (btnTransport)
 */
public class ActionActivity extends AppCompatActivity {

    // Printer/device constants
    private static final String[] PRINTER_CANDIDATES = new String[]{
            "SPP-R310", "SPP-310", "SPP-R300", "SPP-300"
    };
    private static final int RECEIPT_WIDTH_DOTS = 576; // 80mm; 384 for 58mm

    // ESC/POS control bytes
    private static final byte ESC = 0x1B;   // ESC
    private static final byte GS = 0x1D;    // GS

    // Logging
    private static final String LOG_TAG = "GT6Driver";
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";

    // ===== Permissions =====
    private static final String[] BT_PERMS_S_AND_UP = new String[]{
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };
    private static final String[] BT_PERMS_Q_R = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // UI
    private TextView panelLot, panelDesc;
    private TextView panelVinValue;         // VIN number only
    private TextView panelLocationName;     // right aligned
    private TextView panelRowCol;           // right aligned
    private ImageView panelImage;

    // Buttons
    private MaterialButton btnTransport;
    private MaterialButton btnCheckIn, btnCheckOut;
    private MaterialButton btnKeyBagTags, btnCarTag, btnAssignDriver, btnDriverCheckIn;

    // Context/state
    private int eventId = -1;
    private String eventName = "";
    private String driver = "";

    // Dynamic CRM Opportunity Id (replaces hard-coded constant)
    private String crmId = "";   // set from Intent (vehicle search result)

    // Vehicle (source of truth)
    private VehicleDetail vehicle;

    // For legacy fallback if no parcelable was provided
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    // Background thread for printing
    private final ExecutorService printExec = Executors.newSingleThreadExecutor();

    // Modern permission launcher
    private ActivityResultLauncher<String[]> btPermsLauncher;

    // Working fields for the dialog
    private String selectedDriverNumber = null; // typed by user
    /* package */ String selectedDriverName = null;   // resolved using DriverDirectory

    // Monospace columns for ESC/POS alignment
    private static final int COLS_NORMAL = 48; // Font A, 80mm paper (576 dots)
    private static final int COLS_W2 = COLS_NORMAL / 2; // ~24 cols when width is 2×

    private String fixedWidthLine(String left, String right, int totalCols) {
        if (left == null) left = "";
        if (right == null) right = "";
        if (left.length() > totalCols) left = left.substring(0, totalCols);
        if (right.length() > totalCols) right = right.substring(0, totalCols);
        int space = totalCols - left.length() - right.length();
        if (space < 1) space = 1;
        StringBuilder sb = new StringBuilder(totalCols);
        sb.append(left);
        for (int i = 0; i < space; i++) sb.append(' ');
        sb.append(right);
        return sb.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action);

        // --- register permission launcher ---
        btPermsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean g : result.values()) granted &= (g != null && g);
                    if (!granted) {
                        Toast.makeText(this, "Bluetooth permission required to print.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        bindViews();
        readExtras(getIntent());

        // Coalesce from: explicit extra → legacy extra → vehicle fields
        crmId = coalesce(
                safe(getIntent().getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(getIntent().getStringExtra("crmId")),
                (vehicle != null ? safe(vehicle.opportunityId) : ""),
                (vehicle != null ? safe(vehicle.crmopportunityid) : "")
        );

        Log.d(LOG_TAG, "Resolved crmId/opportunityId = " + crmId);

        populateHeader();
        wireButtons();
    }

    private void bindViews() {
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);

        // VIN number only (no label in XML)
        panelVinValue = findViewById(R.id.panelVinValue);

        // Parking info (updated XML)
        panelLocationName = findViewById(R.id.panelLocationName);
        panelRowCol = findViewById(R.id.panelRowCol);

        panelImage = findViewById(R.id.panelImage);

        // Big buttons
        btnTransport = findViewById(R.id.btnTransport);
        btnCheckIn = findViewById(R.id.btnCheckIn);
        btnCheckOut = findViewById(R.id.btnCheckOut);

        // Tool row
        btnKeyBagTags = findViewById(R.id.btnKeyBagTags);
        btnCarTag = findViewById(R.id.btnCarTag);
        btnAssignDriver = findViewById(R.id.btnAssignDriver);
        btnDriverCheckIn = findViewById(R.id.btnDriverCheckIn);
    }

    private void readExtras(@NonNull Intent intent) {
        vehicle = intent.getParcelableExtra(Nav.EXTRA_VEHICLE);
        eventId = intent.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        eventName = safe(intent.getStringExtra(Nav.EXTRA_EVENT_NAME));
        driver = safe(intent.getStringExtra(Nav.EXTRA_DRIVER));

        lotLegacy = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));
    }

    private void populateHeader() {
        String lotStr = (vehicle != null && vehicle.lotnumber != null)
                ? String.valueOf(vehicle.lotnumber) : lotLegacy;

        String title = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vinStr   = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

        panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        panelDesc.setText(title);

        // VIN number only
        panelVinValue.setText(vinStr);

        // Location name
        String locationName = (vehicle != null) ? safe(vehicle.tentid) : "";
        if (panelLocationName != null) {
            panelLocationName.setText(defaulted(locationName, ""));
        }

        // Row/Col
        String row = (vehicle != null) ? safe(vehicle.row) : "";
        String col = (vehicle != null) ? safe(vehicle.col) : "";
        if (panelRowCol != null) {
            String rowColText = "";
            if (!row.isEmpty() || !col.isEmpty()) {
                rowColText = defaulted(col, "—") + " - " + defaulted(row, "—");
            }
            panelRowCol.setText(rowColText);
        }

        float density = getResources().getDisplayMetrics().density;
        int radiusPx = (int) (12 * density);

        if (!thumbUrl.isEmpty()) {
            Glide.with(this)
                    .load(thumbUrl)
                    .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .into(panelImage);
        } else {
            panelImage.setImageResource(android.R.color.darker_gray);
        }
    }

    private void wireButtons() {
        // TRANSPORT
        if (btnTransport != null) {
            btnTransport.setOnClickListener(v -> {
                Intent i = new Intent(this, TransportActivity.class);
                putCommonExtras(i);
                startActivity(i);
            });
        }

        btnCheckIn.setOnClickListener(v -> {
            Intent i = new Intent(this, CheckInDetailsActivity.class);
            putCommonExtras(i);
            startActivity(i);
        });

        btnCheckOut.setOnClickListener(v -> {
            Intent i = new Intent(this, CheckOutDetailsActivity.class);
            putCommonExtras(i);
            startActivity(i);
        });

        btnDriverCheckIn.setOnClickListener(v -> postDriverCheckInHardcoded());

        btnAssignDriver.setOnClickListener(v -> showAssignDriverDialog());

        btnKeyBagTags.setOnClickListener(v -> {
            String lot = currentLot();
            String year = (vehicle != null && vehicle.year != null) ? String.valueOf(vehicle.year) : "";
            String make = (vehicle != null) ? safe(vehicle.make) : "";
            String model = (vehicle != null) ? safe(vehicle.model) : "";
            String color = (vehicle != null) ? safe(vehicle.exteriorcolor) : "";
            String vin = currentVin();

            String tent = (vehicle != null) ? safe(vehicle.tentid) : "";
            String col = (vehicle != null) ? safe(vehicle.col) : "";
            String row = (vehicle != null) ? safe(vehicle.row) : "";

            // LOCATION = TENTID + " / " + COL + " - " + ROW
            String location = (tent + " / " + col + " - " + row).trim();

            printVehicleInfoEscPos(
                    defaulted(lot, "—"),
                    defaulted(year, "—"),
                    defaulted(make, "—"),
                    defaulted(model, "—"),
                    defaulted(color, "—"),
                    defaulted(vin, "—"),
                    defaulted(location, "—"),
                    "" /* ignored */
            );
        });

        btnCarTag.setOnClickListener(v -> {
            String lot = currentLot();
            String tent = (vehicle != null) ? safe(vehicle.tentid) : "";
            String col = (vehicle != null) ? safe(vehicle.col) : "";
            String row = (vehicle != null) ? safe(vehicle.row) : "";
            String time = (vehicle != null) ? safe(vehicle.targetTimeText) : "";

            printCarTag(lot, tent, col, row, time);
        });
    }

    /**
     * Pick the best available printer name. Prefers a bonded device whose name contains one
     * of our candidates (case-insensitive). Falls back to the first candidate if none found.
     */
    private String resolvePrinterName() {
        try {
            android.bluetooth.BluetoothManager bm =
                    (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
            if (adapter != null && adapter.isEnabled()) {
                java.util.Set<android.bluetooth.BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded != null) {
                    for (String cand : PRINTER_CANDIDATES) {
                        for (android.bluetooth.BluetoothDevice d : bonded) {
                            String n = (d.getName() == null) ? "" : d.getName();
                            if (n.toLowerCase(Locale.US).contains(cand.toLowerCase(Locale.US))) {
                                Log.d(LOG_TAG, "Resolved bonded printer match: " + n + " (cand=" + cand + ")");
                                return n;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(LOG_TAG, "resolvePrinterName() failed; using default", t);
        }
        return PRINTER_CANDIDATES[0];
    }

    private void putCommonExtras(Intent i) {
        if (vehicle != null) {
            i.putExtra(Nav.EXTRA_VEHICLE, vehicle);
        } else {
            i.putExtra(Nav.EXTRA_LOT, lotLegacy);
            i.putExtra(Nav.EXTRA_DESC, descLegacy);
            i.putExtra(Nav.EXTRA_VIN, vinLegacy);
            i.putExtra(Nav.EXTRA_THUMB, thumbLegacy);
        }
        i.putExtra(Nav.EXTRA_EVENT_ID, eventId);
        i.putExtra(Nav.EXTRA_EVENT_NAME, eventName);
        i.putExtra(Nav.EXTRA_DRIVER, driver);

        String opp = resolveOpportunityId();
        Log.d(LOG_TAG, "putCommonExtras() passing opportunityId=" + opp);
        if (!opp.isEmpty()) {
            i.putExtra(Nav.EXTRA_OPPORTUNITY_ID, opp);
            i.putExtra("opportunityId", opp);
            i.putExtra("crmId", opp);
        }
    }

    private String resolveOpportunityId() {
        String id = safe(crmId);
        if (!id.isEmpty()) return id;

        if (vehicle != null) {
            String vOpp = safe(vehicle.opportunityId);
            if (!vOpp.isEmpty()) return vOpp;
            String vCrm = safe(vehicle.crmopportunityid);
            if (!vCrm.isEmpty()) return vCrm;
        }

        return coalesce(
                safe(getIntent().getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(getIntent().getStringExtra("opportunityId")),
                safe(getIntent().getStringExtra("crmId"))
        );
    }

    private String currentLot() {
        if (vehicle != null && vehicle.lotnumber != null) return String.valueOf(vehicle.lotnumber);
        return lotLegacy;
    }

    private String currentVin() {
        if (vehicle != null) return safe(vehicle.vin);
        return vinLegacy;
    }

    private String defaulted(String v, String d) {
        return (v != null && !v.trim().isEmpty()) ? v : d;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String coalesce(String... vals) {
        for (String s : vals) if (s != null && !s.trim().isEmpty()) return s;
        return "";
    }

    // ===== Bluetooth permission helpers =====
    private boolean ensureBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasConnect || !hasScan) {
                btPermsLauncher.launch(BT_PERMS_S_AND_UP);
                return false;
            }
            return ensureBluetoothEnabled();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10–11
            boolean hasLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasLoc) {
                btPermsLauncher.launch(BT_PERMS_Q_R);
                return false;
            }
            return ensureBluetoothEnabled();
        } else {
            return ensureBluetoothEnabled();
        }
    }

    private boolean ensureBluetoothEnabled() {
        android.bluetooth.BluetoothManager bm =
                (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        android.bluetooth.BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not available on this device.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!adapter.isEnabled()) {
            startActivity(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        printExec.shutdownNow();
    }

    // ===== ESC/POS helpers =====
    private static byte[] cmdInit() { return new byte[]{ESC, '@'}; }
    private static byte[] cmdAlign(int n) { return new byte[]{ESC, 'a', (byte) n}; }
    private static byte[] cmdCharSize(int w, int h) {
        return new byte[]{GS, '!', (byte) (((h & 0x7) << 4) | (w & 0x7))};
    }
    private static byte[] cmdFontA() { return new byte[]{ESC, 'M', 0x00}; }
    private static byte[] cmdPrintMode(int n) { return new byte[]{ESC, '!', (byte) n}; }
    private static byte[] cmdEmphasized(boolean on) { return new byte[]{ESC, 'E', (byte) (on ? 1 : 0)}; }
    private static byte[] cmdDoubleStrike(boolean on) { return new byte[]{ESC, 'G', (byte) (on ? 1 : 0)}; }

    private String escSeq(byte[] cmd) {
        try { return new String(cmd, StandardCharsets.ISO_8859_1); }
        catch (Exception e) { return new String(cmd); }
    }

    // ===== Print: Vehicle Info (ESC/POS text) =====
    private void printVehicleInfoEscPos(String lot, String year, String make, String model,
                                        String color, String vin, String tent, String descIgnored) {
        if (!ensureBtPermissions()) {
            Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
            return;
        }

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(resolvePrinterName());

                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "LOT      : " + defaulted(lot, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "YEAR     : " + defaulted(year, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "MAKE     : " + defaulted(make, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "MODEL    : " + defaulted(model, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "COLOR    : " + defaulted(color, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "VIN      : " + defaulted(vin, "—") + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "LOCATION : " + defaulted(tent, "—"));

                esc.printText(escSeq(cmdCharSize(0, 0)));
                esc.printText("\n\n");

                // After successful print, POST EventVehicleStatus (eventVehicleStatus = 2)
                ApiResult res = postEventVehicleStatusSync(this.eventId, lot);
                if (res.ok) {
                    runOnUiThread(() -> showBigResultBanner(true, "Vehicle On-Site"));
                } else {
                    final String msg = "Vehicle status update FAILED (code=" + res.code + "): " + (res.message == null ? "" : res.message);
                    Log.w(HTTP_LOG_TAG, msg);
                    runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
                }

                runOnUiThread(() ->
                        Toast.makeText(this, "Vehicle Info printed", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                esc.close();
            }
        });
    }

    // ===== Print: Car Tag (ESC/POS double-size rows + QR) =====
    private void printCarTag(String lotNum, String tentId, String col, String row, String targetTimeText) {
        if (!ensureBtPermissions()) {
            Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String lot = defaulted(lotNum, "—");
        final String tent = defaulted(tentId, "—");
        final String colRow = defaulted(col, "—") + "-" + defaulted(row, "—");
        final String day = defaulted(formatDayParenUpper(targetTimeText), "");
        final String qrData = (vehicle != null) ? safe(vehicle.qrurl) : "";

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(resolvePrinterName());

                StringBuilder job = new StringBuilder(512);
                job.append(escSeq(cmdInit()))
                        .append(escSeq(cmdFontA()))
                        .append(escSeq(cmdAlign(0)))
                        .append(escSeq(cmdCharSize(0, 0)))
                        .append(escSeq(cmdPrintMode(0x30)))   // double width + height
                        .append(escSeq(cmdEmphasized(true)))
                        .append(escSeq(cmdDoubleStrike(true)));

                // ROW 1: LOT | TENT
                String r1 = fixedWidthLine(
                        fitForCols(lot, COLS_W2),
                        fitForCols(tent, COLS_W2),
                        COLS_W2
                );
                job.append(r1).append("\n");

                // ROW 2: (FRIDAY) | COL-ROW
                String r2 = fixedWidthLine(
                        fitForCols(day, COLS_W2),
                        fitForCols(colRow, COLS_W2),
                        COLS_W2
                );
                job.append(r2);

                esc.printText(job.toString());

                // Reset before QR
                esc.printText(
                        escSeq(cmdEmphasized(false)) +
                                escSeq(cmdDoubleStrike(false)) +
                                escSeq(cmdPrintMode(0x00)) +
                                escSeq(cmdCharSize(0, 0))
                );

                esc.printText("\n\n");

                if (!qrData.isEmpty()) {
                    String qrSeq = escposQr(qrData, 16, 51, 2);
                    esc.printText(qrSeq);
                }

                esc.printText("\n\n");

                runOnUiThread(() ->
                        Toast.makeText(this, "Car tag printed", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Car tag print failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                esc.close();
            }
        });
    }

    // Native ESC/POS QR (Model 2). moduleSize: 1..16, ecLevel: 48='L',49='M',50='Q',51='H', align: 0/1/2
    private String escposQr(String data, int moduleSize, int ecLevel, int align) {
        if (data == null) data = "";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{ESC, 'a', (byte) align});

            out.write(new byte[]{GS, '(', 'k', 0x04, 0x00, 0x31, 0x41, 0x32, 0x00}); // model 2
            out.write(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x43, (byte) Math.min(16, Math.max(1, moduleSize))}); // module size
            out.write(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x45, (byte) ecLevel}); // EC level

            byte[] payload = data.getBytes(StandardCharsets.ISO_8859_1);
            int len = payload.length + 3;
            byte pL = (byte) (len & 0xFF);
            byte pH = (byte) ((len >> 8) & 0xFF);
            out.write(new byte[]{GS, '(', 'k', pL, pH, 0x31, 0x50, 0x30}); // store
            out.write(payload);

            out.write(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x51, 0x30}); // print
            return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatDayParenUpper(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (m1.find()) {
            String d = m1.group(1).toLowerCase(Locale.US);
            String full = d.startsWith("mon") ? "MONDAY" :
                    d.startsWith("tue") ? "TUESDAY" :
                            d.startsWith("wed") ? "WEDNESDAY" :
                                    d.startsWith("thu") ? "THURSDAY" :
                                            d.startsWith("fri") ? "FRIDAY" :
                                                    d.startsWith("sat") ? "SATURDAY" : "SUNDAY";
            return "(" + full + ")";
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (m2.find()) return "(" + m2.group(1).toUpperCase(Locale.US) + ")";
        return "(" + s.toUpperCase(Locale.US) + ")";
    }

    private String fitForCols(String s, int cols) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= cols) return s;
        return s.substring(0, cols);
    }

    // ======= API call for Driver Check-In =======
    private void postDriverCheckInHardcoded() {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postDriverCheckInHardcoded() using opportunityId=" + opportunityId);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot check in.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postDriverCheckInHardcoded: missing opportunityId");
            showFullScreenResult(false, "FAILED");
            return;
        }

        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "CheckedIn",
                "BarrettJackson",
                "Barrett-Jackson",
                "Remote Check-In"
        );

        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            Log.i(HTTP_LOG_TAG, "Body: " + new com.google.gson.Gson().toJson(payload));
        } catch (Throwable t) {
            Log.w(HTTP_LOG_TAG, "Logging failed", t);
        }

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(HTTP_LOG_TAG, "DriverCheckIn response code=" + response.code());
                if (response.isSuccessful()) {
                    showFullScreenResult(true, "Moved to Barrett-Jackson");
                } else {
                    int code = response.code();
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {}
                    Log.w(HTTP_LOG_TAG, "DriverCheckIn failed. code=" + code + ", body=" + errBody);

                    if (code == 404) {
                        showFullScreenResult(false, "KEY RECORD NOT FOUND");
                    } else {
                        showFullScreenResult(false, "FAILED");
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "DriverCheckIn network error", t);
                showFullScreenResult(false, "FAILED");
            }
        });
    }

    private void postConsignmentKeyUpdate(@NonNull String reason) {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postConsignmentKeyUpdate() using opportunityId=" + opportunityId + ", reason=" + reason);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing opportunityId");
            showFullScreenResult(false, "FAILED");
            return;
        }
        if (selectedDriverName == null || selectedDriverName.trim().isEmpty()) {
            Toast.makeText(this, "Missing driver name; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing driver name");
            showFullScreenResult(false, "FAILED");
            return;
        }

        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "Arrived",
                "Driver",
                selectedDriverName,
                reason
        );

        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            String json = new com.google.gson.Gson().toJson(payload);
            Log.i(HTTP_LOG_TAG, "Body: " + json);
        } catch (Throwable t) {
            Log.w(HTTP_LOG_TAG, "Logging failed", t);
        }

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(HTTP_LOG_TAG, "Response code=" + response.code());
                if (response.isSuccessful()) {
                    showFullScreenResult(true, "Assigned to " + selectedDriverName);
                } else {
                    int code = response.code();
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {}
                    Log.w(HTTP_LOG_TAG, "Consignment update failed. code=" + code + ", body=" + errBody);

                    if (code == 404) {
                        showFullScreenResult(false, "KEY RECORD NOT FOUND");
                    } else {
                        showFullScreenResult(false, "FAILED");
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Network error updating consignment", t);
                showFullScreenResult(false, "FAILED");
            }
        });
    }

    // ===== EventVehicleStatus (sync) =====
    private static class ApiResult {
        final boolean ok;
        final int code;
        final String message;

        ApiResult(boolean ok, int code, String message) {
            this.ok = ok;
            this.code = code;
            this.message = message;
        }
    }

    private ApiResult postEventVehicleStatusSync(int eventId, String lotNumber) {
        if (eventId <= 0) {
            String msg = "invalid eventId=" + eventId;
            Log.w(HTTP_LOG_TAG, "EventVehicleStatus NOT posted: " + msg);
            return new ApiResult(false, 0, msg);
        }
        if (lotNumber == null || lotNumber.trim().isEmpty() || "—".equals(lotNumber.trim())) {
            String msg = "missing/invalid lotNumber=" + lotNumber;
            Log.w(HTTP_LOG_TAG, "EventVehicleStatus NOT posted: " + msg);
            return new ApiResult(false, 0, msg);
        }

        try {
            DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);

            EventVehicleStatusPayload payload =
                    new EventVehicleStatusPayload(eventId, lotNumber.trim(), 2);

            Call<Void> call = api.updateEventVehicleStatus(payload);

            try {
                Log.i(HTTP_LOG_TAG, "POST " + call.request().url());
                Log.i(HTTP_LOG_TAG, "Body: " + new com.google.gson.Gson().toJson(payload));
            } catch (Throwable t) {
                Log.w(HTTP_LOG_TAG, "Logging failed", t);
            }

            Response<Void> resp = call.execute();
            int code = resp.code();
            Log.i(HTTP_LOG_TAG, "EventVehicleStatus response code=" + code);

            if (!resp.isSuccessful()) {
                String errBody = null;
                try {
                    errBody = resp.errorBody() != null ? resp.errorBody().string() : null;
                } catch (Exception ignored) {}
                Log.w(HTTP_LOG_TAG, "EventVehicleStatus failed. code=" + code + ", body=" + errBody);
                return new ApiResult(false, code, (errBody != null && !errBody.isEmpty()) ? errBody : "HTTP " + code);
            }

            return new ApiResult(true, code, "OK");
        } catch (Exception e) {
            Log.e(HTTP_LOG_TAG, "EventVehicleStatus exception", e);
            return new ApiResult(false, -1, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ===== UI feedback =====
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showBigResultBanner(boolean success, String text) {
        runOnUiThread(() -> {
            ViewGroup decor = (ViewGroup) getWindow().getDecorView();
            final FrameLayout container = new FrameLayout(this);
            container.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            container.setClickable(false);

            final TextView tv = new TextView(this);
            tv.setText(text == null ? "" : text);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tv.setGravity(Gravity.CENTER);

            final int bg = success ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828");
            tv.setBackgroundColor(bg);

            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
            tv.setElevation(dp(8));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.TOP;

            container.addView(tv, lp);
            decor.addView(container);

            tv.setAlpha(0f);
            tv.animate().alpha(1f).setDuration(180).start();

            tv.postDelayed(() -> {
                tv.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    try { decor.removeView(container); } catch (Exception ignored) {}
                }).start();
            }, 1800);
        });
    }

    private void showFullScreenResult(boolean success, String text) {
        runOnUiThread(() -> {
            final ViewGroup decor = (ViewGroup) getWindow().getDecorView();

            final FrameLayout overlay = new FrameLayout(this);
            overlay.setClickable(true);
            overlay.setFocusable(true);
            overlay.setFocusableInTouchMode(true);

            final int bg = success ? Color.parseColor("#1B5E20") : Color.parseColor("#B71C1C");
            overlay.setBackgroundColor(bg);

            final TextView tv = new TextView(this);
            tv.setText(text == null ? "" : text);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(24), dp(24), dp(24), dp(24));
            tv.setAllCaps(true);

            FrameLayout.LayoutParams lpMsg = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            lpMsg.gravity = Gravity.CENTER;
            overlay.addView(tv, lpMsg);

            decor.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200).start();

            try {
                if (success) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                        if (vm != null) {
                            vm.getDefaultVibrator().vibrate(
                                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                            );
                        }
                    } else {
                        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                        if (v != null && v.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                v.vibrate(400);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "Vibration failed: " + e);
            }

            View.OnClickListener dismiss = v -> {
                overlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    try { decor.removeView(overlay); } catch (Exception ignored) {}
                }).start();
            };
            overlay.setOnClickListener(dismiss);
            overlay.postDelayed(() -> {
                if (overlay.getParent() != null) dismiss.onClick(overlay);
            }, 2200);
        });
    }

    // ===== Assign Driver dialog (NO NFC) =====
    private void showAssignDriverDialog() {
        try {
            DriverDirectory.initFromCache(this);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to initialize DriverDirectory from cache", e);
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_assign_driver, null, false);

        final TextInputLayout tilDriver = view.findViewById(R.id.tilDriver);
        final TextInputEditText etDriver = view.findViewById(R.id.etDriver);

        // NOTE: this is just a button ID from your layout. It is NOT NFC.
        final MaterialButton btnDriver = view.findViewById(R.id.btnDriverNfc);
        final MaterialButton btnCancel = view.findViewById(R.id.btnDriverCancel);

        final MaterialButton[] pig = new MaterialButton[1];
        pig[0] = view.findViewById(R.id.btnDriverPigPen);

        // Ensure PIGPEN exists
        if (pig[0] == null) {
            MaterialButton created = new MaterialButton(this);
            created.setId(View.generateViewId());
            created.setText("PIGPEN");
            created.setTextColor(getResources().getColor(android.R.color.white));
            created.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#424242")));
            created.setCornerRadius((int) (12 * getResources().getDisplayMetrics().density));

            ViewGroup row = (ViewGroup) btnDriver.getParent();
            if (row != null) {
                int insertIndex = row.indexOfChild(btnCancel);
                if (insertIndex < 0) insertIndex = row.getChildCount();
                row.addView(created, insertIndex,
                        new ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            pig[0] = created;
        }

        // Big driver display line
        final TextView tvDriverDisplay = new TextView(this);
        tvDriverDisplay.setText("");
        tvDriverDisplay.setTextColor(Color.WHITE);
        tvDriverDisplay.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        tvDriverDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvDriverDisplay.setPadding(0, dp(10), 0, 0);
        tvDriverDisplay.setVisibility(View.GONE);

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            int tilIndex = root.indexOfChild(tilDriver);
            if (tilIndex >= 0) {
                root.addView(tvDriverDisplay, tilIndex + 1,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                root.addView(tvDriverDisplay,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        tilDriver.setHelperText(null);

        final AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        dlg.show();

        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        // Button labels
        btnDriver.setText("DRIVER");
        btnCancel.setText("CANCEL");
        pig[0].setText("PIGPEN");

        // Initial disabled state
        btnDriver.setEnabled(false);
        pig[0].setEnabled(false);
        tilDriver.setError(null);

        selectedDriverNumber = null;
        selectedDriverName = null;

        etDriver.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedDriverNumber = (s == null) ? null : s.toString().trim();
                selectedDriverName = null;

                tilDriver.setError(null);
                boolean ok = false;

                tvDriverDisplay.setVisibility(View.GONE);
                tvDriverDisplay.setText("");

                if (s != null && s.length() > 0 && android.text.TextUtils.isDigitsOnly(s)) {
                    try {
                        int num = Integer.parseInt(selectedDriverNumber);
                        String name = DriverDirectory.nameFor(num);

                        if (name != null && !name.isEmpty()) {
                            selectedDriverName = name;
                            ok = true;

                            tvDriverDisplay.setText("Driver: " + name);
                            tvDriverDisplay.setTextColor(Color.parseColor("#2E7D32"));
                            tvDriverDisplay.setVisibility(View.VISIBLE);
                        } else {
                            tvDriverDisplay.setText("Unknown driver number");
                            tvDriverDisplay.setTextColor(Color.parseColor("#C62828"));
                            tvDriverDisplay.setVisibility(View.VISIBLE);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                btnDriver.setEnabled(ok);
                pig[0].setEnabled(ok);
            }
        });

        etDriver.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && btnDriver.isEnabled()) {
                btnDriver.performClick();
                return true;
            }
            return false;
        });

        btnCancel.setOnClickListener(v -> dlg.dismiss());

        btnDriver.setOnClickListener(v -> {
            if (selectedDriverNumber == null || selectedDriverName == null) {
                tilDriver.setError(getString(R.string.driver_number_error));
                return;
            }
            dlg.dismiss();
            postConsignmentKeyUpdate("Moving to Driver");
        });

        pig[0].setOnClickListener(v -> {
            if (selectedDriverNumber == null || selectedDriverName == null) {
                tilDriver.setError(getString(R.string.driver_number_error));
                return;
            }
            dlg.dismiss();
            postConsignmentKeyUpdate("Moving to PIGPEN");
        });
    }
}










































