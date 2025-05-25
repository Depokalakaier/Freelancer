package com.example.freelancera.models;

import java.io.Serializable;
import java.util.Date;

public class SyncHistory implements Serializable {
    private String id;
    private String taskName;
    private String action;
    private String status;
    private Date timestamp;
    private String details;

    public SyncHistory() {
        // Required empty constructor for Firestore
    }

    public SyncHistory(String taskName, String action, String status) {
        this.taskName = taskName;
        this.action = action;
        this.status = status;
        this.timestamp = new Date();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getFormattedDescription() {
        return String.format("Zadanie: %s → %s (%s)", taskName, action, status);
    }
} 