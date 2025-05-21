package com.example.freelancera.model;

public class Task {
    private String id;
    private String title;
    private String description;
    private String status;
    private String client;
    private String completedDate;

    public Task() {}

    public Task(String id, String title, String description, String status, String client, String completedDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.client = client;
        this.completedDate = completedDate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public String getCompletedDate() { return completedDate; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }
}