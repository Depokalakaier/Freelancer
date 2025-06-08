package com.example.freelancera.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import android.util.Log;

public class RateStorage {
    private static final String PREFS_NAME = "project_rates";
    private static final String KEY_RATES = "rates";

    private static String encodeProjectName(String projectName) {
        if (projectName == null) return null;
        // Replace forward slashes and other problematic characters with underscores
        return projectName.replaceAll("[/\\\\]", "_");
    }

    public static void saveProjectRate(Context context, String projectName, double rate) {
        if (context == null || projectName == null || projectName.isEmpty()) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String ratesJson = prefs.getString(KEY_RATES, "{}");
            JSONObject rates = new JSONObject(ratesJson);
            
            // Save rate locally
            rates.put(projectName, rate);
            prefs.edit().putString(KEY_RATES, rates.toString()).apply();
            Log.d("RateStorage", "Zapisano stawkę lokalnie dla " + projectName + ": " + rate);

            // Save to Firestore
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Map<String, Object> rateData = new HashMap<>();
                rateData.put("rate", rate);
                rateData.put("lastModified", System.currentTimeMillis());

                // Use encoded project name for Firestore document ID
                String encodedProjectName = encodeProjectName(projectName);

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("project_rates")
                    .document(encodedProjectName)
                    .set(rateData)
                    .addOnSuccessListener(aVoid -> Log.d("RateStorage", "Zapisano stawkę w Firestore dla " + projectName))
                    .addOnFailureListener(e -> Log.e("RateStorage", "Błąd zapisu stawki w Firestore dla " + projectName, e));
            }
        } catch (Exception e) {
            Log.e("RateStorage", "Wyjątek przy zapisie stawki", e);
        }
    }

    public static double getProjectRate(Context context, String projectName) {
        if (context == null || projectName == null || projectName.isEmpty()) return 0.0;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String ratesJson = prefs.getString(KEY_RATES, "{}");
            JSONObject rates = new JSONObject(ratesJson);
            
            if (rates.has(projectName)) {
                return rates.getDouble(projectName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static void syncWithFirestore(Context context, String projectName, OnRateSyncListener listener) {
        if (context == null || projectName == null || projectName.isEmpty()) {
            if (listener != null) listener.onSyncComplete(false);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onSyncComplete(false);
            return;
        }

        // Use encoded project name for Firestore document ID
        String encodedProjectName = encodeProjectName(projectName);

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .collection("project_rates")
            .document(encodedProjectName)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Double rate = documentSnapshot.getDouble("rate");
                    if (rate != null) {
                        saveProjectRate(context, projectName, rate);
                        Log.d("RateStorage", "Pobrano stawkę z Firestore dla " + projectName + ": " + rate);
                        if (listener != null) listener.onSyncComplete(true);
                    }
                } else {
                    Log.w("RateStorage", "Brak dokumentu stawki w Firestore dla " + projectName);
                    if (listener != null) listener.onSyncComplete(false);
                }
            })
            .addOnFailureListener(e -> {
                Log.e("RateStorage", "Błąd pobierania stawki z Firestore dla " + projectName, e);
                if (listener != null) listener.onSyncComplete(false);
            });
    }

    public interface OnRateSyncListener {
        void onSyncComplete(boolean success);
    }
} 