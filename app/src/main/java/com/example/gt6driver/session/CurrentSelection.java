package com.example.gt6driver.session;

import androidx.annotation.Nullable;

/** Process-scoped holder for the current event + driver selection. */
public final class CurrentSelection {
    private static final CurrentSelection INSTANCE = new CurrentSelection();

    private int eventId = -1;
    private String eventName = null;

    private int driverNumber = -1;
    private String driverName = null;

    private CurrentSelection() {}

    public static CurrentSelection get() { return INSTANCE; }

    public void setEvent(int id, @Nullable String name) {
        this.eventId = id; this.eventName = name;
    }
    public void setDriver(int number, @Nullable String name) {
        this.driverNumber = number; this.driverName = name;
    }

    public int getEventId() { return eventId; }
    @Nullable public String getEventName() { return eventName; }

    public int getDriverNumber() { return driverNumber; }
    @Nullable public String getDriverName() { return driverName; }

    public boolean hasValidDriver() {
        return driverNumber > 0 && driverName != null && !driverName.trim().isEmpty();
    }
}

