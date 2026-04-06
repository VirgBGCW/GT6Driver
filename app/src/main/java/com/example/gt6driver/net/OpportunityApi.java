package com.example.gt6driver.net;

import com.example.gt6driver.model.PropertyItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface OpportunityApi {

    @GET("api/v1/Opportunity/{opportunityId}/Property")
    Call<List<PropertyItem>> getOpportunityProperty(@Path("opportunityId") String opportunityId);

    @POST("api/v1/Opportunity/Consignment/{opportunityId}/Property")
    Call<Void> addOpportunityProperty(
            @Path("opportunityId") String opportunityId,
            @Body PropertyCreateRequest body
    );
}