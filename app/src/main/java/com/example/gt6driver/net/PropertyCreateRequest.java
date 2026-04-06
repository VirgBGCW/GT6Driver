package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class PropertyCreateRequest {

    @SerializedName("propertyItemTypeId")
    public int propertyItemTypeId;

    @SerializedName("propertyDescription")
    public String propertyDescription;

    @SerializedName("notes")
    public String notes;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("isLeftInCar")
    public boolean isLeftInCar;

    public PropertyCreateRequest(int propertyItemTypeId,
                                 String propertyDescription,
                                 String notes,
                                 int quantity,
                                 boolean isLeftInCar) {
        this.propertyItemTypeId = propertyItemTypeId;
        this.propertyDescription = propertyDescription;
        this.notes = notes;
        this.quantity = quantity;
        this.isLeftInCar = isLeftInCar;
    }
}