package com.example.freelancera.model;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;

public class Invoice implements Parcelable {
    private String taskId;
    private String title;
    private String client;
    private double totalAmount;
    private double hourlyRate;
    private double hoursWorked;
    private String dueDate;
    private String issueDate;
    private boolean sent;
    @SerializedName("paid")
    private boolean isPaid;

    public Invoice() {}

    public Invoice(String taskId, String title, String client, double totalAmount, double hourlyRate, double hoursWorked, String dueDate, String issueDate, boolean sent, boolean isPaid) {
        this.taskId = taskId;
        this.title = title;
        this.client = client;
        this.totalAmount = totalAmount;
        this.hourlyRate = hourlyRate;
        this.hoursWorked = hoursWorked;
        this.dueDate = dueDate;
        this.issueDate = issueDate;
        this.sent = sent;
        this.isPaid = isPaid;
    }

    public Invoice(String taskId, String title, String client, double totalAmount, double hourlyRate, double hoursWorked, String dueDate, String issueDate, boolean sent) {
        this(taskId, title, client, totalAmount, hourlyRate, hoursWorked, dueDate, issueDate, sent, false);
    }

    public Invoice(String id, String client, double amount, double defaultRate, double hours, String dueDate, boolean b) {
        this(id, "", client, amount, defaultRate, hours, dueDate, "", b, false);
    }

    // Parcelable implementation
    protected Invoice(Parcel in) {
        taskId = in.readString();
        title = in.readString();
        client = in.readString();
        totalAmount = in.readDouble();
        hourlyRate = in.readDouble();
        hoursWorked = in.readDouble();
        dueDate = in.readString();
        issueDate = in.readString();
        sent = in.readByte() != 0;
        isPaid = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(taskId);
        dest.writeString(title);
        dest.writeString(client);
        dest.writeDouble(totalAmount);
        dest.writeDouble(hourlyRate);
        dest.writeDouble(hoursWorked);
        dest.writeString(dueDate);
        dest.writeString(issueDate);
        dest.writeByte((byte) (sent ? 1 : 0));
        dest.writeByte((byte) (isPaid ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
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

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
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
    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }
}