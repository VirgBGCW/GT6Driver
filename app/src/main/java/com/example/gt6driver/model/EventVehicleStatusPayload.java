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

    public EventVehicleStatusPayload(int eventId, String lotNumber, EventVehicleStatus eventVehicleStatus) {
        this(eventId, lotNumber, requireStatus(eventVehicleStatus).getValue());
    }

    public int getEventId() { return eventId; }
    public String getLotNumber() { return lotNumber; }
    public int getEventVehicleStatus() { return eventVehicleStatus; }

    private static EventVehicleStatus requireStatus(EventVehicleStatus eventVehicleStatus) {
        if (eventVehicleStatus == null) {
            throw new IllegalArgumentException("eventVehicleStatus is required");
        }
        return eventVehicleStatus;
    }
}
