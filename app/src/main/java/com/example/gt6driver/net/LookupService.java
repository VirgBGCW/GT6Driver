package com.example.gt6driver.net;

import com.example.gt6driver.model.MechanicDriverDto;
import com.google.gson.JsonElement;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LookupService {

    // ===================== AUCTION EVENTS =====================
    // /api/v1/Lookup/LookupCodes?lookupTable=Auction&current=false
    @GET("api/v1/Lookup/LookupCodes")
    Call<JsonElement> getAuctionEvents(
            @Query("lookupTable") String lookupTable,
            @Query("current") boolean current
    );

    // ===================== MECHANIC DRIVERS =====================
    // /api/v1/Lookup/Driver/Property/Key/Mechanic/Driver?eventId=ID
    @GET("api/v1/Lookup/Driver/Property/Key/Mechanic/Driver")
    Call<List<MechanicDriverDto>> getMechanicDrivers(
            @Query("eventId") int eventId
    );
}

