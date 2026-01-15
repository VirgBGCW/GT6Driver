package com.example.gt6driver.model;

public class EventVehicleStatusPayload {
    private int eventId;
    private String lotNumber;
    private int eventVehicleStatus;

    public EventVehicleStatusPayload(int eventId, String lotNumber, int eventVehicleStatus) {
        this.eventId = eventId;
        this.lotNumber = lotNumber;
        this.eventVehicleStatus = eventVehicleStatus;
    }

    public int getEventId() { return eventId; }
    public String getLotNumber() { return lotNumber; }
    public int getEventVehicleStatus() { return eventVehicleStatus; }
}
