package com.example.freelancera.models;

import com.google.firebase.firestore.DocumentId;
import java.util.Date;

public class SyncHistory {
    @DocumentId
    private String id;
    private String taskId;
    private String taskName;
    private String action; // np. "INVOICE_CREATED", "REMINDER_ADDED"
    private String details;
    private Date timestamp;
    private boolean success;
    private String errorMessage;

    public SyncHistory() {
        // Required empty constructor for Firestore
    }

    public SyncHistory(String taskId, String taskName, String action, String details) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.action = action;
        this.details = details;
        this.timestamp = new Date();
        this.success = true;
        this.errorMessage = null;
    }

    public SyncHistory(String taskId, String taskName, String action, String details, boolean success, String errorMessage) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.action = action;
        this.details = details;
        this.timestamp = new Date();
        this.success = success;
        this.errorMessage = errorMessage;
    }

    // Gettery i settery
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getFormattedMessage() {
        if (success) {
            return String.format("%s - %s: %s", taskName, action, details);
        } else {
            return String.format("%s - %s: BŁĄD - %s", taskName, action, errorMessage);
        }
    }
} 