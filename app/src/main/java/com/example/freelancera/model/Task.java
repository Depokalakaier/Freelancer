package com.example.freelancera.model;

public class Task {
    private String id;
    private String title;
    private String description;
    private String status; // "Nowe", "W trakcie", "Ukończone"
    private String client;
    private String completedDate;

    public Task(String id, String title, String description, String status, String client, String completedDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.client = client;
        this.completedDate = completedDate;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getClient() { return client; }
    public String getCompletedDate() { return completedDate; }

    public void setStatus(String status) { this.status = status; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }
}
