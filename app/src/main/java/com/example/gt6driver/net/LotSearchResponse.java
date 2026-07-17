package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class LotSearchResponse {
    @SerializedName(value = "auctionid", alternate = {"auctionId"})
    public Integer auctionid;

    @SerializedName(value = "checkinmileage", alternate = {"checkInMileage"})
    public Integer checkinmileage;

    @SerializedName("col")
    public String col;

    @SerializedName(value = "consignmentid", alternate = {"consignmentId"})
    public Integer consignmentid;

    @SerializedName(value = "crmopportunityid", alternate = {"opportunityId"})
    public String opportunityId;

    @SerializedName(value = "exteriorcolor", alternate = {"exteriorColor"})
    public String exteriorcolor;

    @SerializedName(value = "intakevideo", alternate = {"intakeVideo"})
    public String intakevideo;

    @SerializedName(value = "itemid", alternate = {"itemId"})
    public Integer itemid;

    // Lot numbers can be fractional, so keep this as String.
    @SerializedName(value = "lotnumber", alternate = {"lotNumber"})
    public String lotnumber;

    @SerializedName("make")
    public String make;

    @SerializedName(value = "marketingdescription", alternate = {"marketingDescription"})
    public String marketingdescription;

    @SerializedName("model")
    public String model;

    @SerializedName("notes")
    public String notes;

    @SerializedName(value = "owneruncpath", alternate = {"ownerUncPath"})
    public String owneruncpath;

    @SerializedName(value = "qrurl", alternate = {"qrUrl"})
    public String qrurl;

    @SerializedName(value = "releasevideo", alternate = {"releaseVideo"})
    public String releasevideo;

    @SerializedName("row")
    public String row;

    @SerializedName("stage")
    public Integer stage;

    @SerializedName("status")
    public String status;

    @SerializedName(value = "targettime", alternate = {"targetTime"})
    public Long targettime;

    @SerializedName("expectedStartTime")
    public String expectedStartTime;

    @SerializedName(value = "tbuncpath", alternate = {"tbUncPath"})
    public String tbuncpath;

    @SerializedName(value = "tentid", alternate = {"tentId"})
    public String tentid;

    @SerializedName("vin")
    public String vin;

    @SerializedName("year")
    public Integer year;
}
