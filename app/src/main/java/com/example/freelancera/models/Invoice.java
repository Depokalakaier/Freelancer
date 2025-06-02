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
    private boolean isLocalOnly = true;
    private boolean reminderSet;
    private Date reminderDate;
    private String reminderNote;

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
        this.dueDate = new Date(System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000); // +14 dni
        this.isPaid = false;
        this.status = "DRAFT";
        this.reminderSet = false;
        this.reminderDate = null;
        this.reminderNote = "";
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
        isLocalOnly = in.readByte() != 0;
        reminderSet = in.readByte() != 0;
        long reminderDateLong = in.readLong();
        reminderDate = reminderDateLong != -1 ? new Date(reminderDateLong) : null;
        reminderNote = in.readString();
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
        dest.writeByte((byte) (isLocalOnly ? 1 : 0));
        dest.writeByte((byte) (reminderSet ? 1 : 0));
        dest.writeLong(reminderDate != null ? reminderDate.getTime() : -1);
        dest.writeString(reminderNote);
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
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    
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

    public boolean isLocalOnly() { return isLocalOnly; }
    public void setLocalOnly(boolean localOnly) { this.isLocalOnly = localOnly; }

    public boolean isReminderSet() {
        return reminderSet;
    }

    public void setReminderSet(boolean reminderSet) {
        this.reminderSet = reminderSet;
    }

    public Date getReminderDate() {
        return reminderDate;
    }

    public void setReminderDate(Date reminderDate) {
        this.reminderDate = reminderDate;
        this.reminderSet = (reminderDate != null);
    }

    public String getReminderNote() {
        return reminderNote;
    }

    public void setReminderNote(String reminderNote) {
        this.reminderNote = reminderNote;
    }

    public void markAsPaid() {
        this.isPaid = true;
        this.status = "PAID";
        this.reminderSet = false; // Usuń przypomnienie po opłaceniu
    }

    public void setReminder(Date reminderDate, String note) {
        this.reminderDate = reminderDate;
        this.reminderNote = note;
        this.reminderSet = true;
    }

    public void recalculateTotal() {
        this.totalAmount = this.hours * this.ratePerHour;
    }

    public String toJson() {
        org.json.JSONObject obj = new org.json.JSONObject();
        try {
            obj.put("id", id);
            obj.put("taskId", taskId);
            obj.put("clientName", clientName);
            obj.put("taskName", taskName);
            obj.put("hours", hours);
            obj.put("ratePerHour", ratePerHour);
            obj.put("totalAmount", totalAmount);
            obj.put("issueDate", issueDate != null ? issueDate.getTime() : 0);
            obj.put("dueDate", dueDate != null ? dueDate.getTime() : 0);
            obj.put("isPaid", isPaid);
            obj.put("status", status);
            obj.put("isLocalOnly", isLocalOnly);
            obj.put("reminderSet", reminderSet);
            obj.put("reminderDate", reminderDate != null ? reminderDate.getTime() : 0);
            obj.put("reminderNote", reminderNote);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
        return obj.toString();
    }

    public static Invoice fromJson(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            Invoice invoice = new Invoice();
            invoice.setId(obj.optString("id"));
            invoice.setTaskId(obj.optString("taskId"));
            invoice.setClientName(obj.optString("clientName"));
            invoice.setTaskName(obj.optString("taskName"));
            invoice.setHours(obj.optDouble("hours"));
            invoice.setRatePerHour(obj.optDouble("ratePerHour"));
            invoice.setTotalAmount(obj.optDouble("totalAmount"));
            if (obj.has("issueDate")) invoice.setIssueDate(new java.util.Date(obj.optLong("issueDate")));
            if (obj.has("dueDate")) invoice.setDueDate(new java.util.Date(obj.optLong("dueDate")));
            invoice.setPaid(obj.optBoolean("isPaid"));
            invoice.setStatus(obj.optString("status"));
            invoice.setLocalOnly(obj.optBoolean("isLocalOnly", true));
            invoice.setReminderSet(obj.optBoolean("reminderSet"));
            if (obj.has("reminderDate")) invoice.setReminderDate(new java.util.Date(obj.optLong("reminderDate")));
            invoice.setReminderNote(obj.optString("reminderNote"));
            return invoice;
        } catch (Exception e) { return null; }
    }
} 