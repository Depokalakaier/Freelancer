package com.example.freelancera.model;

public class WorkTime {
    private String taskId;
    private int hours;
    private int minutes;

    public WorkTime(String taskId, int hours, int minutes) {
        this.taskId = taskId;
        this.hours = hours;
        this.minutes = minutes;
    }

    public String getTaskId() { return taskId; }
    public int getHours() { return hours; }
    public int getMinutes() { return minutes; }
    public double getTotalHours() { return hours + minutes / 60.0; }
}