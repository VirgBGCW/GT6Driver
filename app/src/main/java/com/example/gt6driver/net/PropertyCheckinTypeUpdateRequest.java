package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class PropertyCheckinTypeUpdateRequest {

    @SerializedName("propertyItemCheckinType")
    public final String propertyItemCheckinType;

    public PropertyCheckinTypeUpdateRequest(String propertyItemCheckinType) {
        this.propertyItemCheckinType = propertyItemCheckinType;
    }
}