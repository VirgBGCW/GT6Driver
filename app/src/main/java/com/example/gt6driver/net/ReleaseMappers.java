// app/src/main/java/com/example/gt6driver/net/ReleaseMappers.java
package com.example.gt6driver.net;

import java.util.ArrayList;
import java.util.List;

public final class ReleaseMappers {
    private ReleaseMappers() {}

    public static ReleaseVehicleTaskPayload toVehicleTask(ReleasePayload ui, String opportunityId) {
        ReleaseVehicleTaskPayload a = new ReleaseVehicleTaskPayload();
        a.releasedBy    = ui.releasedBy;
        a.opportunityId = opportunityId; // required by VehicleTask API
        a.acivityId     = ui.acivityId;  // keep if you have it

        if (ui.gateRelease != null) {
            a.gate = new ReleaseVehicleTaskPayload.Gate();
            a.gate.isGateReleaseTicket = ui.gateRelease.gateReleaseTicket;
        }

        if (ui.keyCheck != null) {
            a.keyCheck = new ReleaseVehicleTaskPayload.KeyCheck();
            a.keyCheck.hasKey       = ui.keyCheck.hasKey;
            a.keyCheck.numberOfKeys = ui.keyCheck.numberOfKeys;
            a.keyCheck.photoUrl     = ui.keyCheck.photoUrl;
        }

        if (ui.mileage != null) {
            a.mileage = new ReleaseVehicleTaskPayload.Mileage();
            a.mileage.odometer = ui.mileage.odometer;
            a.mileage.photoUrl = ui.mileage.photoUrl;
        }

        if (ui.ownerVerification != null) {
            a.ownerVerification = new ReleaseVehicleTaskPayload.OwnerVerification();
            a.ownerVerification.isOwner            = ui.ownerVerification.isOwner;
            a.ownerVerification.isReliable         = ui.ownerVerification.isReliable;
            a.ownerVerification.isTfx              = ui.ownerVerification.isTfx;
            a.ownerVerification.isOther            = ui.ownerVerification.isOther;
            a.ownerVerification.otherTransportName = ui.ownerVerification.otherTransportName;
            a.ownerVerification.licensePhotoUrl    = ui.ownerVerification.licensePhotoUrl;
        }

        if (ui.video != null) {
            a.video = new ReleaseVehicleTaskPayload.Video();
            a.video.videoUrl = ui.video.videoUrl;
        }

        if (ui.vinVerify != null) {
            a.vinVerify = new ReleaseVehicleTaskPayload.VinVerify();
            a.vinVerify.isMatched = ui.vinVerify.isMatched;
        }

        return a;
    }

    public static List<ReleaseVehicleTaskPayload> toVehicleTaskList(ReleasePayload ui, String opportunityId) {
        List<ReleaseVehicleTaskPayload> list = new ArrayList<>();
        list.add(toVehicleTask(ui, opportunityId));
        return list;
    }
}

