package com.example.freelancera.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.google.firebase.firestore.DocumentId;
import org.json.JSONObject;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

public class Task implements Parcelable {
    private static final String TAG = "Task";
    
    private String id;
    private String name;
    private String description;
    private String status; // "Nowe", "W toku", "Ukończone"
    private String client;
    private double ratePerHour;
    private Date dueDate;
    private Date completedDate;
    private String asanaId;
    private boolean hasInvoice;
    private boolean hasClockifyTime;
    private String source; // "asana" lub "local"
    private boolean needsSync; // true jeśli zadanie wymaga synchronizacji z Asana
    private Date lastSyncDate; // data ostatniej synchronizacji
    private Date createdAt;
    // Nowe pola dla Clockify
    private String clockifyTimeEntryId;
    private long totalTimeInSeconds;
    private Date startTime;
    private Date endTime;
    private double totalAmount; // Kwota do zapłaty (czas * stawka)
    private String currency = "PLN"; // Domyślna waluta
    private String invoiceNumber; // Numer faktury jeśli została wygenerowana
    private boolean isTimerRunning;
    private long lastStartTime;

    public Task() {
        // Required empty constructor for Firestore
        this.source = "local";
        this.needsSync = false;
        this.lastSyncDate = new Date();
        this.totalTimeInSeconds = 0;
        this.totalAmount = 0.0;
        this.status = "Nowe";
        this.id = String.valueOf(System.currentTimeMillis()); // Generujemy tymczasowe ID
    }

    public Task(String asanaId, String name, String status) {
        this.asanaId = asanaId;
        this.name = name;
        this.status = status;
        this.ratePerHour = 0.0;
        this.hasInvoice = false;
        this.hasClockifyTime = false;
        this.source = "asana";
        this.needsSync = false;
        this.lastSyncDate = new Date();
    }

    protected Task(Parcel in) {
        id = in.readString();
        name = in.readString();
        description = in.readString();
        status = in.readString();
        client = in.readString();
        ratePerHour = in.readDouble();
        long dueDateLong = in.readLong();
        dueDate = dueDateLong != -1 ? new Date(dueDateLong) : null;
        long completedDateLong = in.readLong();
        completedDate = completedDateLong != -1 ? new Date(completedDateLong) : null;
        asanaId = in.readString();
        hasInvoice = in.readByte() != 0;
        hasClockifyTime = in.readByte() != 0;
        source = in.readString();
        needsSync = in.readByte() != 0;
        long lastSyncDateLong = in.readLong();
        lastSyncDate = lastSyncDateLong != -1 ? new Date(lastSyncDateLong) : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(status);
        dest.writeString(client);
        dest.writeDouble(ratePerHour);
        dest.writeLong(dueDate != null ? dueDate.getTime() : -1);
        dest.writeLong(completedDate != null ? completedDate.getTime() : -1);
        dest.writeString(asanaId);
        dest.writeByte((byte) (hasInvoice ? 1 : 0));
        dest.writeByte((byte) (hasClockifyTime ? 1 : 0));
        dest.writeString(source);
        dest.writeByte((byte) (needsSync ? 1 : 0));
        dest.writeLong(lastSyncDate != null ? lastSyncDate.getTime() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Task> CREATOR = new Creator<Task>() {
        @Override
        public Task createFromParcel(Parcel in) {
            return new Task(in);
        }

        @Override
        public Task[] newArray(int size) {
            return new Task[size];
        }
    };

    public static Task fromAsanaJson(JSONObject json) {
        try {
            String gid = json.getString("gid");
            String name = json.getString("name");
            String status = json.getBoolean("completed") ? "Ukończone" : "Nowe";
            
            Task task = new Task(gid, name, status);
            task.setId(gid);
            task.setAsanaId(gid);
            task.source = "asana";
            task.needsSync = false;
            task.lastSyncDate = new Date();
            
            // Set description if available
            if (json.has("notes") && !json.isNull("notes")) {
                task.setDescription(json.getString("notes"));
            }
            
            // Set due date if available
            if (json.has("due_on") && !json.isNull("due_on")) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    task.setDueDate(sdf.parse(json.getString("due_on")));
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing due date from Asana", e);
                }
            }
            if (task.getId() == null) {
                Log.e(TAG, "fromAsanaJson: id (gid) is null! JSON: " + json.toString());
            }
            return task;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Asana task JSON", e);
            return null;
        }
    }

    // Getters and setters
    public String getId() { 
        if (id == null) {
            id = String.valueOf(System.currentTimeMillis());
        }
        return id; 
    }
    
    public void setId(String id) { 
        if (id != null && !id.isEmpty()) {
            this.id = id;
        }
    }

    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        markForSync();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        markForSync();
    }

    public String getStatus() { 
        // Zawsze zwracaj prawidłowy status
        if (status == null || status.isEmpty()) {
            return "Nowe";
        }
        // Normalizuj status do jednej z trzech dozwolonych wartości
        switch (status) {
            case "Nowe":
            case "W toku":
            case "Ukończone":
                return status;
            default:
                return "Nowe";
        }
    }

    public void setStatus(String status) { 
        // Akceptuj tylko prawidłowe statusy
        switch (status) {
            case "Nowe":
            case "W toku":
            case "Ukończone":
                this.status = status;
                break;
            default:
                this.status = "Nowe";
                break;
        }
        if ("Ukończone".equals(status)) {
            this.completedDate = new Date();
        }
        markForSync();
    }

    public String getClient() { return client != null ? client : "Brak klienta"; }
    public void setClient(String client) { 
        this.client = client;
        markForSync();
    }

    public double getRatePerHour() { 
        // Jeśli stawka jest ujemna lub nie została ustawiona, zwróć 0
        return ratePerHour < 0 ? 0.0 : ratePerHour; 
    }
    
    public void setRatePerHour(double ratePerHour) { 
        // Nie pozwól na ujemną stawkę
        this.ratePerHour = Math.max(0.0, ratePerHour);
        markForSync();
        // Przelicz całkowitą kwotę
        calculateTotalAmount();
    }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Object dueDate) { 
        if (dueDate instanceof Date) {
            this.dueDate = (Date) dueDate;
        } else if (dueDate instanceof String) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                this.dueDate = sdf.parse((String) dueDate);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing due date from Firestore String", e);
            }
        }
        markForSync();
    }

    public Date getCompletedDate() { return completedDate; }
    public void setCompletedDate(Date completedDate) { 
        this.completedDate = completedDate;
        markForSync();
    }

    public String getAsanaId() { return asanaId; }
    public void setAsanaId(String asanaId) { this.asanaId = asanaId; }

    public boolean isHasInvoice() { return hasInvoice; }
    public void setHasInvoice(boolean hasInvoice) { this.hasInvoice = hasInvoice; }

    public boolean isHasClockifyTime() { return hasClockifyTime; }
    public void setHasClockifyTime(boolean hasClockifyTime) { this.hasClockifyTime = hasClockifyTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }

    public Date getLastSyncDate() { return lastSyncDate; }
    public void setLastSyncDate(Date lastSyncDate) { this.lastSyncDate = lastSyncDate; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getClockifyTimeEntryId() { return clockifyTimeEntryId; }
    public void setClockifyTimeEntryId(String clockifyTimeEntryId) { this.clockifyTimeEntryId = clockifyTimeEntryId; }

    public long getTotalTimeInSeconds() { return totalTimeInSeconds; }
    public void setTotalTimeInSeconds(long totalTimeInSeconds) { 
        this.totalTimeInSeconds = totalTimeInSeconds;
        calculateTotalAmount();
    }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { 
        this.endTime = endTime;
        if (startTime != null) {
            this.totalTimeInSeconds = (endTime.getTime() - startTime.getTime()) / 1000;
            calculateTotalAmount();
        }
    }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public boolean isTimerRunning() { return isTimerRunning; }
    public void setTimerRunning(boolean timerRunning) { isTimerRunning = timerRunning; }

    public long getLastStartTime() { return lastStartTime; }
    public void setLastStartTime(long lastStartTime) { this.lastStartTime = lastStartTime; }

    private void markForSync() {
        if ("asana".equals(source)) {
            this.needsSync = true;
        }
    }

    public Invoice generateInvoice() {
        return new Invoice(id, client, name, ratePerHour);
    }

    private void calculateTotalAmount() {
        double hours = totalTimeInSeconds / 3600.0;
        this.totalAmount = hours * ratePerHour;
    }

    public String getFormattedTotalTime() {
        long hours = totalTimeInSeconds / 3600;
        long minutes = (totalTimeInSeconds % 3600) / 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    public String getFormattedAmount() {
        return String.format(Locale.getDefault(), "%.2f %s", totalAmount, currency);
    }
} 