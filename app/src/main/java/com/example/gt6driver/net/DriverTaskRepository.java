// app/src/main/java/com/example/gt6driver/net/DriverTaskRepository.java
package com.example.gt6driver.net;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DriverTaskRepository {

    private static final String TAG = "DriverTaskRepo";

    private final DriverTaskApi api;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public DriverTaskRepository() {
        this.api = ApiClient.getMemberApi().create(DriverTaskApi.class);
    }

    // -------------------- CALLBACK TYPES --------------------
    public interface SaveCallback {
        void onSaved();
        void onError(Throwable t);
        void onHttpError(int code, String message);
    }

    public interface ReleaseCallback {
        void onSuccess(ReleasePayload payload); // mapped to your UI model
        void onError(Throwable t);
        void onHttpError(int code, String message);
    }

    public interface IntakeCallback {
        void onSuccess(VehicleTaskIntake intake); // returns first (or null)
        void onError(Throwable t);
        void onHttpError(int code, String message);
    }

    // ========================================================
    // ======================  INTAKE  ========================
    // ========================================================

    /** PUT to /api/v1/Driver/{opportunityId}/Intake */
    public void saveIntake(String opportunityId, VehicleTaskIntake body, SaveCallback cb) {
        Call<Void> call = api.putIntake(opportunityId, body);

        // Debug URL + JSON
        try {
            Log.d(TAG, "PUT Intake URL: " + call.request().url());
            Log.d(TAG, "Intake Body:\n" + gson.toJson(body));
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (!resp.isSuccessful()) {
                    cb.onHttpError(resp.code(), readErrBody(resp));
                } else {
                    cb.onSaved();
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError(t);
            }
        });
    }

    /** GET from /api/v1/Driver/VehicleTask/{opportunityId}/Intake (returns array; we surface first) */
    public void fetchIntake(String opportunityId, IntakeCallback cb) {
        api.getIntake(opportunityId).enqueue(new Callback<List<VehicleTaskIntake>>() {
            @Override public void onResponse(Call<List<VehicleTaskIntake>> call,
                                             Response<List<VehicleTaskIntake>> resp) {
                if (!resp.isSuccessful()) {
                    cb.onHttpError(resp.code(), readErrBody(resp));
                    return;
                }
                List<VehicleTaskIntake> list = resp.body();
                VehicleTaskIntake first = (list != null && !list.isEmpty()) ? list.get(0) : null;

                try {
                    Log.d(TAG, "GET Intake (first) mapped to UI:\n" + gson.toJson(first));
                } catch (Throwable ignored) {}

                cb.onSuccess(first);
            }

            @Override public void onFailure(Call<List<VehicleTaskIntake>> call, Throwable t) {
                cb.onError(t);
            }
        });
    }

    // ========================================================
    // =====================  RELEASE  ========================
    // ========================================================

    /**
     * Public "VehicleTask" save method kept for compatibility with your Activity.
     * It simply delegates to the correct **Driver** single-object PUT.
     */
    public void releaseVehicleTask(String opportunityId, ReleasePayload uiModel, SaveCallback cb) {
        // Delegate to the only PUT your API exposes:
        releaseDriver(opportunityId, uiModel, cb);
    }

    /**
     * PUT to /api/v1/Driver/{opportunityId}/Release (single object shape)
     * Uses ReleasePayload with gateRelease.gateReleaseTicket (correct schema).
     */
    public void releaseDriver(String opportunityId, ReleasePayload body, SaveCallback cb) {
        Call<Void> call = api.releaseDriver(opportunityId, body);

        try {
            Log.d(TAG, "PUT Driver Release URL: " + call.request().url());
            Log.d(TAG, "Driver Release Body:\n" + gson.toJson(body));
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                if (!resp.isSuccessful()) {
                    cb.onHttpError(resp.code(), readErrBody(resp));
                } else {
                    cb.onSaved();
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError(t);
            }
        });
    }

    /**
     * GET from /api/v1/Driver/VehicleTask/{opportunityId}/Release (ARRAY)
     * We map the first element to your UI model (ReleasePayload).
     */
    public void fetchRelease(String opportunityId, ReleaseCallback cb) {
        api.getVehicleTaskRelease(opportunityId).enqueue(new Callback<List<ReleaseVehicleTaskPayload>>() {
            @Override public void onResponse(Call<List<ReleaseVehicleTaskPayload>> call,
                                             Response<List<ReleaseVehicleTaskPayload>> resp) {
                if (!resp.isSuccessful()) {
                    cb.onHttpError(resp.code(), readErrBody(resp));
                    return;
                }
                List<ReleaseVehicleTaskPayload> list = resp.body();
                ReleasePayload mapped = (list != null && !list.isEmpty())
                        ? fromVehicleTask(list.get(0))
                        : null;

                // Debug what we got
                try {
                    Log.d(TAG, "GET VehicleTask Release mapped to UI:\n" + gson.toJson(mapped));
                } catch (Throwable ignored) {}

                cb.onSuccess(mapped);
            }

            @Override public void onFailure(Call<List<ReleaseVehicleTaskPayload>> call, Throwable t) {
                cb.onError(t);
            }
        });
    }

    // ========================================================
    // =====================  HELPERS  ========================
    // ========================================================

    private static String readErrBody(Response<?> resp) {
        try {
            return resp.errorBody() != null ? resp.errorBody().string() : null;
        } catch (IOException e) {
            return null;
        }
    }

    // ----- Mappers (GET array → UI single). We DO NOT use these for PUT. -----

    private static ReleasePayload fromVehicleTask(ReleaseVehicleTaskPayload a) {
        if (a == null) return null;

        ReleasePayload ui = new ReleasePayload();
        ui.releasedBy    = a.releasedBy;
        ui.opportunityId = a.opportunityId;
        ui.acivityId     = a.acivityId; // keep if backend returns it; we just don't send it on PUT

        if (a.gate != null) {
            ui.gateRelease = new ReleasePayload.GateRelease();
            ui.gateRelease.gateReleaseTicket = a.gate.isGateReleaseTicket;
        }

        if (a.keyCheck != null) {
            ui.keyCheck = new ReleasePayload.KeyCheck();
            ui.keyCheck.hasKey       = a.keyCheck.hasKey;
            ui.keyCheck.numberOfKeys = a.keyCheck.numberOfKeys;
            ui.keyCheck.photoUrl     = a.keyCheck.photoUrl;
        }

        if (a.mileage != null) {
            ui.mileage = new ReleasePayload.Mileage();
            ui.mileage.odometer = a.mileage.odometer;
            ui.mileage.photoUrl = a.mileage.photoUrl;
        }

        if (a.ownerVerification != null) {
            ui.ownerVerification = new ReleasePayload.OwnerVerification();
            ui.ownerVerification.isOwner            = a.ownerVerification.isOwner;
            ui.ownerVerification.isReliable         = a.ownerVerification.isReliable;
            ui.ownerVerification.isTfx              = a.ownerVerification.isTfx;
            ui.ownerVerification.isOther            = a.ownerVerification.isOther;
            ui.ownerVerification.otherTransportName = a.ownerVerification.otherTransportName;
            ui.ownerVerification.licensePhotoUrl    = a.ownerVerification.licensePhotoUrl;
        }

        if (a.video != null) {
            ui.video = new ReleasePayload.Video();
            ui.video.videoUrl = a.video.videoUrl;
        }

        if (a.vinVerify != null) {
            ui.vinVerify = new ReleasePayload.VinVerify();
            ui.vinVerify.isMatched = a.vinVerify.isMatched;
        }

        return ui;
    }

    // Note: We intentionally removed any "toVehicleTask(...)" mapping for PUT,
    // because the only PUT you have is the Driver single-object endpoint.

    // ------------------------------------------------------------------------
}






