package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class PropertyCreateRequest {

    @SerializedName("propertyType")
    public String propertyType;

    @SerializedName("property")
    public String property;

    @SerializedName("description")
    public String description;

    public PropertyCreateRequest(String propertyType, String property, String description) {
        this.propertyType = propertyType;
        this.property = property;
        this.description = description;
    }
}

