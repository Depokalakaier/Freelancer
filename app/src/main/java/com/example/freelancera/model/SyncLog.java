package com.example.freelancera.model;

public class SyncLog {
    private String date;
    private String status;
    private String message;

    public SyncLog() {}

    public SyncLog(String date, String status, String message) {
        this.date = date;
        this.status = status;
        this.message = message;
    }

    public String getDate() { return date; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}