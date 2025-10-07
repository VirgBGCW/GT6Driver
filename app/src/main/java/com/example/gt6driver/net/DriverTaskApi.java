package com.example.gt6driver.net;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

// import your models:
// import com.example.gt6driver.model.VehicleTaskIntake;
// import com.example.gt6driver.model.ReleaseVehicleTaskPayload;
// import com.example.gt6driver.model.ReleasePayload;
import com.example.gt6driver.model.ConsignmentKeyPayload;

public interface DriverTaskApi {

    // ==== INTAKE ====
    @GET("api/v1/Driver/VehicleTask/{opportunityId}/Intake")
    Call<List<VehicleTaskIntake>> getIntake(@Path("opportunityId") String opportunityId);

    @PUT("api/v1/Driver/{opportunityId}/Intake")
    Call<Void> putIntake(
            @Path("opportunityId") String opportunityId,
            @Body VehicleTaskIntake body
    );

    // ==== RELEASE ====
    @GET("api/v1/Driver/VehicleTask/{opportunityId}/Release")
    Call<List<ReleaseVehicleTaskPayload>> getVehicleTaskRelease(
            @Path("opportunityId") String opportunityId
    );

    @PUT("api/v1/Driver/{opportunityId}/Release")
    Call<Void> releaseDriver(
            @Path("opportunityId") String opportunityId,
            @Body ReleasePayload body
    );

    // ==== CONSIGNMENT KEY (now same base host) ====
    @PUT("api/v1/Opportunity/Consignment/{opportunityId}/Key")
    Call<Void> updateConsignmentKey(
            @Path("opportunityId") String opportunityId,
            @Body ConsignmentKeyPayload body
    );
}




