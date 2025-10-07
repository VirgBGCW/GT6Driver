package com.example.gt6driver.net;

import com.google.gson.JsonElement;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LookupService {

    // We’ll parse dynamically because we don’t control the exact schema.
    // Endpoint (no trailing quote):
    // /api/v1/Lookup/LookupCodes?lookupTable=Auction&current=false
    @GET("api/v1/Lookup/LookupCodes")
    Call<JsonElement> getAuctionEvents(
            @Query("lookupTable") String lookupTable,
            @Query("current") boolean current
    );
}

