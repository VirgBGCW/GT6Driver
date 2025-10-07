// app/src/main/java/com/example/gt6driver/net/VehicleTaskIntake.java
package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class VehicleTaskIntake {

    @SerializedName("checkInBy")     public String checkInBy;
    @SerializedName("opportunityId") public String opportunityId;

    // NOTE: backend typo is "acivityId" — keep it so Gson maps correctly
    @SerializedName("acivityId")     public String activityId;

    @SerializedName("description")   public Description description;
    @SerializedName("keyCheck")      public KeyCheck keyCheck;
    @SerializedName("mileage")       public Mileage mileage;
    @SerializedName("quality")       public Quality quality;
    @SerializedName("video")         public VideoInfo video;
    @SerializedName("vinVerify")     public VinVerify vinVerify;

    // ---------- Nested models ----------

    public static class Description {
        @SerializedName("isCorrect")            public Boolean isCorrect;
        @SerializedName("isIncorrectMileage")   public Boolean isIncorrectMileage;
        @SerializedName("isInCorrectVin")       public Boolean isInCorrectVin;
        @SerializedName("isIncorrectSpelling")  public Boolean isIncorrectSpelling;
        @SerializedName("isIncorrectDetails")   public Boolean isIncorrectDetails;
    }

    public static class KeyCheck {
        @SerializedName("hasKey")       public Boolean hasKey;
        @SerializedName("numberOfKeys") public Integer numberOfKeys;
        @SerializedName("photoUrl")     public String photoUrl;
    }

    public static class Mileage {
        @SerializedName("odometer") public Integer odometer; // use Integer to allow null
        @SerializedName("photoUrl") public String photoUrl;
    }

    public static class Quality {
        @SerializedName("isConcerns")        public Boolean isConcerns;
        @SerializedName("isExteriorDamage")  public Boolean isExteriorDamage;
        @SerializedName("isTiresWheels")     public Boolean isTiresWheels;
        @SerializedName("isServiceLights")   public Boolean isServiceLights;
        @SerializedName("isInteriorDamage")  public Boolean isInteriorDamage;
        @SerializedName("isMechanical")      public Boolean isMechanical;
    }

    public static class VideoInfo {
        @SerializedName("videoUrl") public String videoUrl;
    }

    public static class VinVerify {
        @SerializedName("isMatched")  public Boolean isMatched;
        @SerializedName("newVin")     public String newVin;
        @SerializedName("photoUrl")   public String photoUrl;
        @SerializedName("isNotified") public Boolean isNotified;
    }
}
