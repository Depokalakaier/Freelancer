package com.example.freelancera.models;

import java.io.Serializable;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Locale;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;

public class Task implements Serializable {
    private String id;
    private String name;
    private String description;
    private String status;
    private String assignee;
    private String client;
    private Double workHours;
    private Double hourlyRate;
    private String asanaTaskId;
    private Date dueDate;
    private Date completionDate;
    private boolean invoiceGenerated;
    private String invoiceId;
    private boolean reminderSet;
    private Date reminderDate;

    public Task() {
        // Required empty constructor for Firestore
    }

    public Task(String id, String name, String status, String assignee, String asanaTaskId) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.assignee = assignee;
        this.asanaTaskId = asanaTaskId;
        this.client = null;
        this.workHours = null;
        this.hourlyRate = 0.0;
        this.invoiceGenerated = false;
        this.reminderSet = false;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public Double getWorkHours() { return workHours; }
    public void setWorkHours(Double workHours) { this.workHours = workHours; }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

    public String getAsanaTaskId() { return asanaTaskId; }
    public void setAsanaTaskId(String asanaTaskId) { this.asanaTaskId = asanaTaskId; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Date getCompletionDate() { return completionDate; }
    public void setCompletionDate(Date completionDate) { this.completionDate = completionDate; }

    public boolean isInvoiceGenerated() { return invoiceGenerated; }
    public void setInvoiceGenerated(boolean invoiceGenerated) { this.invoiceGenerated = invoiceGenerated; }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public boolean isReminderSet() { return reminderSet; }
    public void setReminderSet(boolean reminderSet) { this.reminderSet = reminderSet; }

    public Date getReminderDate() { return reminderDate; }
    public void setReminderDate(Date reminderDate) { this.reminderDate = reminderDate; }

    public String getFormattedWorkHours() {
        if (workHours == null) return "Brak danych";
        int hours = workHours.intValue();
        int minutes = (int) ((workHours - hours) * 60);
        return String.format("%dh %02dmin", hours, minutes);
    }

    public String getFormattedHourlyRate() {
        return String.format("%.2f zł/h", hourlyRate);
    }

    public double calculateTotal() {
        if (workHours == null || hourlyRate == null) return 0.0;
        return workHours * hourlyRate;
    }

    public String getFormattedTotal() {
        return String.format("%.2f zł", calculateTotal());
    }

    public static Task fromAsanaJson(JSONObject json) throws JSONException {
        String id = json.getString("gid");
        String name = json.getString("name");
        String status = json.getBoolean("completed") ? "Ukończone" : "Nowe";
        String assignee = json.has("assignee") && !json.isNull("assignee") ? 
            json.getJSONObject("assignee").getString("name") : null;
        String description = json.has("notes") ? json.getString("notes") : "";
        
        Task task = new Task(id, name, status, assignee, id);
        task.setDescription(description);
        
        if (json.has("due_on") && !json.isNull("due_on")) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            try {
                task.setDueDate(sdf.parse(json.getString("due_on")));
            } catch (ParseException e) {
                Log.e("Task", "Error parsing due date", e);
            }
        }
        
        return task;
    }
} 