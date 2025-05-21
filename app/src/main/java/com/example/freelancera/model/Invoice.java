package com.example.freelancera.model;

public class Invoice {
    private String taskId;
    private String client;
    private double totalAmount;
    private double hourlyRate;
    private double hoursWorked;
    private String dueDate;
    private boolean sent;

    public Invoice(String taskId, String client, double totalAmount, double hourlyRate, double hoursWorked, String dueDate, boolean sent) {
        this.taskId = taskId;
        this.client = client;
        this.totalAmount = totalAmount;
        this.hourlyRate = hourlyRate;
        this.hoursWorked = hoursWorked;
        this.dueDate = dueDate;
        this.sent = sent;
    }

    public String getTaskId() { return taskId; }
    public String getClient() { return client; }
    public double getTotalAmount() { return totalAmount; }
    public double getHourlyRate() { return hourlyRate; }
    public double getHoursWorked() { return hoursWorked; }
    public String getDueDate() { return dueDate; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}
