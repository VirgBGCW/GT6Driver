package com.example.gt6driver.model;

public class ConsignmentKeyPayload {
    private String status;
    private String releaseTo;
    private String responsibleParty;
    private String reason;

    public ConsignmentKeyPayload(String status, String releaseTo, String responsibleParty, String reason) {
        this.status = status;
        this.releaseTo = releaseTo;
        this.responsibleParty = responsibleParty;
        this.reason = reason;
    }

    // getters and setters if you need them (Gson can use fields directly)
    public String getStatus() { return status; }
    public String getReleaseTo() { return releaseTo; }
    public String getResponsibleParty() { return responsibleParty; }
    public String getReason() { return reason; }
}
