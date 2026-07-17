package com.example.gt6driver.net;

import com.example.gt6driver.model.MechanicDriverDto;
import com.google.gson.JsonElement;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface LookupService {

    // ===================== AUCTION EVENTS =====================
    // /api/v1/Lookup/LookupCodes?lookupTable=Auction&current=false
    @GET("api/v1/Lookup/LookupCodes")
    Call<JsonElement> getAuctionEvents(
            @Query("lookupTable") String lookupTable,
            @Query("current") boolean current
    );

    // ===================== GENERIC LOOKUP CODES =====================
    // /api/v1/Lookup/LookupCodes?lookupTable=PropertyType
    @GET("api/v1/Lookup/LookupCodes")
    Call<JsonElement> getLookupCodes(
            @Query("lookupTable") String lookupTable
    );

    // ===================== DRIVER / PROPERTY / KEY / MECHANIC =====================
    // /api/v1/Lookup/Driver/Property/Key/Mechanic/{userType}?eventId=ID
    @GET("api/v1/Lookup/Driver/Property/Key/Mechanic/{userType}")
    Call<List<MechanicDriverDto>> getMechanicDrivers(
            @Path("userType") String userType,
            @Query("eventId") int eventId
    );
}
