package com.example.gt6driver.model;

import com.google.gson.annotations.SerializedName;

public class PropertyItem {
    @SerializedName(value = "propertyId", alternate = {
            "id",
            "propertyItemId",
            "opportunityPropertyId",
            "opportunityPropertyItemId",
            "consignmentPropertyId"
    })
    public Object propertyId;

    public String propertyType;
    public String propertyDescription;
    public String notes;
    public String releaseTo;
    public String releaseReason;
    public Integer quantity;
    public String space;
    public String currentOwner;

    @SerializedName(value = "checkInType", alternate = {"propertyItemCheckinType"})
    public String checkInType;

    @SerializedName("isLeftInCar")
    public Boolean isLeftInCar;

    public String propertyReceivedBy;
    public String status;

    public transient boolean isUpdatingCheckInType;

    public String getPropertyIdForApi() {
        if (propertyId == null) return "";

        if (propertyId instanceof Number) {
            return String.valueOf(((Number) propertyId).longValue());
        }

        String value = String.valueOf(propertyId).trim();
        return value.equalsIgnoreCase("null") ? "" : value;
    }
}
