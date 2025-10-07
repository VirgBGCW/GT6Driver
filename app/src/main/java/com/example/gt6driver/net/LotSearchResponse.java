package com.example.gt6driver.net;

import com.google.gson.annotations.SerializedName;

public class LotSearchResponse {
    @SerializedName("auctionid")            public Integer auctionid;
    @SerializedName("checkinmileage")       public Integer checkinmileage;
    @SerializedName("col")                  public String col;
    @SerializedName("consignmentid")        public Integer consignmentid;

    @SerializedName("crmopportunityid")
    public String opportunityId;


    @SerializedName("exteriorcolor")        public String exteriorcolor;
    @SerializedName("intakevideo")          public String intakevideo;
    @SerializedName("itemid")               public Integer itemid;

    // IMPORTANT: lotnumber can be 1308.1 → use String
    @SerializedName("lotnumber")            public String lotnumber;

    @SerializedName("make")                 public String make;
    @SerializedName("marketingdescription") public String marketingdescription;
    @SerializedName("model")                public String model;
    @SerializedName("notes")                public String notes;

    @SerializedName("owneruncpath")         public String owneruncpath;
    @SerializedName("qrurl")                public String qrurl;
    @SerializedName("releasevideo")         public String releasevideo;
    @SerializedName("row")                  public String row;
    @SerializedName("stage")                public Integer stage;
    @SerializedName("status")               public String status;
    @SerializedName("targettime")           public Long targettime;

    @SerializedName("tbuncpath")            public String tbuncpath;   // thumbnail URL
    @SerializedName("tentid")               public String tentid;

    @SerializedName("vin")                  public String vin;
    @SerializedName("year")                 public Integer year;
}

