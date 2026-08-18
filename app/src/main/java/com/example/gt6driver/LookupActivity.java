// app/src/main/java/com/example/gt6driver/LookupActivity.java
package com.example.gt6driver;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.LotSearchResponse;
import com.example.gt6driver.net.VehicleSearchApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LookupActivity extends AppCompatActivity {

    private TextInputEditText lotNumberInput, vinInput, descriptionInput;
    private MaterialButton btnSearch;
    private RecyclerView resultsRecycler;
    private VehicleAdapter adapter;

    private String eventName;
    private int eventId;
    private String driver;
    private String userType;
    private VehicleSearchApi vehicleApi;
    private Call<List<LotSearchResponse>> lotCall;
    private Call<List<LotSearchResponse>> vinCall;
    private Call<List<LotSearchResponse>> descCall;
    private TextView errorText; // TextView with id=textError in activity_lookup.xml

    // If tbuncpath might be relative, set your base here (or leave empty)
    private static final String IMG_BASE = ""; // e.g., "http://auctioneer.barrett-jackson.com/"

    // ===================== STORAGE GUARD =====================
    // Adjust this threshold as needed (example: 2GB free required)
    private static final long MIN_FREE_BYTES = 2L * 1024L * 1024L * 1024L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lookup);

        // From MainActivity (event/driver context)
        eventName = getIntent().getStringExtra("eventName");
        eventId   = getIntent().getIntExtra("eventId", -1);
        driver    = getIntent().getStringExtra(Nav.EXTRA_DRIVER);
        userType  = getIntent().getStringExtra(Nav.EXTRA_USER_TYPE);
        ApiClient.configure(this);

        lotNumberInput   = findViewById(R.id.lotNumberInput);
        vinInput         = findViewById(R.id.vinInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        btnSearch        = findViewById(R.id.btnSearch);
        resultsRecycler  = findViewById(R.id.resultsRecycler);
        errorText        = findViewById(R.id.textError); // <-- make sure this exists in your layout

        resultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehicleAdapter();
        resultsRecycler.setAdapter(adapter);

        // Start EMPTY
        adapter.setItems(new ArrayList<>());

        // Row click → highlight + navigate based on user type, passing the WHOLE object
        adapter.setOnItemClickListener((position, vehicle) -> {
            adapter.setSelectedPosition(position);

            boolean isPropertyUser = userType != null && "Property".equalsIgnoreCase(userType.trim());
            boolean isKeyUser = userType != null && "Key".equalsIgnoreCase(userType.trim());
            boolean isVideoReviewer = userType != null && "Video Reviewer".equalsIgnoreCase(userType.trim());

            Class<?> destination = ActionActivity.class;
            if (isPropertyUser) {
                destination = PropertyActivity.class;
            } else if (isKeyUser) {
                destination = RemoteKeyControlActivity.class;
            } else if (isVideoReviewer) {
                destination = VideoReviewActivity.class;
            }

            Intent intent = new Intent(LookupActivity.this, destination);

            // ✅ use shared keys for the parcelable/context
            intent.putExtra(Nav.EXTRA_VEHICLE, vehicle);
            intent.putExtra(Nav.EXTRA_EVENT_ID, eventId);
            intent.putExtra(Nav.EXTRA_EVENT_NAME, eventName);
            intent.putExtra(Nav.EXTRA_DRIVER, driver);
            intent.putExtra(Nav.EXTRA_USER_TYPE, userType);

            // ✅ pass opp id under BOTH keys to satisfy old/new readers downstream
            String opp = (vehicle != null && vehicle.opportunityId != null) ? vehicle.opportunityId : "";
            if (opp != null && !opp.trim().isEmpty()) {
                intent.putExtra(Nav.EXTRA_OPPORTUNITY_ID, opp); // new canonical
                intent.putExtra("opportunityId", opp);          // legacy readers
            }

            startActivity(intent);
        });

        // Optional UX: keep mutual exclusivity of inputs
        setupMutuallyExclusiveInputs();

        // Enable search only when any field has input
        btnSearch.setEnabled(false);
        TextWatcher tw = new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Re-check storage each time input changes (lightweight) so button state stays correct
                boolean hasAnyInput = !safe(vinInput).isEmpty()
                        || !safe(lotNumberInput).isEmpty()
                        || !safe(descriptionInput).isEmpty();

                boolean storageOk = checkStorageAndApplyUiGuard(false);
                btnSearch.setEnabled(hasAnyInput && storageOk);

                if (errorText != null && storageOk) errorText.setVisibility(View.GONE);
            }
        };
        vinInput.addTextChangedListener(tw);
        lotNumberInput.addTextChangedListener(tw);
        descriptionInput.addTextChangedListener(tw);

        vehicleApi = ApiClient.getMemberApi().create(VehicleSearchApi.class);

        // Search: pick one of VIN / Lot / Terms based on which has content
        btnSearch.setOnClickListener(v -> {
            hideKeyboard();
            lotNumberInput.clearFocus();
            vinInput.clearFocus();
            descriptionInput.clearFocus();

            // Hard guard right before action
            if (!checkStorageAndApplyUiGuard(true)) {
                adapter.setItems(new ArrayList<>());
                return;
            }

            String vinStr = safe(vinInput);
            String lotStr = safe(lotNumberInput);
            String descStr = safe(descriptionInput);

            if (!vinStr.isEmpty()) {
                performVinSearch();
            } else if (!lotStr.isEmpty()) {
                performLotSearch();
            } else if (!descStr.isEmpty()) {
                performDescriptionSearch();
            } else {
                showError("Enter VIN or Lot.");
                adapter.setItems(new ArrayList<>());
            }
        });

        // Guard missing eventId
        if (eventId <= 0) {
            showError("Missing eventId. Please select an event and try again.");
            btnSearch.setEnabled(false);
        }

        // ✅ Storage check when screen loads
        checkStorageAndApplyUiGuard(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApiClient.configure(this);
        vehicleApi = ApiClient.getMemberApi().create(VehicleSearchApi.class);

        // ✅ Cancel any in-flight calls
        cancelAll();

        // ✅ Clear previous inputs
        if (lotNumberInput != null) lotNumberInput.setText("");
        if (vinInput != null) vinInput.setText("");
        if (descriptionInput != null) descriptionInput.setText("");

        // ✅ Clear results + selection
        if (adapter != null) adapter.setItems(new ArrayList<>());

        // ✅ Hide error message
        if (errorText != null) errorText.setVisibility(View.GONE);

        // ✅ Clear focus + keyboard
        lotNumberInput.clearFocus();
        vinInput.clearFocus();
        descriptionInput.clearFocus();
        hideKeyboard();

        // ✅ Storage check when screen is revisited/refreshed
        checkStorageAndApplyUiGuard(true);

        // ✅ Search button stays disabled (no input)
        if (btnSearch != null) {
            btnSearch.setText("Search");
            btnSearch.setEnabled(false);
        }
    }


    private String safe(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        btnSearch.setEnabled(!loading);
        btnSearch.setText(loading ? "Searching..." : "Search");
        if (errorText != null) errorText.setVisibility(View.GONE);
    }

    /**
     * Default error display (keeps whatever XML constraints you have for the "no results" area).
     */
    private void showError(String msg) {
        if (errorText != null) {
            // restore a "normal" look (in case low-storage previously styled it)
            errorText.setBackgroundColor(0x00000000);
            errorText.setPadding(0, 0, 0, 0);
            errorText.setGravity(Gravity.START);

            errorText.setTextSize(18f);
            errorText.setTextColor(0xFFFFFFFF);

            errorText.setText(msg);
            errorText.setVisibility(View.VISIBLE);
        } else {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void showNoResults(String searchType, String searchValue) {
        String value = searchValue == null ? "" : searchValue.trim();
        String msg = value.isEmpty()
                ? "No matching vehicles found.\nTry another search."
                : "No matching vehicle found for " + searchType + " \"" + value + "\".\nTry another search.";

        if (errorText != null) {
            errorText.setBackgroundColor(0x99000000);
            errorText.setPadding(dp(20), dp(16), dp(20), dp(16));
            errorText.setGravity(Gravity.CENTER);
            errorText.setTextSize(20f);
            errorText.setTextColor(0xFFFFFFFF);
            errorText.setText(msg);
            errorText.setVisibility(View.VISIBLE);
        } else {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private boolean showNoResultsIfNotFound(Response<List<LotSearchResponse>> resp,
                                            String searchType,
                                            String searchValue) {
        if (resp != null && resp.code() == 404) {
            showNoResults(searchType, searchValue);
            adapter.setItems(new ArrayList<>());
            return true;
        }
        return false;
    }

    private String formatSearchError(Response<?> resp) {
        int code = resp == null ? 0 : resp.code();
        String body = readErrorBody(resp);
        String message = extractApiErrorMessage(body);
        return message.isEmpty() ? "Search failed: HTTP " + code : message;
    }

    private String readErrorBody(Response<?> resp) {
        if (resp == null || resp.errorBody() == null) return "";
        try {
            return resp.errorBody().string().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private String extractApiErrorMessage(String raw) {
        if (raw == null) return "";

        String text = raw.trim();
        if (text.isEmpty()) return "";

        try {
            if (text.startsWith("{")) {
                return messageFromJsonObject(new JSONObject(text)).trim();
            }
            if (text.startsWith("[")) {
                return messageFromJsonArray(new JSONArray(text)).trim();
            }
        } catch (JSONException ignored) {
            // Use the raw response below when the body is plain text.
        }

        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String messageFromJsonObject(JSONObject object) throws JSONException {
        String direct = firstString(object, "message", "Message", "error", "Error", "detail", "Detail");
        if (!direct.isEmpty()) return direct;

        String errors = messageFromJsonValue(object.opt("errors"));
        if (!errors.isEmpty()) return errors;

        String title = firstString(object, "title", "Title");
        if (!title.isEmpty()) return title;

        return "";
    }

    private String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String messageFromJsonValue(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof JSONArray) return messageFromJsonArray((JSONArray) value);
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            ArrayList<String> parts = new ArrayList<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String message = messageFromJsonValue(object.opt(key));
                if (!message.isEmpty()) parts.add(key + ": " + message);
            }
            return joinMessages(parts);
        }
        return String.valueOf(value).trim();
    }

    private String messageFromJsonArray(JSONArray array) throws JSONException {
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String message = messageFromJsonValue(array.opt(i));
            if (!message.isEmpty()) parts.add(message);
        }
        return joinMessages(parts);
    }

    private String joinMessages(ArrayList<String> parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) joined.append('\n');
            joined.append(part);
        }
        return joined.toString();
    }

    // Clear Previous input and results
    private void resetLookupUi() {
        // cancel any in-flight network calls
        cancelAll();

        // clear inputs
        if (lotNumberInput != null) lotNumberInput.setText("");
        if (vinInput != null) vinInput.setText("");
        if (descriptionInput != null) descriptionInput.setText("");

        // clear results
        if (adapter != null) adapter.setItems(new ArrayList<>());

        // hide error
        if (errorText != null) errorText.setVisibility(View.GONE);

        // reset search button
        if (btnSearch != null) {
            btnSearch.setText("Search");
            btnSearch.setEnabled(false);
        }

        // clear focus + keyboard
        lotNumberInput.clearFocus();
        vinInput.clearFocus();
        descriptionInput.clearFocus();
        hideKeyboard();
    }


    /**
     * ✅ BIG + CENTERED low-storage message in middle of screen.
     * Requires activity_lookup root to be a ConstraintLayout (typical).
     */
    private void showErrorLowCentered(String msg) {
        if (errorText == null) {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        errorText.setText(msg);
        errorText.setTextSize(26f);
        errorText.setTextColor(0xFFFFFFFF);
        errorText.setBackgroundColor(0xCCB00020); // translucent red
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(dp(20), dp(20), dp(20), dp(20));

        ViewGroup.LayoutParams lp = errorText.getLayoutParams();
        if (lp instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams clp = (ConstraintLayout.LayoutParams) lp;

            // center it
            clp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            clp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            clp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            clp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            clp.verticalBias = 0.50f;

            // match constraints for width
            clp.width = 0;

            errorText.setLayoutParams(clp);
        }

        errorText.setVisibility(View.VISIBLE);
    }

    private void cancelAll() {
        if (vinCall  != null && !vinCall.isCanceled())  { vinCall.cancel();  vinCall = null; }
        if (lotCall  != null && !lotCall.isCanceled())  { lotCall.cancel();  lotCall = null; }
        if (descCall != null && !descCall.isCanceled()) { descCall.cancel(); descCall = null; }
    }

    // ===================== STORAGE HELPERS =====================

    private long getAvailableBytesInternal() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        double b = (double) bytes;
        int i = 0;
        while (b >= 1024.0 && i < units.length - 1) {
            b /= 1024.0;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f %s", b, units[i]);
    }

    private boolean checkStorageAndApplyUiGuard(boolean showMessage) {
        long free = getAvailableBytesInternal();
        boolean ok = free >= MIN_FREE_BYTES;

        if (!ok) {
            btnSearch.setEnabled(false);

            if (showMessage) {
                String msg = "LOW STORAGE: Only " + formatBytes(free) + " free.\n"
                        + "Free space and try again.\n"
                        + "Need at least " + formatBytes(MIN_FREE_BYTES) + ".";
                showErrorLowCentered(msg);
            }
        }

        return ok;
    }

    private void performLotSearch() {
        if (!checkStorageAndApplyUiGuard(true)) return;

        String lotStr = safe(lotNumberInput);
        if (lotStr.isEmpty()) {
            showError("Enter a Lot number.");
            adapter.setItems(new ArrayList<>());
            return;
        }

// allow optional leading "-" and optional decimal (e.g. -145, 300, 300.1, -300.1)
        if (!lotStr.matches("-?\\d+(?:\\.\\d+)?")) {
            showError("Lot must be a number (e.g., -145, 300, 300.1).");
            adapter.setItems(new ArrayList<>());
            return;
        }


        cancelAll();
        setLoading(true);

        lotCall = vehicleApi.searchByLot(eventId, lotStr);
        lotCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (showNoResultsIfNotFound(resp, "lot number", lotStr)) return;
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError(formatSearchError(resp));
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showNoResults("lot number", lotStr);
            }

            @Override
            public void onFailure(Call<List<LotSearchResponse>> call, Throwable t) {
                if (call.isCanceled()) return;
                setLoading(false);
                showError("Search error: " + t.getMessage());
                adapter.setItems(new ArrayList<>());
            }
        });
    }

    private void performVinSearch() {
        if (!checkStorageAndApplyUiGuard(true)) return;

        String vinStr = safe(vinInput);
        if (vinStr.isEmpty()) {
            showError("Enter a VIN.");
            adapter.setItems(new ArrayList<>());
            return;
        }
        vinStr = vinStr.replace(" ", "").toUpperCase();
        final String searchVin = vinStr;
        cancelAll();
        setLoading(true);

        vinCall = vehicleApi.searchByVin(eventId, searchVin);
        vinCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (showNoResultsIfNotFound(resp, "VIN ending in", searchVin)) return;
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError(formatSearchError(resp));
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showNoResults("VIN ending in", searchVin);
            }

            @Override
            public void onFailure(Call<List<LotSearchResponse>> call, Throwable t) {
                if (call.isCanceled()) return;
                setLoading(false);
                showError("Search error: " + t.getMessage());
                adapter.setItems(new ArrayList<>());
            }
        });
    }

    private void performDescriptionSearch() {
        if (!checkStorageAndApplyUiGuard(true)) return;

        String terms = safe(descriptionInput);
        if (terms.isEmpty()) {
            showError("Enter a description.");
            adapter.setItems(new ArrayList<>());
            return;
        }
        if (terms.length() < 2) {
            showError("Enter at least 2 characters.");
            adapter.setItems(new ArrayList<>());
            return;
        }
        cancelAll();
        setLoading(true);

        descCall = vehicleApi.searchByTerms(eventId, terms);
        descCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (showNoResultsIfNotFound(resp, "description", terms)) return;
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError(formatSearchError(resp));
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showNoResults("description", terms);
            }

            @Override
            public void onFailure(Call<List<LotSearchResponse>> call, Throwable t) {
                if (call.isCanceled()) return;
                setLoading(false);
                showError("Search error: " + t.getMessage());
                adapter.setItems(new ArrayList<>());
            }
        });
    }

    // ---- Mapping helpers ----

    private List<VehicleDetail> mapToDetails(List<LotSearchResponse> apiRows) {
        List<VehicleDetail> mapped = new ArrayList<>();
        if (apiRows == null) return mapped;

        for (LotSearchResponse v : apiRows) {
            VehicleDetail d = new VehicleDetail();

            // Raw
            d.auctionid = v.auctionid;
            d.checkinmileage = v.checkinmileage;
            d.col = v.col;
            d.consignmentid = v.consignmentid;
            d.crmopportunityid = v.opportunityId;
            d.opportunityId = v.opportunityId;
            d.exteriorcolor = v.exteriorcolor;
            d.intakevideo = v.intakevideo;
            d.itemid = v.itemid;
            d.lotnumber = normalizeLotNumber(v.lotnumber);
            d.make = v.make;
            d.marketingdescription = v.marketingdescription;
            d.model = v.model;
            d.notes = urlDecode(v.notes);
            d.owneruncpath = v.owneruncpath;
            d.qrurl = v.qrurl;
            d.releasevideo = v.releasevideo;
            d.row = v.row;
            d.stage = v.stage;
            d.status = v.status;
            d.targettime = v.targettime;
            d.tbuncpath = v.tbuncpath;
            d.tentid = v.tentid;   // ✅ we will use this in UI
            d.vin = v.vin;
            d.year = v.year;

            // Derived
            d.title = fmtTitle(v.year, v.make, v.model);
            d.lane = fmtLane(v.col, v.row);
            d.targetTimeText = firstNonEmpty(rawExpectedStartTime(v.expectedStartTime), fmtTargetTime(v.targettime));
            d.thumbUrl = buildThumbUrl(v.tbuncpath);

            mapped.add(d);
        }
        return mapped;
    }

    private static String fmtTitle(Integer year, String make, String model) {
        String y = (year == null) ? "" : String.valueOf(year);
        String m = (make == null) ? "" : make.trim();
        String mo = (model == null) ? "" : model.trim();
        return (y + " " + m + " " + mo).trim().replaceAll("\\s+", " ");
    }

    private static String normalizeLotNumber(String value) {
        if (value == null) return "";
        String s = value.trim();
        if (s.isEmpty()) return "";
        try {
            return new java.math.BigDecimal(s).stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return s;
        }
    }

    private static String fmtLane(String col, String row) {
        String c = col == null ? "" : col.trim();
        String r = row == null ? "" : row.trim();
        if (c.isEmpty() && r.isEmpty()) return "";
        if (c.isEmpty()) return r;
        if (r.isEmpty()) return c;
        return c + "-" + r;
    }

    private static String fmtTargetTime(Long epochMs) {
        if (epochMs == null || epochMs <= 0) return "";
        java.text.DateFormat df =
                new java.text.SimpleDateFormat("EEE, MMM d h:mm a", java.util.Locale.getDefault());
        df.setTimeZone(java.util.TimeZone.getDefault());
        return df.format(new java.util.Date(epochMs));
    }

    private static String rawExpectedStartTime(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a == null || a.trim().isEmpty()) ? (b == null ? "" : b) : a;
    }

    private static String urlDecode(String s) {
        if (s == null || s.isEmpty()) return "";
        try { return java.net.URLDecoder.decode(s, "UTF-8"); }
        catch (Exception ignore) { return s; }
    }

    private static String buildThumbUrl(String tbuncpath) {
        if (tbuncpath == null || tbuncpath.trim().isEmpty()) return "";
        String t = tbuncpath.trim();
        if (t.startsWith("http")) return t;
        if (IMG_BASE.isEmpty()) return t;
        return IMG_BASE + (t.startsWith("/") ? t.substring(1) : t);
    }

    private void setupMutuallyExclusiveInputs() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus) {
                if (v == lotNumberInput) {
                    clearExcept(lotNumberInput);
                } else if (v == vinInput) {
                    clearExcept(vinInput);
                } else if (v == descriptionInput) {
                    clearExcept(descriptionInput);
                }
            }
        };

        View.OnClickListener clickListener = v -> {
            if (v == lotNumberInput) {
                clearExcept(lotNumberInput);
            } else if (v == vinInput) {
                clearExcept(vinInput);
            } else if (v == descriptionInput) {
                clearExcept(descriptionInput);
            }
        };

        lotNumberInput.setOnFocusChangeListener(focusListener);
        vinInput.setOnFocusChangeListener(focusListener);
        descriptionInput.setOnFocusChangeListener(focusListener);

        lotNumberInput.setOnClickListener(clickListener);
        vinInput.setOnClickListener(clickListener);
        descriptionInput.setOnClickListener(clickListener);
    }

    private void clearExcept(TextInputEditText keep) {
        if (keep != lotNumberInput)   lotNumberInput.setText("");
        if (keep != vinInput)         vinInput.setText("");
        if (keep != descriptionInput) descriptionInput.setText("");
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private int dp(int dps) {
        return Math.round(getResources().getDisplayMetrics().density * dps);
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelAll();
    }

    // ---------- Adapter ----------

    static class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VH> {

        interface OnItemClick { void onClick(int position, VehicleDetail row); }

        private final List<VehicleDetail> items = new ArrayList<>();
        private OnItemClick onItemClick;
        private int selectedPosition = RecyclerView.NO_POSITION;

        void setOnItemClickListener(OnItemClick l) { this.onItemClick = l; }

        void setSelectedPosition(int pos) {
            int old = selectedPosition;
            selectedPosition = pos;
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            if (pos  != RecyclerView.NO_POSITION) notifyItemChanged(pos);
        }

        static class VH extends RecyclerView.ViewHolder {
            final View root;
            final TextView tvLot;
            final TextView tvDesc;
            final TextView tvVinNumber;
            final TextView tvParking;
            final android.widget.ImageView ivThumb;

            VH(View itemView) {
                super(itemView);
                root        = itemView.findViewById(R.id.rootRow);
                tvLot       = itemView.findViewById(R.id.tvLot);
                tvDesc      = itemView.findViewById(R.id.tvDesc);
                tvVinNumber = itemView.findViewById(R.id.tvVinNumber);
                tvParking   = itemView.findViewById(R.id.tvParking);
                ivThumb     = itemView.findViewById(R.id.ivThumb);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vehicle, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            VehicleDetail row = items.get(position);

            String lotStr = normalizeLotNumber(row.lotnumber);
            holder.tvLot.setText(lotStr.isEmpty() ? "LOT -" : "LOT # " + lotStr);

            String title = (row.title == null || row.title.isEmpty())
                    ? (row.marketingdescription == null ? "" : row.marketingdescription)
                    : row.title;
            holder.tvDesc.setText(title);

            holder.tvVinNumber.setText((row.vin == null || row.vin.isEmpty()) ? "" : "VIN: " + row.vin);

            // ✅ Display exactly: "TENTID COL-ROW" (no labels)
            String tent = (row.tentid == null) ? "" : String.valueOf(row.tentid);

            String c = (row.col == null) ? "" : row.col.trim();
            String r = (row.row == null) ? "" : row.row.trim();

            String colRow = "";
            if (!c.isEmpty() && !r.isEmpty()) colRow = c + "-" + r;
            else if (!c.isEmpty()) colRow = c;
            else if (!r.isEmpty()) colRow = r;

            String display;
            if (!tent.isEmpty() && !colRow.isEmpty()) display = tent + " " + colRow;
            else if (!tent.isEmpty()) display = tent;
            else if (!colRow.isEmpty()) display = colRow;
            else display = "-";

            holder.tvParking.setText(display);

            if (row.thumbUrl != null && !row.thumbUrl.isEmpty()) {
                int radius = 16;
                com.bumptech.glide.Glide.with(holder.ivThumb.getContext())
                        .load(row.thumbUrl)
                        .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                                new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .into(holder.ivThumb);
            } else {
                holder.ivThumb.setImageResource(android.R.color.darker_gray);
            }

            holder.root.setBackgroundColor(
                    position == selectedPosition ? 0x143A8ABF : 0x00000000
            );

            holder.itemView.setOnClickListener(v -> {
                if (onItemClick != null) onItemClick.onClick(holder.getAdapterPosition(), row);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        void setItems(List<VehicleDetail> newItems) {
            items.clear();
            items.addAll(newItems);
            selectedPosition = RecyclerView.NO_POSITION;
            notifyDataSetChanged();
        }
    }

    // ---- Simple TextWatcher helper ----
    abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}









