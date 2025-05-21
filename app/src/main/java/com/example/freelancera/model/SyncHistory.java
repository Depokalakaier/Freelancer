package com.example.freelancera.model;

public class SyncHistory {
    private String description;
    private String date;
    private String status;

    public SyncHistory() {}

    public SyncHistory(String description, String date, String status) {
        this.description = description;
        this.date = date;
        this.status = status;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}