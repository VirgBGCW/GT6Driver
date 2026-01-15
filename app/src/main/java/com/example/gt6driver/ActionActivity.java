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
import java.util.Locale;
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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
 * ActionActivity — NFC removed. Assign dialog posts directly to API.
 * Updated to bind new XML parking fields:
 *  - panelLocationName (right side of VIN label row)
 *  - panelRowCol (right side of VIN value row)
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

    // VIN label + VIN value (under image)
    private TextView panelVinLabel, panelVinValue;

    // NEW (from updated XML)
    private TextView panelLocationName; // <-- FIXED: declare
    private TextView panelRowCol;

    private ImageView panelImage;
    private MaterialButton btnCheckIn, btnCheckOut, btnKeyBagTags, btnCarTag, btnAssignDriver, btnDriverCheckIn;

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

    // QR URL for car tag (optional)
    private String qrUrl = "";

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

        // In your XML you have this label. Bind it so we can line up location name row.
        panelVinLabel = findViewById(R.id.panelVinLabel);
        panelVinValue = findViewById(R.id.panelVinValue);

        // NEW (parking info) - FIXED: bind panelLocationName too
        panelLocationName = findViewById(R.id.panelLocationName);
        panelRowCol = findViewById(R.id.panelRowCol);

        panelImage = findViewById(R.id.panelImage);

        btnCheckIn = findViewById(R.id.btnCheckIn);
        btnCheckOut = findViewById(R.id.btnCheckOut);
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

        // QR URL (support several extra names)
        qrUrl = coalesce(
                safe(intent.getStringExtra("qrurl")),
                safe(intent.getStringExtra("qrUrl")),
                safe(intent.getStringExtra("QR_URL"))
        );
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
        panelVinValue.setText(vinStr);

        // ===== Parking info (matches your XML) =====
        // Location name on the RIGHT side of the VIN LABEL row
        String locationName = (vehicle != null) ? safe(vehicle.tentid) : "";
        if (panelLocationName != null) {
            panelLocationName.setText(defaulted(locationName, ""));
        }

        // ROW-COL on the RIGHT side of the VIN VALUE row (example: "6 - WW")
        String row = (vehicle != null) ? safe(vehicle.row) : "";
        String col = (vehicle != null) ? safe(vehicle.col) : "";
        if (panelRowCol != null) {
            String rowColText = "";
            if (!row.isEmpty() || !col.isEmpty()) {
                rowColText = defaulted(col, "—") + " - " + defaulted(row, "—");
            }
            panelRowCol.setText(rowColText);
        }
        // ==========================================

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

        btnDriverCheckIn.setOnClickListener(v -> {
            postDriverCheckInHardcoded();
        });

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

            // LOCATION = TENTID + " " + COL + "-" + ROW
            String location = (tent + " / " + col + " - " + row).trim();

            String descriptionForPrint = (vehicle != null)
                    ? coalesce(vehicle.marketingdescription, "")
                    : (panelDesc.getText() != null ? panelDesc.getText().toString() : "");

            // Pass LOCATION in existing "tent" parameter
            printVehicleInfoEscPos(
                    defaulted(lot, "—"),
                    defaulted(year, "—"),
                    defaulted(make, "—"),
                    defaulted(model, "—"),
                    defaulted(color, "—"),
                    defaulted(vin, "—"),
                    defaulted(location, "—"),
                    descriptionForPrint /* ignored */
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
                                return n; // return the real bonded device name
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(LOG_TAG, "resolvePrinterName() failed; using default", t);
        }
        // Fallback to the first candidate if no bonded match found
        return PRINTER_CANDIDATES[0];
    }

    // Put common extras, including the dynamic opportunity id (if available)
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
            // Canonical + legacy names so every Activity can read it
            i.putExtra(Nav.EXTRA_OPPORTUNITY_ID, opp); // canonical
            i.putExtra("opportunityId", opp);          // legacy
            i.putExtra("crmId", opp);                  // old
        }
    }

    // Unified resolver for the Opportunity/CRM id used everywhere
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

    // === Build the key-bag label as a bitmap so we can rasterize ===
    private Bitmap buildLabelBitmap(String lot, String year, String make,
                                    String model, String color, String vin, String tent) {
        View label = getLayoutInflater().inflate(R.layout.label_key_bag_tag, null, false);

        ((TextView) label.findViewById(R.id.tvLot)).setText("LOT #: " + defaulted(lot, "—"));
        ((TextView) label.findViewById(R.id.tvYear)).setText("YEAR: " + defaulted(year, "—"));
        ((TextView) label.findViewById(R.id.tvMake)).setText("MAKE: " + defaulted(make, "—"));
        ((TextView) label.findViewById(R.id.tvModel)).setText("MODEL: " + defaulted(model, "—"));
        ((TextView) label.findViewById(R.id.tvColor)).setText("COLOR: " + defaulted(color, "—"));
        ((TextView) label.findViewById(R.id.tvVin)).setText("VIN: " + defaulted(vin, "—"));
        ((TextView) label.findViewById(R.id.tvTent)).setText("TENT: " + defaulted(tent, "—"));

        int designWidthPx = 800; // printer step will scale to 576 dots (80mm)
        int wSpec = View.MeasureSpec.makeMeasureSpec(designWidthPx, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        label.measure(wSpec, hSpec);
        label.layout(0, 0, label.getMeasuredWidth(), label.getMeasuredHeight());

        Bitmap b = Bitmap.createBitmap(label.getMeasuredWidth(), label.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        label.draw(c);
        return b;
    }

    // === Key Bag print (bitmap) ===
    private void printKeyBagTag(String lot, String year, String make,
                                String model, String color, String vin, String tent) {
        if (!ensureBtPermissions()) {
            Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bmp = buildLabelBitmap(lot, year, make, model, color, vin, tent);
        Bitmap bmpToPrint = rotateBitmap(bmp, 90f);

        // force monochrome for reliable thermal printing
        Bitmap mono = toMono(bmpToPrint);

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(resolvePrinterName());
                esc.printBitmap(mono != null ? mono : bmpToPrint, RECEIPT_WIDTH_DOTS, /*feedAndCut*/ true);
                runOnUiThread(() -> Toast.makeText(this, "Printed Key Bag Tag", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                esc.close();
            }
        });
    }

    // Toggle this if orientation is wrong on your device/printer
    private static final float CAR_TAG_ROTATION_DEG = 90f; // try 270f if needed

    // ===== Raster Car Tag with QR (bitmap) =====

    private Bitmap generateQrBitmap(String data, int sizePx) {
        if (data == null || data.trim().isEmpty()) return null;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < sizePx; y++) {
                for (int x = 0; x < sizePx; x++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            Log.w(LOG_TAG, "QR generation failed", e);
            return null;
        }
    }

    private Bitmap scaleToWidth(Bitmap src, int targetWidth) {
        if (src == null) return null;
        if (src.getWidth() == targetWidth) return src;
        float scale = targetWidth / (float) src.getWidth();
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        return Bitmap.createScaledBitmap(src, targetWidth, h, true);
    }

    private Bitmap buildCarTagBitmapWithQr(String lotNum, String tentId, String targetTimeText, String col, String row, String qr) {
        final int W = 800;     // design width; scale later
        final int H = 420;     // slightly taller to fit QR
        final int M = 24;      // margin
        final int QR = 280;    // QR side
        final int GAP = 18;

        String lot = defaulted(lotNum, "—").trim();
        String tent = defaulted(tentId, "—").trim();
        String when = defaulted(formatDayAndTimeSmart(targetTimeText), "—").trim(); // “Friday 3:30 PM”
        String colRow = (defaulted(col, "—") + "-" + defaulted(row, "—")).trim();

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);

        // QR (optional)
        Bitmap qrBmp = (qr != null && !qr.isEmpty()) ? generateQrBitmap(qr, QR) : null;

        // Text area width (reserve space for QR if present)
        int textRight = W - M - (qrBmp != null ? (QR + M) : 0);

        // LOT label
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        p.setTextSize(36f);
        float y = M + p.getTextSize();
        c.drawText("LOT", M, y, p);

        // LOT + TENT (bold, dynamic fit)
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        float big = 112f, min = 72f;
        while (big >= min) {
            p.setTextSize(big);
            float lotW = p.measureText(lot);
            float tentW = p.measureText(tent);
            if (lotW + 36f + tentW <= (textRight - M)) break;
            big -= 2f;
        }
        y += (big + 10f);
        c.drawText(lot, M, y, p);
        float tentX = textRight - p.measureText(tent);
        c.drawText(tent, tentX, y, p);

        // Time + Location
        p.setTextSize(44f);
        y += (p.getTextSize() + GAP);
        c.drawText(when, M, y, p);

        String locStr = colRow;
        float locW = p.measureText(locStr);
        float locX = textRight - locW;
        c.drawText(locStr, locX, y, p);

        // Draw QR + caption
        if (qrBmp != null) {
            int qrX = W - M - QR;
            int qrY = (H - QR) / 2;
            c.drawBitmap(qrBmp, qrX, qrY, null);

            p.setTypeface(Typeface.SANS_SERIF);
            p.setTextSize(24f);
            String cap = "Scan for details";
            float capW = p.measureText(cap);
            float capX = qrX + (QR - capW) / 2f;
            float capY = qrY + QR + p.getTextSize() + 4f;
            if (capY + p.getTextSize() < H - 6) {
                c.drawText(cap, capX, capY, p);
            }
        }

        return bmp;
    }

    // Make the bitmap pure black/white so the printer fires dots reliably
    private Bitmap toMono(Bitmap src) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        int w = src.getWidth(), h = src.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                int y8 = (r * 299 + g * 587 + b * 114) / 1000;
                out.setPixel(x, y, (y8 < 170) ? Color.BLACK : Color.WHITE);
            }
        }
        return out;
    }

    private Bitmap makeQrBitmap(String data, int sizePx) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix m = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < sizePx; y++) {
            for (int x = 0; x < sizePx; x++) {
                bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    private void printBigQrRight(BluetoothEscPosPrinter esc, String data, int qrPx) throws Exception {
        if (data == null || data.trim().isEmpty()) return;

        int canvasW = RECEIPT_WIDTH_DOTS;   // 576 on 80mm
        int padRight = 8;
        Bitmap qr = makeQrBitmap(data, qrPx);
        Bitmap canvas = Bitmap.createBitmap(canvasW, qrPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);
        c.drawColor(Color.WHITE);
        c.drawBitmap(qr, canvasW - qrPx - padRight, 0, null);

        esc.printText(escSeq(cmdAlign(0)));
        esc.printBitmap(canvas, RECEIPT_WIDTH_DOTS, /*feedAndCut*/ false);
    }

    // Print Car Tag
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
                        Toast.makeText(this, "Car tag printed (balanced spacing)", Toast.LENGTH_SHORT).show()
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

    /** Returns like "Friday 3:30 PM" even if input is "Fri 3:30 PM". */
    private String formatDayAndTimeSmart(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        try {
            java.util.regex.Pattern pAbbrev = java.util.regex.Pattern.compile(
                    "^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\b\\s+(\\d{1,2}:\\d{2}\\s?(AM|PM))\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = pAbbrev.matcher(input.trim());
            if (m.find()) {
                String dow = m.group(1).toLowerCase(Locale.US);
                String time = m.group(2).toUpperCase(Locale.US).replaceAll("\\s+", " ");
                String full =
                        dow.startsWith("mon") ? "Monday" :
                                dow.startsWith("tue") ? "Tuesday" :
                                        dow.startsWith("wed") ? "Wednesday" :
                                                dow.startsWith("thu") ? "Thursday" :
                                                        dow.startsWith("fri") ? "Friday" :
                                                                dow.startsWith("sat") ? "Saturday" : "Sunday";
                return full + " " + time;
            }
        } catch (Throwable ignored) {
        }

        try {
            java.time.format.DateTimeFormatter[] parsers = new java.time.format.DateTimeFormatter[]{
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
                    java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a")
            };
            java.time.ZonedDateTime zdt = null;
            for (java.time.format.DateTimeFormatter f : parsers) {
                try {
                    java.time.temporal.TemporalAccessor ta = f.parse(input);
                    if (ta.isSupported(java.time.temporal.ChronoField.OFFSET_SECONDS)) {
                        zdt = java.time.OffsetDateTime.from(ta)
                                .atZoneSameInstant(java.time.ZoneId.systemDefault());
                    } else if (ta.isSupported(java.time.temporal.ChronoField.HOUR_OF_DAY)) {
                        zdt = java.time.LocalDateTime.from(ta)
                                .atZone(java.time.ZoneId.systemDefault());
                    }
                    if (zdt != null) break;
                } catch (Exception ignored) {
                }
            }
            if (zdt != null) {
                java.time.format.DateTimeFormatter out =
                        java.time.format.DateTimeFormatter.ofPattern("EEEE h:mm a");
                return zdt.format(out);
            }
        } catch (Throwable ignored) {
        }

        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(Mon|Tue|Wed|Thu|Fri|Sat|Sun|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday).*?(\\b\\d{1,2}:\\d{2}\\s?(AM|PM)\\b)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = p.matcher(input);
            if (m.find()) {
                String day = m.group(1);
                String time = m.group(2).toUpperCase(Locale.US).replaceAll("\\s+", " ");
                String dl = day.toLowerCase(Locale.US);
                String full =
                        dl.startsWith("mon") ? "Monday" :
                                dl.startsWith("tue") ? "Tuesday" :
                                        dl.startsWith("wed") ? "Wednesday" :
                                                dl.startsWith("thu") ? "Thursday" :
                                                        dl.startsWith("fri") ? "Friday" :
                                                                dl.startsWith("sat") ? "Saturday" : "Sunday";
                return full + " " + time;
            }
        } catch (Throwable ignored) {
        }

        return input;
    }

    private Bitmap buildCarTagBitmapFromXml(String lotNum, String tentId, String targetTimeText, String col, String row) {
        View label = getLayoutInflater().inflate(R.layout.label_car_tag, null, false);

        TextView tvLotLabel = label.findViewById(R.id.tvLotLabel);
        TextView tvLotBig = label.findViewById(R.id.tvLotBig);
        TextView tvSaleDay = label.findViewById(R.id.tvSaleDay);
        TextView tvLocationLabel = label.findViewById(R.id.tvLocationLabel);
        TextView tvLocationMain = label.findViewById(R.id.tvLocationMain);
        TextView tvLocationDetail = label.findViewById(R.id.tvLocationDetail);

        String lot = defaulted(lotNum, "—").trim();
        String tent = defaulted(tentId, "—").trim();
        String time = defaulted(targetTimeText, "—").trim();
        String colRow = (defaulted(col, "—") + " - " + defaulted(row, "—")).trim();

        tvLotLabel.setText("LOT #");
        tvLotBig.setText(lot);
        tvSaleDay.setText(time);

        tvLocationLabel.setText("Location");
        tvLocationMain.setText(tent);
        tvLocationDetail.setText(colRow);

        tvLotLabel.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        tvLotBig.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        tvSaleDay.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        tvLocationLabel.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        tvLocationMain.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        tvLocationDetail.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);

        final int designWidthPx = 800;
        int wSpec = View.MeasureSpec.makeMeasureSpec(designWidthPx, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        label.measure(wSpec, hSpec);
        label.layout(0, 0, label.getMeasuredWidth(), label.getMeasuredHeight());

        int w = label.getMeasuredWidth();
        int h = label.getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            w = designWidthPx;
            h = 200;
        }

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        label.draw(c);
        Log.d(LOG_TAG, "CarTag bitmap built: " + w + "x" + h);
        return b;
    }

    private void saveBitmapToPictures(Bitmap bmp, String name) {
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GT6Driver");
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                Log.d(LOG_TAG, "Saved preview to: " + uri);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to save bitmap preview", e);
        }
    }

    // Bluetooth permission helpers
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
            return ensureBluetoothEnabled(); // pre-Android 10
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

    // === ESC/POS helpers ===
    private static byte[] cmdInit() { return new byte[]{ESC, '@'}; }

    private static byte[] cmdAlign(int n) { return new byte[]{ESC, 'a', (byte) n}; } // 0=L,1=C,2=R

    private static byte[] cmdBold(boolean on) { return new byte[]{ESC, 'E', (byte) (on ? 1 : 0)}; }

    private static byte[] cmdCharSize(int w, int h) {
        return new byte[]{GS, '!', (byte) (((h & 0x7) << 4) | (w & 0x7))};
    }

    private static byte[] cmdFeed(int n) { return new byte[]{ESC, 'd', (byte) Math.max(0, Math.min(255, n))}; }

    private static byte[] cmdFontA() { return new byte[]{ESC, 'M', 0x00}; } // select Font A

    // Print mode (bit5=DW, bit4=DH, bit3=emphasized)
    private static byte[] cmdPrintMode(int n) { return new byte[]{ESC, '!', (byte) n}; }

    private static byte[] cmdEmphasized(boolean on) { return new byte[]{ESC, 'E', (byte) (on ? 1 : 0)}; }

    private static byte[] cmdDoubleStrike(boolean on) { return new byte[]{ESC, 'G', (byte) (on ? 1 : 0)}; }

    private String escSeq(byte[] cmd) {
        try {
            return new String(cmd, StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return new String(cmd);
        }
    }

    private void printLabelValue(BluetoothEscPosPrinter esc, String label, String value) {
        try {
            esc.printText(
                    escSeq(cmdCharSize(1, 1)) +
                            label +
                            escSeq(cmdBold(true)) +
                            defaulted(value, "—") +
                            escSeq(cmdBold(false)) +
                            "\n"
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Bitmap rotateBitmap(Bitmap src, float degrees) {
        if (src == null) return null;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private void printVehicleInfoEscPos(String lot, String year, String make, String model,
                                        String color, String vin, String tent, String desc /* ignored */) {
        if (!ensureBtPermissions()) {
            Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
            return;
        }

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(resolvePrinterName());

                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "LOT      : " + escSeq(cmdBold(true)) + defaulted(lot, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "YEAR     : " + escSeq(cmdBold(true)) + defaulted(year, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "MAKE     : " + escSeq(cmdBold(true)) + defaulted(make, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "MODEL    : " + escSeq(cmdBold(true)) + defaulted(model, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "COLOR    : " + escSeq(cmdBold(true)) + defaulted(color, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "VIN      : " + escSeq(cmdBold(true)) + defaulted(vin, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText(escSeq(cmdFontA()) + escSeq(cmdCharSize(1, 0)) + "LOCATION : " + escSeq(cmdBold(true)) + defaulted(tent, "—") + escSeq(cmdBold(false)));

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
                        Toast.makeText(this, "Vehicle Info printed (feeds 2 lines)", Toast.LENGTH_SHORT).show()
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

    // Native ESC/POS QR (Model 2). moduleSize: 1..16, ecLevel: 48='L',49='M',50='Q',51='H', align: 0/1/2
    private String escposQr(String data, int moduleSize, int ecLevel, int align) {
        if (data == null) data = "";
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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

    private void printQrEscPos(BluetoothEscPosPrinter esc, String data) throws Exception {
        printQrEscPos(esc, data, 16, 'M');
    }

    private void printQrEscPos(BluetoothEscPosPrinter esc, String data, int moduleSize, char ecLevel) throws Exception {
        int size = Math.max(1, Math.min(16, moduleSize));
        byte ec;
        switch (Character.toUpperCase(ecLevel)) {
            case 'L':
                ec = 48;
                break;
            case 'M':
                ec = 49;
                break;
            case 'Q':
                ec = 50;
                break;
            default:
                ec = 51;
                break;
        }

        esc.printText(escSeq(new byte[]{GS, '(', 'k', 0x04, 0x00, 0x31, 0x41, 0x32, 0x00}));
        esc.printText(escSeq(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x43, (byte) size}));
        esc.printText(escSeq(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x45, ec}));

        byte[] d = (data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
        int len = d.length + 3;
        byte pL = (byte) (len & 0xFF);
        byte pH = (byte) ((len >> 8) & 0xFF);
        byte[] store = new byte[8 + d.length];
        store[0] = GS;
        store[1] = '(';
        store[2] = 'k';
        store[3] = pL;
        store[4] = pH;
        store[5] = 0x31;
        store[6] = 0x50;
        store[7] = 0x30;
        System.arraycopy(d, 0, store, 8, d.length);
        esc.printText(escSeq(store));

        esc.printText(escSeq(new byte[]{GS, '(', 'k', 0x03, 0x00, 0x31, 0x51, 0x30}));
    }

    private Bitmap buildCarTagBitmapCarTag(String lotNum, String tentId, String targetTimeText, String col, String row) {
        final int designWidth = 800;
        final int height = 360;
        final int marginPx = 20;

        String lot = defaulted(lotNum, "—").trim();
        String tent = defaulted(tentId, "—").trim();
        String time = defaulted(targetTimeText, "—").trim();
        String colRow = (defaulted(col, "—") + "-" + defaulted(row, "—")).trim();

        Bitmap bmp = Bitmap.createBitmap(designWidth, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);

        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        p.setTextSize(36f);
        float y = marginPx + p.getTextSize();
        canvas.drawText("LOT", marginPx, y, p);

        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        float bigSize = 120f;
        float minBig = 72f;
        float usableW = designWidth - (marginPx * 2);

        while (bigSize >= minBig) {
            p.setTextSize(bigSize);
            float lotW = p.measureText(lot);
            float tentW = p.measureText(tent);
            float gap = 24f;
            if (lotW + gap + tentW <= usableW) break;
            bigSize -= 2f;
        }
        p.setTextSize(bigSize);

        y += (bigSize + 14f);

        float leftX = marginPx;
        float tentW = p.measureText(tent);
        float rightX = designWidth - marginPx - tentW;

        canvas.drawText(lot, leftX, y, p);
        canvas.drawText(tent, rightX, y, p);

        p.setTextSize(42f);
        float footerBaseline = y + p.getTextSize() + 18f;
        canvas.drawText(time, marginPx, footerBaseline, p);

        float crW = p.measureText(colRow);
        float crX = designWidth - marginPx - crW;
        canvas.drawText(colRow, crX, footerBaseline, p);

        return bmp;
    }

    // ======= API call for Assign (reusing existing payload shape) =======
    private void postDriverCheckInHardcoded() {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postDriverCheckInHardcoded() using opportunityId=" + opportunityId);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot check in.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postDriverCheckInHardcoded: missing opportunityId");
            showFullScreenResult(false, "FAILED");
            return;
        }

        // ✅ HARD-CODED values for Driver Check-In - VIRGIL
        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "CheckedIn",            // status (example)
                "BarrettJackson",           // role (example)
                "Barrett-Jackson",           // name (example)
                "Remote Check-In"     // reason (example)
        );


        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        // Optional: log outgoing payload
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
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {}
                    Log.w(HTTP_LOG_TAG, "DriverCheckIn failed. code=" + response.code() + ", body=" + errBody);
                    showFullScreenResult(false, "FAILED");
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

        // Always Arrived + Driver role; reason varies (Check In vs PIGPEN)
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
            okhttp3.Headers h = call.request().headers();
            for (String name : h.names()) {
                Log.v(HTTP_LOG_TAG, "Hdr " + name + ": " + h.get(name));
            }
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
                    showFullScreenResult(true, "Assigned to "+ selectedDriverName);
                } else {
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {
                    }
                    Log.w(HTTP_LOG_TAG, "Consignment update failed. code=" + response.code() + ", body=" + errBody);
                    showFullScreenResult(false, "FAILED");
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Network error updating consignment", t);
                showFullScreenResult(false, "FAILED");
            }
        });
    }

    // ADDED: EventVehicleStatus call (sync; safe because called from printExec background thread)
    private static class ApiResult {
        final boolean ok;
        final int code;          // -1 for exceptions / no response
        final String message;    // error body or exception message

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
                } catch (Exception ignored) {
                }
                Log.w(HTTP_LOG_TAG, "EventVehicleStatus failed. code=" + code + ", body=" + errBody);
                return new ApiResult(false, code, (errBody != null && !errBody.isEmpty()) ? errBody : "HTTP " + code);
            }

            return new ApiResult(true, code, "OK");
        } catch (Exception e) {
            Log.e(HTTP_LOG_TAG, "EventVehicleStatus exception", e);
            return new ApiResult(false, -1, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // === FULL-SCREEN / Banner UI feedback =========================================
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Show a large, top-of-screen banner. success=true => green; false => red. */
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
                    try {
                        decor.removeView(container);
                    } catch (Exception ignored) {
                    }
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
                    try {
                        decor.removeView(overlay);
                    } catch (Exception ignored) {
                    }
                }).start();
            };
            overlay.setOnClickListener(dismiss);
            overlay.postDelayed(() -> {
                if (overlay.getParent() != null) dismiss.onClick(overlay);
            }, 2200);
        });
    }

    // Printer Helpers
    private String fitForCols(String s, int cols) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= cols) return s;
        return s.substring(0, cols);
    }

    // ====== ASSIGN DRIVER DIALOG (no NFC) =========================================
    private void showAssignDriverDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_assign_driver, null, false);

        final TextInputLayout tilDriver = view.findViewById(R.id.tilDriver);
        final TextInputEditText etDriver = view.findViewById(R.id.etDriver);
        final MaterialButton btnCheckIn = view.findViewById(R.id.btnDriverNfc);   // reuse id from layout
        final MaterialButton btnCancel = view.findViewById(R.id.btnDriverCancel);

        final MaterialButton[] pig = new MaterialButton[1];
        pig[0] = view.findViewById(R.id.btnDriverPigPen);

        if (pig[0] == null) {
            MaterialButton created = new MaterialButton(this);
            created.setId(View.generateViewId());
            created.setText("PIGPEN");
            created.setTextColor(getResources().getColor(android.R.color.white));
            created.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#424242")));
            created.setCornerRadius((int) (12 * getResources().getDisplayMetrics().density));

            ViewGroup row = (ViewGroup) btnCheckIn.getParent();
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

        final AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        btnCheckIn.setText("DRIVER");
        btnCancel.setText("CANCEL");
        pig[0].setText("PIGPEN");

        btnCheckIn.setEnabled(false);
        pig[0].setEnabled(false);
        tilDriver.setHelperText(null);
        tilDriver.setError(null);

        selectedDriverNumber = null;
        selectedDriverName = null;

        etDriver.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedDriverNumber = (s == null) ? null : s.toString().trim();
                selectedDriverName = null;
                tilDriver.setError(null);

                boolean ok = false;
                if (s != null && s.length() > 0 && android.text.TextUtils.isDigitsOnly(s)) {
                    try {
                        int num = Integer.parseInt(selectedDriverNumber);
                        String name = DriverDirectory.nameFor(num);
                        if (name != null && !name.isEmpty()) {
                            selectedDriverName = name;
                            ok = true;
                            tilDriver.setHelperText("Driver: " + name);
                        } else {
                            tilDriver.setHelperText("Unknown driver number");
                        }
                    } catch (NumberFormatException ignored) { }
                } else {
                    tilDriver.setHelperText(null);
                }
                btnCheckIn.setEnabled(ok);
                pig[0].setEnabled(ok);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        etDriver.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && btnCheckIn.isEnabled()) {
                btnCheckIn.performClick();
                return true;
            }
            return false;
        });

        btnCancel.setOnClickListener(v -> dlg.dismiss());

        btnCheckIn.setOnClickListener(v -> {
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




















