package com.example.gt6driver.model;

import android.os.Parcel;
import android.os.Parcelable;

public class VehicleDetail implements Parcelable {
    // Raw API fields
    public Integer auctionid;
    public Integer checkinmileage;
    public String  col;
    public Integer consignmentid;
    public String  crmopportunityid;
    public String  exteriorcolor;
    public String  intakevideo;
    public Integer itemid;

    // CHANGE: lotnumber is String because API can return fractional like "1308.1"
    public String  lotnumber;

    public String  make;
    public String  marketingdescription;
    public String  model;
    public String  notes;
    public String  owneruncpath;
    public String  qrurl;
    public String  releasevideo;
    public String  row;
    public Integer stage;
    public String  status;
    public Long    targettime;
    public String  tbuncpath;
    public String  tentid;
    public String  vin;
    public Integer year;

    // Derived / display
    public String title;          // "1981 SUZUKI GS850G"
    public String lane;           // "A-1"
    public String targetTimeText; // formatted local time
    public String thumbUrl;       // absolute URL
    public String opportunityId;

    public VehicleDetail() {}

    // ---- Parcelable ----
    protected VehicleDetail(Parcel in) {
        auctionid = (in.readByte()==0) ? null : in.readInt();
        checkinmileage = (in.readByte()==0) ? null : in.readInt();
        col = in.readString();
        consignmentid = (in.readByte()==0) ? null : in.readInt();
        crmopportunityid = in.readString();
        exteriorcolor = in.readString();
        intakevideo = in.readString();
        itemid = (in.readByte()==0) ? null : in.readInt();

        lotnumber = in.readString();  // <-- String

        make = in.readString();
        marketingdescription = in.readString();
        model = in.readString();
        notes = in.readString();
        owneruncpath = in.readString();
        qrurl = in.readString();
        releasevideo = in.readString();
        row = in.readString();
        stage = (in.readByte()==0) ? null : in.readInt();
        status = in.readString();
        targettime = (in.readByte()==0) ? null : in.readLong();
        tbuncpath = in.readString();
        tentid = in.readString();
        vin = in.readString();
        year = (in.readByte()==0) ? null : in.readInt();

        title = in.readString();
        lane = in.readString();
        targetTimeText = in.readString();
        thumbUrl = in.readString();
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        if (auctionid==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(auctionid);}
        if (checkinmileage==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(checkinmileage);}
        dest.writeString(col);
        if (consignmentid==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(consignmentid);}
        dest.writeString(crmopportunityid);
        dest.writeString(exteriorcolor);
        dest.writeString(intakevideo);
        if (itemid==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(itemid);}

        dest.writeString(lotnumber); // <-- String

        dest.writeString(make);
        dest.writeString(marketingdescription);
        dest.writeString(model);
        dest.writeString(notes);
        dest.writeString(owneruncpath);
        dest.writeString(qrurl);
        dest.writeString(releasevideo);
        dest.writeString(row);
        if (stage==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(stage);}
        dest.writeString(status);
        if (targettime==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeLong(targettime);}
        dest.writeString(tbuncpath);
        dest.writeString(tentid);
        dest.writeString(vin);
        if (year==null) dest.writeByte((byte)0); else {dest.writeByte((byte)1); dest.writeInt(year);}

        dest.writeString(title);
        dest.writeString(lane);
        dest.writeString(targetTimeText);
        dest.writeString(thumbUrl);
    }

    @Override public int describeContents() { return 0; }

    public static final Creator<VehicleDetail> CREATOR = new Creator<VehicleDetail>() {
        @Override public VehicleDetail createFromParcel(Parcel in) { return new VehicleDetail(in); }
        @Override public VehicleDetail[] newArray(int size) { return new VehicleDetail[size]; }
    };
}

