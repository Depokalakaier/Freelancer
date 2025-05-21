package com.example.freelancera.model;

public class WorkTime {
    private String taskId;
    private int hours;
    private int minutes;

    public WorkTime() {}

    public WorkTime(String taskId, int hours, int minutes) {
        this.taskId = taskId;
        this.hours = hours;
        this.minutes = minutes;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public int getHours() { return hours; }
    public void setHours(int hours) { this.hours = hours; }

    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }

    public double getTotalHours() { return hours + minutes / 60.0; }
}