package com.example.gt6driver.net;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * Barrett-Jackson Driver search endpoints
 * Base URL should be: http://mobiletools.corp.barrett-jackson.com/
 */
public interface VehicleSearchApi {

    // ---------- Preferred: query-param style (safe encoding, easier to read) ----------
    // Example full URL: http://mobiletools.corp.barrett-jackson.com/mn-bj-api/Driver?auction=844&lot=100
    @GET
    Call<List<LotSearchResponse>> searchByLotAbsolute(@Url String absoluteUrl);

    @GET
    Call<List<LotSearchResponse>> searchByVinAbsolute(@Url String absoluteUrl);

    @GET
    Call<List<LotSearchResponse>> searchByTermsAbsolute(@Url String absoluteUrl);
}

