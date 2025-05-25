package com.example.freelancera.models;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.firestore.DocumentId;
import java.util.Date;

public class Invoice implements Parcelable {
    @DocumentId
    private String id;
    private String taskId;
    private String clientName;
    private String taskName;
    private double hours; // Będzie uzupełniane gdy połączymy z Clockify
    private double ratePerHour;
    private double totalAmount;
    private Date issueDate;
    private Date dueDate;
    private boolean isPaid;
    private String status; // "DRAFT", "SENT", "PAID"

    public Invoice() {
        // Required empty constructor for Firestore
    }

    public Invoice(String taskId, String clientName, String taskName, double ratePerHour) {
        this.taskId = taskId;
        this.clientName = clientName;
        this.taskName = taskName;
        this.ratePerHour = ratePerHour;
        this.hours = 0.0; // Domyślnie 0 - będzie aktualizowane z Clockify
        this.totalAmount = 0.0; // Będzie przeliczane po otrzymaniu godzin
        this.issueDate = new Date();
        this.dueDate = new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000); // +7 dni
        this.isPaid = false;
        this.status = "DRAFT";
    }

    protected Invoice(Parcel in) {
        id = in.readString();
        taskId = in.readString();
        clientName = in.readString();
        taskName = in.readString();
        hours = in.readDouble();
        ratePerHour = in.readDouble();
        totalAmount = in.readDouble();
        issueDate = new Date(in.readLong());
        dueDate = new Date(in.readLong());
        isPaid = in.readByte() != 0;
        status = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(taskId);
        dest.writeString(clientName);
        dest.writeString(taskName);
        dest.writeDouble(hours);
        dest.writeDouble(ratePerHour);
        dest.writeDouble(totalAmount);
        dest.writeLong(issueDate.getTime());
        dest.writeLong(dueDate.getTime());
        dest.writeByte((byte) (isPaid ? 1 : 0));
        dest.writeString(status);
    }

    public static final Creator<Invoice> CREATOR = new Creator<Invoice>() {
        @Override
        public Invoice createFromParcel(Parcel in) {
            return new Invoice(in);
        }

        @Override
        public Invoice[] newArray(int size) {
            return new Invoice[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    // Gettery i settery
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    
    public double getHours() { return hours; }
    public void setHours(double hours) { 
        this.hours = hours;
        recalculateTotal();
    }
    
    public double getRatePerHour() { return ratePerHour; }
    public void setRatePerHour(double ratePerHour) { 
        this.ratePerHour = ratePerHour;
        recalculateTotal();
    }
    
    public double getTotalAmount() { return totalAmount; }
    
    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }
    
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    
    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { 
        isPaid = paid;
        status = paid ? "PAID" : "DRAFT";
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    private void recalculateTotal() {
        this.totalAmount = this.hours * this.ratePerHour;
    }
} 