package com.example.gt6driver.net;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Barrett-Jackson Driver opportunity search endpoints.
 * Uses the current ApiClient base URL (PRODUCTION/UAT).
 */
public interface VehicleSearchApi {

    @GET("api/v1/Driver/Opportunities")
    Call<List<LotSearchResponse>> searchByLot(
            @Query("auction") int auctionId,
            @Query("lot") String lot
    );

    @GET("api/v1/Driver/Opportunities")
    Call<List<LotSearchResponse>> searchByVin(
            @Query("auction") int auctionId,
            @Query("vin") String vin
    );

    @GET("api/v1/Driver/Opportunities")
    Call<List<LotSearchResponse>> searchByTerms(
            @Query("auction") int auctionId,
            @Query("terms") String terms
    );
}
