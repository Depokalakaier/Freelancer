package com.example.freelancera.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.freelancera.models.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Date;

public class TaskStorage {
    private static final String PREFS_NAME = "task_data";
    private static final String KEY_TASKS = "tasks";
    
    private final Context context;
    private final FirebaseFirestore firestore;
    private final FirebaseUser user;
    private final SharedPreferences prefs;

    public TaskStorage(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.user = FirebaseAuth.getInstance().getCurrentUser();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveTask(Task task) {
        if (task == null) return;

        // Save locally
        try {
            String tasksJson = prefs.getString(KEY_TASKS, "{}");
            JSONObject tasks = new JSONObject(tasksJson);
            
            // Convert task to JSON
            Gson gson = new Gson();
            String taskJson = gson.toJson(task);
            JSONObject taskObj = new JSONObject(taskJson);
            
            // Add last modified timestamp
            taskObj.put("lastModified", new Date().getTime());
            
            // Save to local storage
            tasks.put(task.getId(), taskObj);
            prefs.edit().putString(KEY_TASKS, tasks.toString()).apply();

            // Save to Firestore if user is logged in
            if (user != null) {
                firestore.collection("users")
                        .document(user.getUid())
                        .collection("tasks")
                        .document(task.getId())
                        .set(task);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Task getTask(String taskId) {
        if (taskId == null) return null;

        try {
            String tasksJson = prefs.getString(KEY_TASKS, "{}");
            JSONObject tasks = new JSONObject(tasksJson);
            
            if (tasks.has(taskId)) {
                JSONObject taskObj = tasks.getJSONObject(taskId);
                return new Gson().fromJson(taskObj.toString(), Task.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Task> getAllTasks() {
        List<Task> taskList = new ArrayList<>();
        try {
            String tasksJson = prefs.getString(KEY_TASKS, "{}");
            JSONObject tasks = new JSONObject(tasksJson);
            
            Iterator<String> keys = tasks.keys();
            while (keys.hasNext()) {
                String taskId = keys.next();
                JSONObject taskObj = tasks.getJSONObject(taskId);
                Task task = new Gson().fromJson(taskObj.toString(), Task.class);
                taskList.add(task);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return taskList;
    }

    public void clearAllTasks() {
        prefs.edit().remove(KEY_TASKS).apply();
        
        // Usuń też z Firestore jeśli użytkownik jest zalogowany
        if (user != null) {
            firestore.collection("users")
                    .document(user.getUid())
                    .collection("tasks")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        WriteBatch batch = firestore.batch();
                        for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            batch.delete(document.getReference());
                        }
                        batch.commit();
                    });
        }
    }

    public void syncWithFirestore(String taskId, OnTaskSyncListener listener) {
        if (user == null || taskId == null) {
            if (listener != null) listener.onSyncComplete(false);
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Task firestoreTask = documentSnapshot.toObject(Task.class);
                        if (firestoreTask != null) {
                            // Get local task
                            Task localTask = getTask(taskId);
                            
                            // Merge data
                            if (localTask != null) {
                                // Keep local data that shouldn't be overwritten
                                firestoreTask.setDueDate(localTask.getDueDate());
                                firestoreTask.setClient(localTask.getClient());
                                
                                // Get rate from RateStorage
                                double savedRate = RateStorage.getProjectRate(context, firestoreTask.getName());
                                if (savedRate > 0) {
                                    firestoreTask.setRatePerHour(savedRate);
                                }
                            }
                            
                            // Save merged task
                            saveTask(firestoreTask);
                            
                            if (listener != null) listener.onSyncComplete(true);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onSyncComplete(false);
                });
    }

    public interface OnTaskSyncListener {
        void onSyncComplete(boolean success);
    }
} 