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
import android.view.View;
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
import com.example.gt6driver.model.ConsignmentKeyPayload;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.DriverTaskApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// NFC
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Handler;
import android.os.Looper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// ZXing (QR)
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class ActionActivity extends AppCompatActivity {
    // Printer/device constants
    private static final String PRINTER_NAME = "SPP-R310"; // fallback printer name
    private static final int RECEIPT_WIDTH_DOTS = 576;     // 80mm; 384 for 58mm

    // ESC/POS bytes
    private static final byte ESC = 0x1B;   // ESC
    private static final byte GS  = 0x1D;   // GS

    // Logging
    private static final String LOG_TAG = "GT6DriverNFC";

    // ===== Permissions =====
    private static final String[] BT_PERMS_S_AND_UP = new String[] {
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };
    private static final String[] BT_PERMS_Q_R = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // UI
    private TextView panelLot, panelDesc, panelVin;
    private ImageView panelImage;
    private MaterialButton btnCheckIn, btnCheckOut, btnKeyBagTags, btnCarTag, btnAssignDriver;

    // Context/state
    private int eventId = -1;
    private String eventName = "";
    private String driver = "";

    // 🔄 Dynamic CRM Opportunity Id (replaces hard-coded constant)
    private String crmId = "";   // set from Intent (vehicle search result)

    // Vehicle (new source of truth)
    private VehicleDetail vehicle;

    // For legacy fallback if no parcelable was provided
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    // Background thread for printing
    private final ExecutorService printExec = Executors.newSingleThreadExecutor();

    // Modern permission launcher
    private ActivityResultLauncher<String[]> btPermsLauncher;

    // NFC
    private NfcAdapter nfcAdapter;
    private volatile boolean nfcWriteMode = false;
    private final AtomicBoolean nfcWriting = new AtomicBoolean(false);
    private String nfcPayloadToWrite = null;
    private AlertDialog nfcPromptDialog = null;
    private static final long READER_MODE_DISABLE_DELAY_MS = 1500;
    private static final long NFC_IGNORE_WINDOW_MS = 1500;
    private volatile long nfcIgnoreUntil = 0L;

    // ===== Driver lookup data (STATIC, hard-coded) =====
    private static final Map<Integer, String> STATIC_DRIVER_MAP = new HashMap<>();
    static {
        STATIC_DRIVER_MAP.put(2,  "Dave Kirk");
        STATIC_DRIVER_MAP.put(3,  "Michael Cooper");
        STATIC_DRIVER_MAP.put(4,  "Greg Schupfer");
        STATIC_DRIVER_MAP.put(5,  "Jeff Hammond");
        STATIC_DRIVER_MAP.put(6,  "Scott Thomas");
        STATIC_DRIVER_MAP.put(7,  "Collin Flatt");
        STATIC_DRIVER_MAP.put(8,  "Jack Conroy");
        STATIC_DRIVER_MAP.put(9,  "Bob Hoffman");
        STATIC_DRIVER_MAP.put(10, "Dave Lightburne");
        STATIC_DRIVER_MAP.put(11, "Bobby Owens");
        STATIC_DRIVER_MAP.put(12, "John Kindell");
        STATIC_DRIVER_MAP.put(13, "Jan Kindell");
        STATIC_DRIVER_MAP.put(14, "Theo Lander");
        STATIC_DRIVER_MAP.put(15, "Mike Berry");
        STATIC_DRIVER_MAP.put(16, "Mike Padilla");
        STATIC_DRIVER_MAP.put(17, "Gary Dallmer");
        STATIC_DRIVER_MAP.put(18, "Brooke Barr");
        STATIC_DRIVER_MAP.put(19, "Dan Sundstrum");
        STATIC_DRIVER_MAP.put(20, "Jennifer Ringle");
        STATIC_DRIVER_MAP.put(21, "Curt Warner");
        STATIC_DRIVER_MAP.put(31, "Deane Chenoweth");
        STATIC_DRIVER_MAP.put(32, "Julie Lander");
        STATIC_DRIVER_MAP.put(33, "Mike Hannam");
        STATIC_DRIVER_MAP.put(34, "Andy Leoni");
        STATIC_DRIVER_MAP.put(35, "Adam Bruder");
        STATIC_DRIVER_MAP.put(36, "Andy Tolmachoff");
        STATIC_DRIVER_MAP.put(37, "Arthur Avery Jr");
        STATIC_DRIVER_MAP.put(38, "Bernhard Neumann");
        STATIC_DRIVER_MAP.put(39, "Bill Caldwell");
        STATIC_DRIVER_MAP.put(40, "Bob Fouraker");
        STATIC_DRIVER_MAP.put(41, "Bob Reece");
        STATIC_DRIVER_MAP.put(42, "Bob Kussard");
        STATIC_DRIVER_MAP.put(43, "Brett Mcgee");
        STATIC_DRIVER_MAP.put(44, "Brett Williams");
        STATIC_DRIVER_MAP.put(45, "Bryce Albertson");
        STATIC_DRIVER_MAP.put(46, "Cal Hiebert");
        STATIC_DRIVER_MAP.put(47, "Chad Erwin");
        STATIC_DRIVER_MAP.put(48, "Charlie Hughes");
        STATIC_DRIVER_MAP.put(49, "Cherie Costello");
        STATIC_DRIVER_MAP.put(50, "Chip Tally");
        STATIC_DRIVER_MAP.put(51, "Christopher Hays");
        STATIC_DRIVER_MAP.put(52, "Cyrus Ringle");
        STATIC_DRIVER_MAP.put(53, "Dan Martinez");
        STATIC_DRIVER_MAP.put(54, "Dan Forseth");
        STATIC_DRIVER_MAP.put(55, "Dave Klumpp");
        STATIC_DRIVER_MAP.put(56, "Dave Bryce");
        STATIC_DRIVER_MAP.put(57, "David Baker");
        STATIC_DRIVER_MAP.put(58, "David Zazueta");
        STATIC_DRIVER_MAP.put(59, "David Breen");
        STATIC_DRIVER_MAP.put(60, "Dennis Bruder");
        STATIC_DRIVER_MAP.put(61, "Dirk Matthews");
        STATIC_DRIVER_MAP.put(62, "Doug Hollingstead");
        STATIC_DRIVER_MAP.put(63, "Earnie Lipps");
        STATIC_DRIVER_MAP.put(64, "Ed Brodzinski");
        STATIC_DRIVER_MAP.put(65, "Ed Dominguez");
        STATIC_DRIVER_MAP.put(66, "Ernie Corral");
        STATIC_DRIVER_MAP.put(67, "Farrel Rasner");
        STATIC_DRIVER_MAP.put(68, "Gabriel Ellington");
        STATIC_DRIVER_MAP.put(69, "Garry Eastwood");
        STATIC_DRIVER_MAP.put(70, "Gene Lusian");
        STATIC_DRIVER_MAP.put(71, "Gerry Callisen");
        STATIC_DRIVER_MAP.put(72, "Gilbert Montez");
        STATIC_DRIVER_MAP.put(73, "Gina Squires");
        STATIC_DRIVER_MAP.put(74, "Jeff Schultz");
        STATIC_DRIVER_MAP.put(75, "Jeff Brainard");
        STATIC_DRIVER_MAP.put(76, "Jim Domenoe");
        STATIC_DRIVER_MAP.put(77, "Jim Costello");
        STATIC_DRIVER_MAP.put(78, "Jimmy Dustman");
        STATIC_DRIVER_MAP.put(79, "Joe Noah");
        STATIC_DRIVER_MAP.put(80, "Joe Borson");
        STATIC_DRIVER_MAP.put(81, "John Gordon");
        STATIC_DRIVER_MAP.put(82, "John Mihalka");
        STATIC_DRIVER_MAP.put(83, "John Miller");
        STATIC_DRIVER_MAP.put(84, "Ken Maki");
        STATIC_DRIVER_MAP.put(85, "Kyndal Schultz");
        STATIC_DRIVER_MAP.put(86, "Lloyd Buelt");
        STATIC_DRIVER_MAP.put(87, "Loren Powell");
        STATIC_DRIVER_MAP.put(88, "Marcos Aguilar");
        STATIC_DRIVER_MAP.put(89, "Martin Amaya Jr.");
        STATIC_DRIVER_MAP.put(90, "Martin Amaya Sr.");
        STATIC_DRIVER_MAP.put(91, "Marty Bellanca");
        STATIC_DRIVER_MAP.put(92, "Marty Lea");
        STATIC_DRIVER_MAP.put(93, "Matt Yare");
        STATIC_DRIVER_MAP.put(94, "Mike Stone");
        STATIC_DRIVER_MAP.put(95, "Mike Cullen");
        STATIC_DRIVER_MAP.put(96, "Mike Hill");
        STATIC_DRIVER_MAP.put(97, "Mike Kniskern");
        STATIC_DRIVER_MAP.put(98, "Mike Evans");
        STATIC_DRIVER_MAP.put(99, "Mike Samer");
        STATIC_DRIVER_MAP.put(100, "Mike Cooper");
        STATIC_DRIVER_MAP.put(101, "Mike Dustman");
        STATIC_DRIVER_MAP.put(102, "Nathaniel Anderson");
        STATIC_DRIVER_MAP.put(103, "Pete Pelletier");
        STATIC_DRIVER_MAP.put(104, "Pete Bergmann");
        STATIC_DRIVER_MAP.put(105, "Pete Carpenter");
        STATIC_DRIVER_MAP.put(106, "Peter Kirdan");
        STATIC_DRIVER_MAP.put(107, "Phil Miller");
        STATIC_DRIVER_MAP.put(108, "Randy Lea");
        STATIC_DRIVER_MAP.put(109, "Randy Solesbee");
        STATIC_DRIVER_MAP.put(110, "Rick Bell");
        STATIC_DRIVER_MAP.put(111, "Rick Eckenrode");
        STATIC_DRIVER_MAP.put(112, "Roland Smith");
        STATIC_DRIVER_MAP.put(113, "Ron Miller");
        STATIC_DRIVER_MAP.put(114, "Ron Jones");
        STATIC_DRIVER_MAP.put(115, "Ryan Ringle");
        STATIC_DRIVER_MAP.put(116, "Ryan Kasprzyk");
        STATIC_DRIVER_MAP.put(117, "Scott Jaeckels");
        STATIC_DRIVER_MAP.put(118, "Scott Tinius");
        STATIC_DRIVER_MAP.put(119, "Sean McNulty");
        STATIC_DRIVER_MAP.put(120, "Steve Squires");
        STATIC_DRIVER_MAP.put(121, "Steve Medina");
        STATIC_DRIVER_MAP.put(122, "Steve Abbit");
        STATIC_DRIVER_MAP.put(123, "Steven Montez");
        STATIC_DRIVER_MAP.put(124, "Terry Crawford");
        STATIC_DRIVER_MAP.put(125, "Troy Bales");
        STATIC_DRIVER_MAP.put(126, "Walt Miller");
        STATIC_DRIVER_MAP.put(127, "Walt Brodzinski");
        STATIC_DRIVER_MAP.put(128, "Hector Quinones");
        STATIC_DRIVER_MAP.put(129, "Vince Fernandez");
        STATIC_DRIVER_MAP.put(130, "Brian Stevens");
        STATIC_DRIVER_MAP.put(131, "Steve Oestreich");
        STATIC_DRIVER_MAP.put(132, "Eric Carlson");
        STATIC_DRIVER_MAP.put(133, "Darryl Toupkin");
        STATIC_DRIVER_MAP.put(134, "Doyle Gaines");
        STATIC_DRIVER_MAP.put(135, "Dave Neumeyer");
        STATIC_DRIVER_MAP.put(136, "Martin Chambers");
        STATIC_DRIVER_MAP.put(137, "Kris Vesely");
        STATIC_DRIVER_MAP.put(138, "Roman Chiago");
        STATIC_DRIVER_MAP.put(139, "Donnie Balentine");
        STATIC_DRIVER_MAP.put(140, "Michael Denny");
        STATIC_DRIVER_MAP.put(141, "John Veith");
        STATIC_DRIVER_MAP.put(142, "Troy Pabst");
        STATIC_DRIVER_MAP.put(143, "Ken Erickson");
        STATIC_DRIVER_MAP.put(144, "Joe Quintanares");
        STATIC_DRIVER_MAP.put(145, "Steffany Stanfield");
        STATIC_DRIVER_MAP.put(146, "Jim Ryan");
        STATIC_DRIVER_MAP.put(147, "Blake Barnett");
        STATIC_DRIVER_MAP.put(148, "Katherine Cox");
        STATIC_DRIVER_MAP.put(149, "Craig Hamre");
        STATIC_DRIVER_MAP.put(150, "Steve Shelly");
        STATIC_DRIVER_MAP.put(151, "Matt Russell");
        STATIC_DRIVER_MAP.put(152, "George Hammond");
        STATIC_DRIVER_MAP.put(153, "Roland Bullerkist");
        STATIC_DRIVER_MAP.put(154, "John Stock");
        STATIC_DRIVER_MAP.put(155, "Ron Perry");
        STATIC_DRIVER_MAP.put(156, "John Rorquist");
        STATIC_DRIVER_MAP.put(157, "Carleton Wahl");
        STATIC_DRIVER_MAP.put(158, "Chuck Williamson");
        STATIC_DRIVER_MAP.put(159, "Lynette Cox");
        STATIC_DRIVER_MAP.put(160, "Sean Pearce");
        STATIC_DRIVER_MAP.put(161, "Rachel Hobbs");
        STATIC_DRIVER_MAP.put(162, "Jon Basham");
        STATIC_DRIVER_MAP.put(163, "Jonathan Hess");
        STATIC_DRIVER_MAP.put(164, "Steve Klein");
        STATIC_DRIVER_MAP.put(165, "David Lugo");
        STATIC_DRIVER_MAP.put(166, "Andy Russell");
        STATIC_DRIVER_MAP.put(167, "Chip Delano");
        STATIC_DRIVER_MAP.put(168, "Tony Goe");
        STATIC_DRIVER_MAP.put(169, "Mike Nolan");
        STATIC_DRIVER_MAP.put(170, "Paul Schoenborn");
        STATIC_DRIVER_MAP.put(171, "Lincoln Belt");
        STATIC_DRIVER_MAP.put(172, "Phil Souza");
        STATIC_DRIVER_MAP.put(173, "Clayton Miller");
        STATIC_DRIVER_MAP.put(174, "Andrew Frederick");
        STATIC_DRIVER_MAP.put(175, "Edward Corona");
        STATIC_DRIVER_MAP.put(176, "Cameron Kittle");
        STATIC_DRIVER_MAP.put(177, "Randy Thompson");
        STATIC_DRIVER_MAP.put(178, "Ken Anderson");
        STATIC_DRIVER_MAP.put(179, "Paul Jacobson");
        STATIC_DRIVER_MAP.put(180, "Brian Kotula");
        STATIC_DRIVER_MAP.put(181, "Bryant Miller");
        STATIC_DRIVER_MAP.put(182, "Mike Richardson");
        STATIC_DRIVER_MAP.put(183, "David Schalles");
        STATIC_DRIVER_MAP.put(184, "Aaron Frederick");
        STATIC_DRIVER_MAP.put(185, "Josh Huggett");
        STATIC_DRIVER_MAP.put(186, "Katelyn Collison");
        STATIC_DRIVER_MAP.put(187, "Revè Osheel");
        STATIC_DRIVER_MAP.put(188, "Lexi Lunquist");
        STATIC_DRIVER_MAP.put(189, "Cash Berger");
        STATIC_DRIVER_MAP.put(190, "Jeni Clayton");
        STATIC_DRIVER_MAP.put(191, "Shane McNulty");
        STATIC_DRIVER_MAP.put(192, "Landon Halonen");
        STATIC_DRIVER_MAP.put(193, "Daunte Messina");
        STATIC_DRIVER_MAP.put(194, "Maya Basham");
        STATIC_DRIVER_MAP.put(195, "Jake Houk");
    }

    // Working fields for the dialog
    private String selectedDriverNumber = null; // typed by user
    private String selectedDriverName = null;   // resolved from map

    // Monospace columns for ESC/POS alignment
    private static final int COLS_NORMAL = 48; // Font A, 80mm paper (576 dots)
    private static final int COLS_W2 = COLS_NORMAL / 2; // ~24 cols when width is 2×

    private static final int COLS_XL = COLS_NORMAL / 3; // ~16 cols at 3× width (w=2)

    private static final int COLS_BIG    = 24; // double-width => ~half columns

    // 🔗 QR URL for car tag
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

        // NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        bindViews();
        readExtras(getIntent());

        // ✅ Coalesce from: explicit extra → legacy extra → vehicle fields
        crmId = coalesce(
                safe(getIntent().getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(getIntent().getStringExtra("crmId")),
                (vehicle != null ? safe(vehicle.opportunityId)    : ""),
                (vehicle != null ? safe(vehicle.crmopportunityid) : "")
        );

        Log.d(LOG_TAG, "Resolved crmId/opportunityId = " + crmId);

        populateHeader();
        wireButtons();
    }

    private void bindViews() {
        panelLot      = findViewById(R.id.panelLot);
        panelDesc     = findViewById(R.id.panelDesc);
        panelVin      = findViewById(R.id.panelVin);
        panelImage    = findViewById(R.id.panelImage);
        btnCheckIn    = findViewById(R.id.btnCheckIn);
        btnCheckOut   = findViewById(R.id.btnCheckOut);
        btnKeyBagTags = findViewById(R.id.btnKeyBagTags);
        btnCarTag     = findViewById(R.id.btnCarTag);
        btnAssignDriver = findViewById(R.id.btnAssignDriver);
    }

    private void readExtras(@NonNull Intent intent) {
        vehicle   = intent.getParcelableExtra(Nav.EXTRA_VEHICLE);
        eventId   = intent.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        eventName = safe(intent.getStringExtra(Nav.EXTRA_EVENT_NAME));
        driver    = safe(intent.getStringExtra(Nav.EXTRA_DRIVER));
        lotLegacy   = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy  = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy   = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));

        // 🔗 QR URL (support several extra names)
        qrUrl = coalesce(
                safe(intent.getStringExtra("qrurl")),
                safe(intent.getStringExtra("qrUrl")),
                safe(intent.getStringExtra("QR_URL"))
        );
    }

    private void populateHeader() {
        String lotStr   = (vehicle != null && vehicle.lotnumber != null)
                ? String.valueOf(vehicle.lotnumber) : lotLegacy;

        String title    = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vinStr   = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

        panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        panelDesc.setText(title);
        panelVin.setText(vinStr.isEmpty() ? "" : ("VIN: " + vinStr));

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

        btnAssignDriver.setOnClickListener(v -> showAssignDriverDialog());

        btnKeyBagTags.setOnClickListener(v -> {
            String lot    = currentLot();
            String year   = (vehicle != null && vehicle.year != null) ? String.valueOf(vehicle.year) : "";
            String make   = (vehicle != null) ? safe(vehicle.make) : "";
            String model  = (vehicle != null) ? safe(vehicle.model) : "";
            String color  = (vehicle != null) ? safe(vehicle.exteriorcolor) : "";
            String vin    = currentVin();
            String tent   = (vehicle != null) ? safe(vehicle.tentid) : "";

            String descriptionForPrint = (vehicle != null)
                    ? coalesce(vehicle.marketingdescription, "")
                    : (panelDesc.getText() != null ? panelDesc.getText().toString() : "");

            printVehicleInfoEscPos(
                    defaulted(lot, "—"),
                    defaulted(year, "—"),
                    defaulted(make, "—"),
                    defaulted(model, "—"),
                    defaulted(color, "—"),
                    defaulted(vin, "—"),
                    defaulted(tent, "—"),
                    descriptionForPrint /* ignored inside */
            );
        });

        btnCarTag.setOnClickListener(v -> {
            String lot   = currentLot();
            String tent  = (vehicle != null) ? safe(vehicle.tentid) : "";
            String col   = (vehicle != null) ? safe(vehicle.col) : "";
            String row   = (vehicle != null) ? safe(vehicle.row) : "";
            String time  = (vehicle != null) ? safe(vehicle.targetTimeText) : "";

            printCarTag(lot, tent, col, row, time);
        });
    }

    // Put common extras, including the dynamic opportunity id (if available)
    private void putCommonExtras(Intent i) {
        if (vehicle != null) {
            i.putExtra(Nav.EXTRA_VEHICLE, vehicle);
        } else {
            i.putExtra(Nav.EXTRA_LOT,   lotLegacy);
            i.putExtra(Nav.EXTRA_DESC,  descLegacy);
            i.putExtra(Nav.EXTRA_VIN,   vinLegacy);
            i.putExtra(Nav.EXTRA_THUMB, thumbLegacy);
        }
        i.putExtra(Nav.EXTRA_EVENT_ID,   eventId);
        i.putExtra(Nav.EXTRA_EVENT_NAME, eventName);
        i.putExtra(Nav.EXTRA_DRIVER,     driver);

        String opp = resolveOpportunityId();
        Log.d(LOG_TAG, "putCommonExtras() passing opportunityId=" + opp);
        if (!opp.isEmpty()) {
            // Canonical + legacy names so every Activity can read it
            i.putExtra(Nav.EXTRA_OPPORTUNITY_ID, opp); // canonical
            i.putExtra("opportunityId", opp);          // legacy
            i.putExtra("crmId", opp);                  // old
        }
    }

    // 🔄 Unified resolver for the Opportunity/CRM id used everywhere
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

    private String defaulted(String v, String d) { return (v != null && !v.trim().isEmpty()) ? v : d; }
    private String safe(String s) { return (s == null) ? "" : s; }
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

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(PRINTER_NAME);
                esc.printBitmap(bmpToPrint, RECEIPT_WIDTH_DOTS, /*feedAndCut*/ true);
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

        String lot    = defaulted(lotNum, "—").trim();
        String tent   = defaulted(tentId, "—").trim();
        String when   = defaulted(formatDayAndTimeSmart(targetTimeText), "—").trim(); // “Friday 3:30 PM”
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
            float lotW  = p.measureText(lot);
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
            float capX = qrX + (QR - capW)/2f;
            float capY = qrY + QR + p.getTextSize() + 4f;
            if (capY + p.getTextSize() < H - 6) {
                c.drawText(cap, capX, capY, p);
            }
        }

        return bmp;
    }

    // 2) Make the bitmap pure black/white so the printer fires dots reliably
    private Bitmap toMono(Bitmap src) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        int w = src.getWidth(), h = src.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                // luminance: 0..255
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                int y8 = (r*299 + g*587 + b*114) / 1000;
                out.setPixel(x, y, (y8 < 170) ? Color.BLACK : Color.WHITE); // threshold ~170
            }
        }
        return out;
    }
    // Day-only formatter -> "(FRIDAY)"
    private Bitmap makeQrBitmap(String data, int sizePx) throws Exception {
        java.util.Map<com.google.zxing.EncodeHintType,Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 0);
        com.google.zxing.common.BitMatrix m = new com.google.zxing.MultiFormatWriter()
                .encode(data, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
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

        // Build a full-width canvas so we can pin QR to the right edge
        int canvasW = RECEIPT_WIDTH_DOTS;   // 576 on 80mm
        int padRight = 8;                   // small gutter
        Bitmap qr = makeQrBitmap(data, qrPx);
        Bitmap canvas = Bitmap.createBitmap(canvasW, qrPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);
        c.drawColor(Color.WHITE);
        c.drawBitmap(qr, canvasW - qrPx - padRight, 0, null);

        // Print left-aligned (we embedded the right padding ourselves)
        esc.printText(escSeq(cmdAlign(0)));
        esc.printBitmap(canvas, RECEIPT_WIDTH_DOTS, /*feedAndCut*/ false);
    }

// Print Car Tag
// Pure ESC/POS: Row1 big ("LOT" left, TENT right), Row2 big (LOT# left, COL-ROW right), Row3 big (TIME left), big QR right
private void printCarTag(String lotNum, String tentId, String col, String row, String targetTimeText) {
    if (!ensureBtPermissions()) {
        Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
        return;
    }

    final String lot    = defaulted(lotNum, "—");
    final String tent   = defaulted(tentId, "—");
    final String colRow = defaulted(col, "—") + "-" + defaulted(row, "—");
    final String day    = defaulted(formatDayParenUpper(targetTimeText), "");
    final String qrData = (vehicle != null) ? safe(vehicle.qrurl) : "";

    printExec.execute(() -> {
        BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
        try {
            esc.connectByName(PRINTER_NAME);

            StringBuilder job = new StringBuilder(512);
            job.append(escSeq(cmdInit()))
                    .append(escSeq(cmdFontA()))
                    .append(escSeq(cmdAlign(0)))
                    .append(escSeq(cmdCharSize(0,0)))
                    // Big text: Double-Width + Double-Height + emphasized + double-strike
                    .append(escSeq(cmdPrintMode(0x30)))   // 0x20 (DW) + 0x10 (DH)
                    .append(escSeq(cmdEmphasized(true)))
                    .append(escSeq(cmdDoubleStrike(true)));

            // ROW 1: "LOT" (left) | TENT value (right)
            String r1 = fixedWidthLine(fitForCols("LOT", COLS_W2),
                    fitForCols(tent,  COLS_W2),
                    COLS_W2);
            job.append(r1).append("\n");

            // ROW 2: LOT NUMBER (left) | col-row (right)
            String r2 = fixedWidthLine(fitForCols(lot,    COLS_W2),
                    fitForCols(colRow, COLS_W2),
                    COLS_W2);
            job.append(r2).append("\n");

            // ROW 3: (FRIDAY)
            job.append(fitForCols(day, COLS_W2)).append("\n");

            // Push text block
            esc.printText(job.toString());
            // --- Native ESC/POS QR on the RIGHT (large) ---
            // --- Native ESC/POS QR on the RIGHT (max size) ---
            // Some firmware prefers normal print mode before QR; reset just in case.
            if (!qrData.isEmpty()) {
                esc.printText(escSeq(cmdEmphasized(false)) + escSeq(cmdDoubleStrike(false)) + escSeq(cmdPrintMode(0x00)));
                // moduleSize=16 (max), EC='H' (51), align=2 (right)
                String qrSeq = escposQr(qrData, 16, 51, 2);
                esc.printText(qrSeq + "\n");
            }


            // Reset modes and feed a touch
            esc.printText(
                    escSeq(cmdEmphasized(false)) +
                            escSeq(cmdDoubleStrike(false)) +
                            escSeq(cmdPrintMode(0x00)) +
                            escSeq(cmdCharSize(0,0)) +
                            escSeq(cmdFeed(2))
            );

            runOnUiThread(() ->
                    Toast.makeText(this, "Car tag printed (pure ESC/POS + large QR)", Toast.LENGTH_SHORT).show()
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

        // Abbrev form: "Fri 3:30 PM"
        try {
            java.util.regex.Pattern pAbbrev = java.util.regex.Pattern.compile(
                    "^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\b\\s+(\\d{1,2}:\\d{2}\\s?(AM|PM))\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = pAbbrev.matcher(input.trim());
            if (m.find()) {
                String dow = m.group(1).toLowerCase(java.util.Locale.US);
                String time = m.group(2).toUpperCase(java.util.Locale.US).replaceAll("\\s+", " ");
                String full =
                        dow.startsWith("mon") ? "Monday" :
                                dow.startsWith("tue") ? "Tuesday" :
                                        dow.startsWith("wed") ? "Wednesday" :
                                                dow.startsWith("thu") ? "Thursday" :
                                                        dow.startsWith("fri") ? "Friday" :
                                                                dow.startsWith("sat") ? "Saturday" :
                                                                        "Sunday";
                return full + " " + time;
            }
        } catch (Throwable ignored) {}

        // Parse common full datetime strings to "Friday h:mm a"
        try {
            try {
                java.time.format.DateTimeFormatter[] parsers = new java.time.format.DateTimeFormatter[] {
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
                    } catch (Exception ignored) {}
                }
                if (zdt != null) {
                    java.time.format.DateTimeFormatter out =
                            java.time.format.DateTimeFormatter.ofPattern("EEEE h:mm a");
                    return zdt.format(out);
                }
            } catch (Throwable ignored) {}

            java.text.SimpleDateFormat[] sdfs = new java.text.SimpleDateFormat[] {
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US),
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",     java.util.Locale.US),
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",       java.util.Locale.US),
                    new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm",          java.util.Locale.US),
                    new java.text.SimpleDateFormat("MMM d, yyyy h:mm a",        java.util.Locale.US)
            };
            java.util.Date d = null;
            for (java.text.SimpleDateFormat f : sdfs) {
                try { d = f.parse(input); break; } catch (Exception ignored) {}
            }
            if (d != null) {
                return new java.text.SimpleDateFormat("EEEE h:mm a", java.util.Locale.US).format(d);
            }
        } catch (Throwable ignored) {}

        // Extract day + time from a mixed string
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(Mon|Tue|Wed|Thu|Fri|Sat|Sun|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday).*?(\\b\\d{1,2}:\\d{2}\\s?(AM|PM)\\b)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = p.matcher(input);
            if (m.find()) {
                String day = m.group(1);
                String time = m.group(2).toUpperCase(java.util.Locale.US).replaceAll("\\s+", " ");
                String dl = day.toLowerCase(java.util.Locale.US);
                String full =
                        dl.startsWith("mon") ? "Monday" :
                                dl.startsWith("tue") ? "Tuesday" :
                                        dl.startsWith("wed") ? "Wednesday" :
                                                dl.startsWith("thu") ? "Thursday" :
                                                        dl.startsWith("fri") ? "Friday" :
                                                                dl.startsWith("sat") ? "Saturday" :
                                                                        "Sunday";
                return full + " " + time;
            }
        } catch (Throwable ignored) {}

        return input;
    }

    private Bitmap buildCarTagBitmapFromXml(String lotNum, String tentId, String targetTimeText, String col, String row) {
        View label = getLayoutInflater().inflate(R.layout.label_car_tag, null, false);

        TextView tvLotLabel       = label.findViewById(R.id.tvLotLabel);
        TextView tvLotBig         = label.findViewById(R.id.tvLotBig);
        TextView tvSaleDay        = label.findViewById(R.id.tvSaleDay);
        TextView tvLocationLabel  = label.findViewById(R.id.tvLocationLabel);
        TextView tvLocationMain   = label.findViewById(R.id.tvLocationMain);
        TextView tvLocationDetail = label.findViewById(R.id.tvLocationDetail);

        String lot    = defaulted(lotNum, "—").trim();
        String tent   = defaulted(tentId, "—").trim();
        String time   = defaulted(targetTimeText, "—").trim();
        String colRow = (defaulted(col, "—") + " - " + defaulted(row, "—")).trim();

        tvLotLabel.setText("LOT #");
        tvLotBig.setText(lot);
        tvSaleDay.setText(time);

        tvLocationLabel.setText("Location");
        tvLocationMain.setText(tent);
        tvLocationDetail.setText(colRow);

        tvLotLabel.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL);
        tvLotBig.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        tvSaleDay.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        tvLocationLabel.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        tvLocationMain.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        tvLocationDetail.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);

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
    private static byte[] cmdInit()                 { return new byte[]{ ESC, '@' }; }
    private static byte[] cmdAlign(int n)           { return new byte[]{ ESC, 'a', (byte) n }; } // 0=L,1=C,2=R
    private static byte[] cmdBold(boolean on)       { return new byte[]{ ESC, 'E', (byte) (on ? 1 : 0) }; }
    private static byte[] cmdCharSize(int w, int h) { return new byte[]{ GS, '!', (byte) (((h & 0x7) << 4) | (w & 0x7)) }; }
    private static byte[] cmdFeed(int n)            { return new byte[]{ ESC, 'd', (byte) Math.max(0, Math.min(255, n)) }; }
    private static byte[] cmdFontA()                { return new byte[]{ ESC, 'M', 0x00 }; } // select Font A

    private String escSeq(byte[] cmd) {
        try { return new String(cmd, StandardCharsets.ISO_8859_1); }
        catch (Exception e) { return new String(cmd); }
    }

    private void printLabelValue(BluetoothEscPosPrinter esc, String label, String value) {
        try {
            // Force size on *each* line so firmware can't silently revert
            esc.printText(
                    escSeq(cmdCharSize(1, 1)) +        // double width + height
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

    // Print Key Tag STuff
    private void printVehicleInfoEscPos(String lot, String year, String make, String model,
                                        String color, String vin, String tent, String desc /* ignored */) {
        if (!ensureBtPermissions()) {
            Toast.makeText(this, "Grant Bluetooth permission to print.", Toast.LENGTH_SHORT).show();
            return;
        }

        printExec.execute(() -> {
            BluetoothEscPosPrinter esc = new BluetoothEscPosPrinter();
            try {
                esc.connectByName(PRINTER_NAME);

                // === HEADER (double width only, no spacing before or after) ===
                esc.printText(
                        escSeq(cmdInit()) +
                                escSeq(cmdFontA()) +
                                escSeq(cmdAlign(1)) +
                                escSeq(cmdCharSize(1, 0)) +       // double width only
                                escSeq(cmdBold(true)) +
                                "*** KEY TAG BAG ***" +            // no trailing feed
                                escSeq(cmdBold(false)) +
                                escSeq(cmdAlign(0)) +              // back to left align
                                "\n"                               // single line break only
                );

                // === BODY (same width, left aligned, tight lines) ===
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "LOT   : " + escSeq(cmdBold(true)) + defaulted(lot, "—")   + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "YEAR  : " + escSeq(cmdBold(true)) + defaulted(year, "—")  + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "MAKE  : " + escSeq(cmdBold(true)) + defaulted(make, "—")  + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "MODEL : " + escSeq(cmdBold(true)) + defaulted(model, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "COLOR : " + escSeq(cmdBold(true)) + defaulted(color, "—") + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "VIN   : " + escSeq(cmdBold(true)) + defaulted(vin, "—")   + escSeq(cmdBold(false)) + "\n");
                esc.printText( escSeq(cmdFontA()) + escSeq(cmdCharSize(1,0)) + "TENT  : " + escSeq(cmdBold(true)) + defaulted(tent, "—")  + escSeq(cmdBold(false)) );

                // No extra feed
                esc.printText(escSeq(cmdCharSize(0, 0)));

                runOnUiThread(() ->
                        Toast.makeText(this, "Key Bag Tag printed (tight top & bottom)", Toast.LENGTH_SHORT).show()
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

    // ===================== NFC =====================
    private String buildNfcPayload(String lotNumber, String crmId, String driverNumber) {
        String d = (driverNumber == null || driverNumber.trim().isEmpty()) ? "0" : driverNumber.trim();
        return "arrived|" + lotNumber + "|" + crmId;
    }

    private void showAssignDriverDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_assign_driver, null, false);

        final TextInputLayout tilDriver = view.findViewById(R.id.tilDriver);
        final TextInputEditText etDriver = view.findViewById(R.id.etDriver);
        final MaterialButton btnNfc = view.findViewById(R.id.btnDriverNfc);
        final MaterialButton btnCancel = view.findViewById(R.id.btnDriverCancel);

        final AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        btnNfc.setEnabled(false);
        tilDriver.setHelperText(null);
        tilDriver.setError(null);

        etDriver.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedDriverNumber = (s == null) ? null : s.toString().trim();
                selectedDriverName = null;
                tilDriver.setError(null);

                boolean digitsOnly = s != null && s.length() > 0 && android.text.TextUtils.isDigitsOnly(s);
                if (!digitsOnly) {
                    btnNfc.setEnabled(false);
                    tilDriver.setHelperText(null);
                    return;
                }

                try {
                    int num = Integer.parseInt(selectedDriverNumber);
                    String name = STATIC_DRIVER_MAP.get(num);
                    if (name != null && !name.isEmpty()) {
                        selectedDriverName = name;
                        btnNfc.setEnabled(true);
                        tilDriver.setHelperText("Driver: " + name);
                    } else {
                        selectedDriverName = null;
                        btnNfc.setEnabled(false);
                        tilDriver.setHelperText("Unknown driver number");
                    }
                } catch (NumberFormatException e) {
                    selectedDriverName = null;
                    btnNfc.setEnabled(false);
                    tilDriver.setHelperText(null);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        etDriver.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && btnNfc.isEnabled()) {
                btnNfc.performClick();
                return true;
            }
            return false;
        });

        btnCancel.setOnClickListener(v -> dlg.dismiss());

        btnNfc.setOnClickListener(v -> {
            if (selectedDriverNumber == null || selectedDriverName == null) {
                tilDriver.setError(getString(R.string.driver_number_error));
                return;
            }

            String lotForPayload = defaulted(currentLot(), "UNKNOWN_LOT");
            String payload = buildNfcPayload(lotForPayload, resolveOpportunityId(), selectedDriverNumber);
            dlg.dismiss();
            startNfcWriteFlow(payload);
        });
    }

    private NdefMessage buildTextNdefMessage(String text) {
        byte[] langBytes = Locale.ENGLISH.getLanguage().getBytes(StandardCharsets.US_ASCII);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int langLen = langBytes.length;
        int textLen = textBytes.length;
        byte[] payload = new byte[1 + langLen + textLen];
        payload[0] = (byte) (langLen & 0x1F); // UTF-8 + lang length
        System.arraycopy(langBytes, 0, payload, 1, langLen);
        System.arraycopy(textBytes, 0, payload, 1 + langLen, textLen);

        NdefRecord textRecord = new NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT,
                new byte[0],
                payload
        );
        return new NdefMessage(new NdefRecord[]{ textRecord });
    }

    private void startNfcWriteFlow(String payload) {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not available on this device.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable NFC in Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        nfcPayloadToWrite = payload;
        nfcWriteMode = true;
        nfcWriting.set(false);

        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;

        nfcAdapter.enableReaderMode(this, this::onTagDiscovered, flags, null);

        Log.i(LOG_TAG, "Starting NFC write. lot=" + defaulted(currentLot(), "UNKNOWN_LOT")
                + ", payloadLen=" + payload.length() + ", payload=\"" + payload + "\"");

        nfcPromptDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Write to NFC tag")
                .setMessage("Hold a tag to the device to update LOT # " + defaulted(currentLot(), "UNKNOWN_LOT"))
                .setCancelable(true)
                .setNegativeButton("Cancel", (d, w) -> stopNfcWriteFlow(false))
                .create();
        nfcPromptDialog.show();
    }

    private void stopNfcWriteFlow(boolean wrote) {
        nfcWriteMode = false;
        nfcPayloadToWrite = null;
        nfcIgnoreUntil = android.os.SystemClock.uptimeMillis() + NFC_IGNORE_WINDOW_MS;

        if (nfcPromptDialog != null && nfcPromptDialog.isShowing()) {
            try { nfcPromptDialog.dismiss(); } catch (Exception ignored) {}
        }
        if (wrote) Toast.makeText(this, "NFC write complete.", Toast.LENGTH_SHORT).show();

        if (nfcAdapter != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { nfcAdapter.disableReaderMode(this); } catch (Exception ignored) {}
            }, READER_MODE_DISABLE_DELAY_MS);
        }
    }

    private void onTagDiscovered(Tag tag) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now < nfcIgnoreUntil) return;
        if (!nfcWriteMode) return;
        if (!nfcWriting.compareAndSet(false, true)) return;
        final String payload = nfcPayloadToWrite;
        if (payload == null) { nfcWriting.set(false); return; }

        try {
            Log.d(LOG_TAG, "Tag discovered. techs=" + Arrays.toString(tag.getTechList()));
            NdefMessage msg = buildTextNdefMessage(payload);
            Log.d(LOG_TAG, "Attempting write: need=" + msg.toByteArray().length + "B, payload=\"" + payload + "\"");
            boolean success = writeNdefMessageToTag(msg, tag);
            if (success) {
                Log.i(LOG_TAG, "NFC write SUCCESS");
                runOnUiThread(() -> {
                    stopNfcWriteFlow(true);
                    postConsignmentKeyUpdate();
                });
            } else {
                final String diag = debugTagInfo(tag, msg);
                Log.e(LOG_TAG, "Failed to write to tag. " + diag);
                nfcWriting.set(false);
                runOnUiThread(() -> Toast.makeText(this, "Failed to write to tag. See Logcat (" + LOG_TAG + ")", Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            nfcWriting.set(false);
            Log.e(LOG_TAG, "Exception during write", e);
            runOnUiThread(() -> Toast.makeText(this, "NFC error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private boolean writeNdefMessageToTag(NdefMessage message, Tag tag) {
        final int MAX_ATTEMPTS = 4;
        final long STABILIZE_MS = 150;
        final long RETRY_DELAY_MS = 200;

        Ndef ndef = null;
        NdefFormatable format = null;

        try {
            ndef = Ndef.get(tag);
            if (ndef != null) {
                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    try {
                        if (!ndef.isConnected()) ndef.connect();
                        if (!ndef.isWritable()) {
                            Log.w(LOG_TAG, "Tag is read-only");
                            return false;
                        }
                        int need = message.toByteArray().length;
                        int max  = ndef.getMaxSize();
                        if (need > max) {
                            Log.w(LOG_TAG, "Tag too small (" + need + " > " + max + " bytes)");
                            return false;
                        }
                        if (attempt == 1 && STABILIZE_MS > 0) {
                            try { Thread.sleep(STABILIZE_MS); } catch (InterruptedException ignored) {}
                        }

                        ndef.writeNdefMessage(message);
                        Log.d(LOG_TAG, "Ndef write() completed on attempt " + attempt);
                        return true;

                    } catch (android.nfc.TagLostException tle) {
                        Log.w(LOG_TAG, "TagLostException on attempt " + attempt + "/" + MAX_ATTEMPTS);
                        try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                    } catch (java.io.IOException ioe) {
                        Log.w(LOG_TAG, "IOException on attempt " + attempt + "/" + MAX_ATTEMPTS + ": " + ioe);
                        try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Unexpected error during NDEF write on attempt " + attempt, e);
                        return false;
                    } finally {
                        try { if (ndef.isConnected()) ndef.close(); } catch (Exception ignored) {}
                        ndef = Ndef.get(tag);
                    }

                }
                Log.e(LOG_TAG, "Failed to write after " + MAX_ATTEMPTS + " attempts.");
                return false;
            } else {
                format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        try { Thread.sleep(STABILIZE_MS); } catch (InterruptedException ignored) {}
                        format.format(message);
                        Log.d(LOG_TAG, "Tag formatted and written as NDEF");
                        return true;
                    } catch (android.nfc.TagLostException tle) {
                        Log.w(LOG_TAG, "TagLostException during format.");
                        return false;
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error formatting tag as NDEF", e);
                        return false;
                    } finally {
                        try { format.close(); } catch (Exception ignored) {}
                    }
                } else {
                    Log.w(LOG_TAG, "Tag doesn't support NDEF (techs: " + Arrays.toString(tag.getTechList()) + ")");
                    return false;
                }
            }
        } finally {
            try { if (ndef != null && ndef.isConnected()) ndef.close(); } catch (Exception ignored) {}
            try { if (format != null) format.close(); } catch (Exception ignored) {}
        }
    }
    // Print mode (bit5=DW, bit4=DH, bit3=emphasized)
    private static byte[] cmdPrintMode(int n)        { return new byte[]{ ESC, '!', (byte) n }; }
    private static byte[] cmdEmphasized(boolean on)  { return new byte[]{ ESC, 'E', (byte) (on ? 1 : 0) }; }
    private static byte[] cmdDoubleStrike(boolean on){ return new byte[]{ ESC, 'G', (byte) (on ? 1 : 0) }; }

    // Build native ESC/POS QR sequence (Model 2, adjustable size + EC)
// Native ESC/POS QR (Model 2). moduleSize: 1..16, ecLevel: 48='L',49='M',50='Q',51='H', align: 0/1/2
    private String escposQr(String data, int moduleSize, int ecLevel, int align) {
        if (data == null) data = "";
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.write(new byte[]{ ESC, 'a', (byte) align });                        // align

            out.write(new byte[]{ GS, '(', 'k', 0x04, 0x00, 0x31, 0x41, 0x32, 0x00 }); // model 2
            out.write(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x43, (byte) Math.min(16, Math.max(1, moduleSize)) }); // module size
            out.write(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x45, (byte) ecLevel }); // EC level

            byte[] payload = data.getBytes(StandardCharsets.ISO_8859_1);
            int len = payload.length + 3;
            byte pL = (byte) (len & 0xFF);
            byte pH = (byte) ((len >> 8) & 0xFF);
            out.write(new byte[]{ GS, '(', 'k', pL, pH, 0x31, 0x50, 0x30 }); // store
            out.write(payload);

            out.write(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x51, 0x30 }); // print
            return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return "";
        }
    }


    // Day-only "(FRIDAY)" formatter (keep your existing if already present)
    private String formatDayParenUpper(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (m1.find()) {
            String d = m1.group(1).toLowerCase(java.util.Locale.US);
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
        if (m2.find()) return "(" + m2.group(1).toUpperCase(java.util.Locale.US) + ")";
        return "(" + s.toUpperCase(java.util.Locale.US) + ")";
    }


    // QR by esc pos

    private void printQrEscPos(BluetoothEscPosPrinter esc, String data) throws Exception {
        printQrEscPos(esc, data, 16, 'M'); // use max size by default
    }

    private void printQrEscPos(BluetoothEscPosPrinter esc, String data, int moduleSize, char ecLevel) throws Exception {
        int size = Math.max(1, Math.min(16, moduleSize)); // ESC/POS allows 1..16
        byte ec;
        switch (Character.toUpperCase(ecLevel)) {
            case 'L': ec = 48; break; // L
            case 'M': ec = 49; break; // M
            case 'Q': ec = 50; break; // Q
            default : ec = 51; break; // H
        }

        // Model 2
        esc.printText(escSeq(new byte[]{ GS, '(', 'k', 0x04, 0x00, 0x31, 0x41, 0x32, 0x00 }));
        // Module size (dot size)
        esc.printText(escSeq(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x43, (byte) size }));
        // Error correction level
        esc.printText(escSeq(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x45, ec }));

        // Store data
        byte[] d = (data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
        int len = d.length + 3;
        byte pL = (byte) (len & 0xFF);
        byte pH = (byte) ((len >> 8) & 0xFF);
        byte[] store = new byte[8 + d.length];
        store[0] = GS; store[1] = '('; store[2] = 'k'; store[3] = pL; store[4] = pH; store[5] = 0x31; store[6] = 0x50; store[7] = 0x30;
        System.arraycopy(d, 0, store, 8, d.length);
        esc.printText(escSeq(store));

        // Print symbol
        esc.printText(escSeq(new byte[]{ GS, '(', 'k', 0x03, 0x00, 0x31, 0x51, 0x30 }));
    }




    private Bitmap buildCarTagBitmapCarTag(String lotNum, String tentId, String targetTimeText, String col, String row) {
        final int designWidth = 800;
        final int height      = 360;
        final int marginPx    = 20;

        String lot   = defaulted(lotNum, "—").trim();
        String tent  = defaulted(tentId, "—").trim();
        String time  = defaulted(targetTimeText, "—").trim();
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
        float minBig  = 72f;
        float usableW = designWidth - (marginPx * 2);

        while (bigSize >= minBig) {
            p.setTextSize(bigSize);
            float lotW  = p.measureText(lot);
            float tentW = p.measureText(tent);
            float gap   = 24f;
            if (lotW + gap + tentW <= usableW) break;
            bigSize -= 2f;
        }
        p.setTextSize(bigSize);

        y += (bigSize + 14f);

        float leftX = marginPx;
        float tentW = p.measureText(tent);
        float rightX = designWidth - marginPx - tentW;

        canvas.drawText(lot,  leftX,  y, p);
        canvas.drawText(tent, rightX, y, p);

        p.setTextSize(42f);
        float footerBaseline = y + p.getTextSize() + 18f;
        canvas.drawText(time, marginPx, footerBaseline, p);

        float crW = p.measureText(colRow);
        float crX = designWidth - marginPx - crW;
        canvas.drawText(colRow, crX, footerBaseline, p);

        return bmp;
    }

    private String debugTagInfo(Tag tag, NdefMessage attempted) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("techs=").append(Arrays.toString(tag.getTechList()));
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                try {
                    ndef.connect();
                    boolean writable = ndef.isWritable();
                    int max = ndef.getMaxSize();
                    int need = (attempted != null) ? attempted.toByteArray().length : -1;
                    sb.append(", writable=").append(writable)
                            .append(", max=").append(max).append("B");
                    if (need >= 0) sb.append(", need=").append(need).append("B");
                    try {
                        NdefMessage current = ndef.getNdefMessage();
                        if (current != null) {
                            sb.append(", hasNdef=true, curLen=")
                                    .append(current.toByteArray().length).append("B");
                        } else {
                            sb.append(", hasNdef=false");
                        }
                    } catch (Exception ignore) { }
                } finally {
                    try { ndef.close(); } catch (Exception ignore) {}
                }
            } else {
                sb.append(", ndef=null");
            }
        } catch (Exception e) {
            sb.append(", diagErr=").append(e.getClass().getSimpleName());
        }
        return sb.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcWriteMode) {
            stopNfcWriteFlow(false);
        }
    }

    // Printer Helpers
    private String fitForCols(String s, int cols) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= cols) return s;
        return s.substring(0, cols);
    }

    // ======= API call after NFC success =======
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";

    private void postConsignmentKeyUpdate() {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postConsignmentKeyUpdate() using opportunityId=" + opportunityId);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing opportunityId");
            return;
        }
        if (selectedDriverName == null || selectedDriverName.trim().isEmpty()) {
            Toast.makeText(this, "Missing driver name; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing driver name");
            return;
        }

        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "Arrived",
                "Driver",
                selectedDriverName,
                "Moving to Check In"
        );

        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        try {
            Log.i("GT6DriverHTTP", call.request().method() + " " + call.request().url());
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
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(HTTP_LOG_TAG, "Response code=" + response.code());
                if (response.isSuccessful()) {
                    Toast.makeText(ActionActivity.this, "Consignment updated: Arrived (Driver)", Toast.LENGTH_SHORT).show();
                } else {
                    String errBody = null;
                    try { errBody = response.errorBody() != null ? response.errorBody().string() : null; } catch (Exception ignored) {}
                    Log.w(HTTP_LOG_TAG, "Consignment update failed. code=" + response.code() + ", body=" + errBody);
                    Toast.makeText(ActionActivity.this, "Consignment update failed (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Network error updating consignment", t);
                Toast.makeText(ActionActivity.this, "Network error updating consignment", Toast.LENGTH_LONG).show();
            }
        });
    }

}

















