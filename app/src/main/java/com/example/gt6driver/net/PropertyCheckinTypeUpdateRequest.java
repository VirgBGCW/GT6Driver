package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class PropertyCheckinTypeUpdateRequest {

    @SerializedName("propertyItemCheckinType")
    public final String propertyItemCheckinType;

    @SerializedName("isLeftInCar")
    public final boolean isLeftInCar;

    public PropertyCheckinTypeUpdateRequest(String propertyItemCheckinType) {
        this(propertyItemCheckinType, false);
    }

    public PropertyCheckinTypeUpdateRequest(String propertyItemCheckinType, boolean isLeftInCar) {
        this.propertyItemCheckinType = propertyItemCheckinType;
        this.isLeftInCar = isLeftInCar;
    }
}
