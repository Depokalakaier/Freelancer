package com.example.freelancera.model;

public class SyncHistory {
    private String description;
    private String date;
    private String status; // np. "ok", "błąd"

    public SyncHistory(String description, String date, String status) {
        this.description = description;
        this.date = date;
        this.status = status;
    }

    public String getDescription() { return description; }
    public String getDate() { return date; }
    public String getStatus() { return status; }
}
