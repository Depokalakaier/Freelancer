package com.example.freelancera.model;

public class Invoice {
    private String taskId;
    private String client;
    private double totalAmount;
    private double hourlyRate;
    private double hoursWorked;
    private String dueDate;
    private boolean sent;

    public Invoice() {}

    public Invoice(String taskId, String client, double totalAmount, double hourlyRate, double hoursWorked, String dueDate, boolean sent) {
        this.taskId = taskId;
        this.client = client;
        this.totalAmount = totalAmount;
        this.hourlyRate = hourlyRate;
        this.hoursWorked = hoursWorked;
        this.dueDate = dueDate;
        this.sent = sent;
    }

    // gettery i settery
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
    public double getHoursWorked() { return hoursWorked; }
    public void setHoursWorked(double hoursWorked) { this.hoursWorked = hoursWorked; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}