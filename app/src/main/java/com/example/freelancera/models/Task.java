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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;
import java.util.ArrayList;
import android.content.Context;
import com.example.freelancera.storage.TaskStorage;
import com.google.firebase.auth.FirebaseUser;

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
    // Pola dla Toggl
    private String togglProjectId;
    private String togglProjectName;
    private String togglClientId;
    private String togglClientName;
    private long togglTrackedSeconds;
    private Date completedAt;
    // Dodaję pole context (jeśli nie ma)
    private transient android.content.Context context;

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
            Task task = new Task();
            task.setId(json.getString("gid"));
            task.setName(json.getString("name"));
            task.setDescription(json.optString("notes", ""));
            task.setStatus(json.optBoolean("completed") ? "Ukończone" : "Nowe");
            
            // Zachowaj istniejącą stawkę jeśli zadanie już istnieje
            Task existingTask = null;
            if (task.context != null) {
                TaskStorage storage = new TaskStorage(task.context);
                existingTask = storage.getTask(task.getId());
            }
            
            if (existingTask != null) {
                task.setRatePerHour(existingTask.getRatePerHour());
                task.setClient(existingTask.getClient());
                task.setTogglProjectId(existingTask.getTogglProjectId());
                task.setTogglProjectName(existingTask.getTogglProjectName());
                task.setTogglClientId(existingTask.getTogglClientId());
                task.setTogglClientName(existingTask.getTogglClientName());
                task.setTogglTrackedSeconds(existingTask.getTogglTrackedSeconds());
            }
            
            return task;
        } catch (Exception e) {
            e.printStackTrace();
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
        // Jeśli mamy ustawioną stawkę dla tego zadania, użyj jej
        if (ratePerHour > 0) return ratePerHour;
        
        // Jeśli nie ma stawki, spróbuj pobrać z Firestore
        if (context != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("tasks")
                    .document(getId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Double rate = documentSnapshot.getDouble("ratePerHour");
                            if (rate != null && rate > 0) {
                                ratePerHour = rate;
                                // Zapisz lokalnie
                                TaskStorage storage = new TaskStorage(context);
                                storage.saveTask(this);
                            }
                        }
                    });
            }
        }
        
        return ratePerHour;
    }

    public void setRatePerHour(double ratePerHour) { 
        this.ratePerHour = ratePerHour;
        if (context != null) {
            // Zapisz do Firestore
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("tasks")
                    .document(getId())
                    .update("ratePerHour", ratePerHour);
                
                // Zapisz lokalnie
                TaskStorage storage = new TaskStorage(context);
                storage.saveTask(this);
            }
        }
        markForSync();
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

    public String getTogglProjectId() { return togglProjectId; }
    public void setTogglProjectId(String togglProjectId) { this.togglProjectId = togglProjectId; }

    public String getTogglProjectName() { return togglProjectName; }
    public void setTogglProjectName(String togglProjectName) { this.togglProjectName = togglProjectName; }

    public String getTogglClientId() { return togglClientId; }
    public void setTogglClientId(String togglClientId) { this.togglClientId = togglClientId; }

    public String getTogglClientName() { return togglClientName; }
    public void setTogglClientName(String togglClientName) { this.togglClientName = togglClientName; }

    public long getTogglTrackedSeconds() { return togglTrackedSeconds; }
    public void setTogglTrackedSeconds(long togglTrackedSeconds) { this.togglTrackedSeconds = togglTrackedSeconds; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public void setContext(android.content.Context context) { this.context = context; }

    private void markForSync() {
        if ("asana".equals(source)) {
            this.needsSync = true;
        }
    }

    public Invoice generateInvoice() {
        return new Invoice(id, client, name, ratePerHour);
    }

    private void calculateTotalAmount() {
        if (togglTrackedSeconds > 0 && ratePerHour > 0) {
            double hours = togglTrackedSeconds / 3600.0;
        this.totalAmount = hours * ratePerHour;
        }
    }

    public String getFormattedTotalTime() {
        long hours = totalTimeInSeconds / 3600;
        long minutes = (totalTimeInSeconds % 3600) / 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    public String getFormattedAmount() {
        return String.format(Locale.getDefault(), "%.2f %s", totalAmount, currency);
    }

    public void updateTogglData(Context context, long trackedSeconds, String projectName, String clientName) {
        this.togglTrackedSeconds = trackedSeconds;
        this.togglProjectName = projectName;
        this.togglClientName = clientName;
        
        if (context != null) {
            TaskStorage storage = new TaskStorage(context);
            storage.saveTask(this);
        }
    }

    public static List<Task> loadTasks(Context context) {
        if (context == null) return new ArrayList<>();
        TaskStorage storage = new TaskStorage(context);
        return storage.getAllTasks();
    }

    public void save(Context context) {
        if (context == null) return;
        TaskStorage storage = new TaskStorage(context);
        storage.saveTask(this);
    }

    public double getRoundedHours() {
        long seconds = togglTrackedSeconds > 0 ? togglTrackedSeconds : totalTimeInSeconds;
        double exactHours = seconds / 3600.0;
        
        // Wydziel pełne godziny i minuty
        int fullHours = (int) exactHours;
        int minutes = (int) ((exactHours - fullHours) * 60);
        
        // Zaokrąglij według schematu
        if (minutes <= 15) {
            return fullHours;
        } else if (minutes <= 44) {
            return fullHours + 0.5;
        } else {
            return fullHours + 1;
        }
    }

    public String getFormattedRoundedHours() {
        double roundedHours = getRoundedHours();
        int hours = (int) roundedHours;
        int minutes = (int) ((roundedHours - hours) * 60);
        return String.format(Locale.getDefault(), "%d:%02d", hours, minutes);
    }

    public void updateRateFromFirestore(Runnable onComplete) {
        if (context == null || id == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .collection("tasks")
            .document(id)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Double rate = documentSnapshot.getDouble("ratePerHour");
                    if (rate != null && rate > 0) {
                        ratePerHour = rate;
                        // Zapisz lokalnie
                        TaskStorage storage = new TaskStorage(context);
                        storage.saveTask(this);
                    }
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            })
            .addOnFailureListener(e -> {
                if (onComplete != null) {
                    onComplete.run();
                }
            });
    }
} 