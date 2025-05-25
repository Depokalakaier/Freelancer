package com.example.freelancera.models;

import java.io.Serializable;
import java.util.Date;
import java.util.Calendar;

public class Invoice implements Serializable {
    private String id;
    private String taskId;
    private String taskName;
    private String clientName;
    private String clientEmail;
    private String clientAddress;
    private double amount;
    private double taxRate;
    private Date issueDate;
    private Date dueDate;
    private String status; // DRAFT, SENT, PAID
    private String notes;

    public Invoice() {
        // Required empty constructor for Firestore
    }

    public Invoice(Task task) {
        this.taskId = task.getId();
        this.taskName = task.getName();
        this.clientName = task.getClient();
        this.amount = task.calculateTotal();
        this.taxRate = 0.23; // 23% VAT domyślnie
        this.issueDate = new Date();
        this.dueDate = new Date(System.currentTimeMillis() + (14 * 24 * 60 * 60 * 1000L)); // +14 dni
        this.status = "DRAFT";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public String getClientAddress() { return clientAddress; }
    public void setClientAddress(String clientAddress) { this.clientAddress = clientAddress; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }

    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // Helper methods
    public double getTaxAmount() {
        return amount * taxRate;
    }

    public double getTotalAmount() {
        return amount + getTaxAmount();
    }

    public String getFormattedAmount() {
        return String.format("%.2f zł", amount);
    }

    public String getFormattedTaxAmount() {
        return String.format("%.2f zł", getTaxAmount());
    }

    public String getFormattedTotalAmount() {
        return String.format("%.2f zł", getTotalAmount());
    }

    public String getFormattedInvoiceNumber() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(issueDate);
        return String.format("FV/%s/%d", id, cal.get(Calendar.YEAR));
    }
} 