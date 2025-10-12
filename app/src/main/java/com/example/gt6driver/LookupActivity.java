package com.example.gt6driver;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gt6driver.model.VehicleDetail;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.VehicleSearchApi;
import com.example.gt6driver.net.LotSearchResponse;

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
    private VehicleSearchApi vehicleApi;
    private Call<List<LotSearchResponse>> lotCall;
    private Call<List<LotSearchResponse>> vinCall;
    private Call<List<LotSearchResponse>> descCall;
    private TextView errorText; // TextView with id=textError in activity_lookup.xml

    // If tbuncpath might be relative, set your base here (or leave empty)
    private static final String IMG_BASE = ""; // e.g., "http://auctioneer.barrett-jackson.com/"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lookup);

        // From MainActivity (event/driver context)
        eventName = getIntent().getStringExtra("eventName");
        eventId   = getIntent().getIntExtra("eventId", -1);
        driver    = getIntent().getStringExtra("driver");

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

        // Row click → highlight + go to ActionActivity, passing the WHOLE object
        adapter.setOnItemClickListener((position, vehicle) -> {
            adapter.setSelectedPosition(position);
            Intent intent = new Intent(LookupActivity.this, ActionActivity.class);

            // ✅ use the shared key for the parcelable
            intent.putExtra(Nav.EXTRA_VEHICLE, vehicle);

            intent.putExtra("eventId", eventId);
            intent.putExtra("eventName", eventName);
            intent.putExtra("driver", driver);

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
                btnSearch.setEnabled(!safe(vinInput).isEmpty()
                        || !safe(lotNumberInput).isEmpty()
                        || !safe(descriptionInput).isEmpty());
                if (errorText != null) errorText.setVisibility(View.GONE);
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
    }

    private String safe(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        btnSearch.setEnabled(!loading);
        btnSearch.setText(loading ? "Searching..." : "Search");
        if (errorText != null) errorText.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        if (errorText != null) {
            errorText.setText(msg);
            errorText.setVisibility(View.VISIBLE);
        } else {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void cancelAll() {
        if (vinCall  != null && !vinCall.isCanceled())  { vinCall.cancel();  vinCall = null; }
        if (lotCall  != null && !lotCall.isCanceled())  { lotCall.cancel();  lotCall = null; }
        if (descCall != null && !descCall.isCanceled()) { descCall.cancel(); descCall = null; }
    }

    private void performLotSearch() {
        String lotStr = safe(lotNumberInput);
        if (lotStr.isEmpty()) {
            showError("Enter a Lot number.");
            adapter.setItems(new ArrayList<>());
            return;
        }

// Allow integer or decimal (e.g., 300 or 300.1)
        if (!lotStr.matches("\\d+(?:\\.\\d+)?")) {
            showError("Lot must be a number (e.g., 300 or 300.1).");
            adapter.setItems(new ArrayList<>());
            return;
        }

        cancelAll();
        setLoading(true);

        String absoluteUrl =
                "http://mobiletools.corp.barrett-jackson.com/mn-bj-api/Driver"
                        + "?auction=" + eventId
                        + "&lot=" + lotStr;

        lotCall = vehicleApi.searchByLotAbsolute(absoluteUrl);
        lotCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError("Search failed: HTTP " + resp.code());
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showError("No results.");
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
        String vinStr = safe(vinInput);
        if (vinStr.isEmpty()) {
            showError("Enter a VIN.");
            adapter.setItems(new ArrayList<>());
            return;
        }
        vinStr = vinStr.replace(" ", "").toUpperCase();
        String encodedVin;
        try {
            encodedVin = java.net.URLEncoder.encode(vinStr, "UTF-8");
        } catch (Exception e) {
            showError("Encoding error.");
            return;
        }
        cancelAll();
        setLoading(true);

        String absoluteUrl =
                "http://mobiletools.corp.barrett-jackson.com/mn-bj-api/Driver"
                        + "?auction=" + eventId
                        + "&vin=" + encodedVin;

        vinCall = vehicleApi.searchByVinAbsolute(absoluteUrl);
        vinCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError("Search failed: HTTP " + resp.code());
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showError("No results.");
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

        String encoded;
        try {
            encoded = java.net.URLEncoder.encode(terms, "UTF-8");
        } catch (Exception e) {
            showError("Encoding error.");
            setLoading(false);
            return;
        }

        String absoluteUrl =
                "http://mobiletools.corp.barrett-jackson.com/mn-bj-api/Driver"
                        + "?auction=" + eventId
                        + "&terms=" + encoded;

        descCall = vehicleApi.searchByTermsAbsolute(absoluteUrl);
        descCall.enqueue(new Callback<List<LotSearchResponse>>() {
            @Override
            public void onResponse(Call<List<LotSearchResponse>> call, Response<List<LotSearchResponse>> resp) {
                setLoading(false);
                if (!resp.isSuccessful() || resp.body() == null) {
                    showError("Search failed: HTTP " + resp.code());
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                adapter.setItems(mapToDetails(resp.body()));
                if (adapter.getItemCount() == 0) showError("No results.");
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
            d.opportunityId = v.opportunityId;         // ✅ new unified name
            d.exteriorcolor = v.exteriorcolor;
            d.intakevideo = v.intakevideo;
            d.itemid = v.itemid;
            d.lotnumber = v.lotnumber;
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
            d.tentid = v.tentid;
            d.vin = v.vin;
            d.year = v.year;

            // Derived
            d.title = fmtTitle(v.year, v.make, v.model);
            d.lane = fmtLane(v.col, v.row);
            d.targetTimeText = fmtTargetTime(v.targettime);
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

    @Override
    protected void onStop() {
        super.onStop();
        cancelAll();
    }

    // ---------- Adapter ----------

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
            final android.widget.ImageView ivThumb;

            VH(View itemView) {
                super(itemView);
                root        = itemView.findViewById(R.id.rootRow);
                tvLot       = itemView.findViewById(R.id.tvLot);
                tvDesc      = itemView.findViewById(R.id.tvDesc);
                tvVinNumber = itemView.findViewById(R.id.tvVinNumber);
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

            // lotnumber is String
            String lotStr = (row.lotnumber == null) ? "" : row.lotnumber;
            holder.tvLot.setText(lotStr.isEmpty() ? "LOT —" : "LOT # " + lotStr);

            String title = (row.title == null || row.title.isEmpty())
                    ? (row.marketingdescription == null ? "" : row.marketingdescription)
                    : row.title;
            holder.tvDesc.setText(title);

            holder.tvVinNumber.setText((row.vin == null || row.vin.isEmpty()) ? "" : "VIN: " + row.vin);

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





