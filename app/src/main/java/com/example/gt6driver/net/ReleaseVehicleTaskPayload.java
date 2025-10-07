// app/src/main/java/com/example/gt6driver/net/ReleaseVehicleTaskPayload.java
package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class ReleaseVehicleTaskPayload {
    @SerializedName("releasedBy")    public String releasedBy;
    @SerializedName("opportunityId") public String opportunityId; // required by this API
    @SerializedName("acivityId")     public String acivityId;     // typo is intentional

    @SerializedName("gate")          public Gate gate;
    @SerializedName("keyCheck")      public KeyCheck keyCheck;
    @SerializedName("mileage")       public Mileage mileage;
    @SerializedName("ownerVerification") public OwnerVerification ownerVerification;
    @SerializedName("video")         public Video video;
    @SerializedName("vinVerify")     public VinVerify vinVerify;

    public static class Gate {
        @SerializedName("isGateReleaseTicket")
        public Boolean isGateReleaseTicket;
    }
    public static class KeyCheck {
        @SerializedName("hasKey") public Boolean hasKey;
        @SerializedName("numberOfKeys") public Integer numberOfKeys;
        @SerializedName("photoUrl") public String photoUrl;
    }
    public static class Mileage {
        @SerializedName("odometer") public Integer odometer;
        @SerializedName("photoUrl") public String photoUrl;
    }
    public static class OwnerVerification {
        @SerializedName("isOwner") public Boolean isOwner;
        @SerializedName("isReliable") public Boolean isReliable;
        @SerializedName("isTfx") public Boolean isTfx;
        @SerializedName("isOther") public Boolean isOther;
        @SerializedName("otherTransportName") public String otherTransportName;
        @SerializedName("licensePhotoUrl") public String licensePhotoUrl;
    }
    public static class Video {
        @SerializedName("videoUrl") public String videoUrl;
    }
    public static class VinVerify {
        @SerializedName("isMatched") public Boolean isMatched;
    }
}
