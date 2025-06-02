package com.example.freelancera.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class RateStorage {
    private static final String PREFS_NAME = "project_rates";
    private static final String KEY_RATES = "rates";

    public static void saveProjectRate(Context context, String projectName, double rate) {
        if (context == null || projectName == null || projectName.isEmpty()) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String ratesJson = prefs.getString(KEY_RATES, "{}");
            JSONObject rates = new JSONObject(ratesJson);
            
            // Save rate locally
            rates.put(projectName, rate);
            prefs.edit().putString(KEY_RATES, rates.toString()).apply();

            // Save to Firestore
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Map<String, Object> rateData = new HashMap<>();
                rateData.put("rate", rate);
                rateData.put("lastModified", System.currentTimeMillis());

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("project_rates")
                    .document(projectName)
                    .set(rateData);
            }
        } catch (Exception e) {
            e.printStackTrace();
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

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .collection("project_rates")
            .document(projectName)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Double rate = documentSnapshot.getDouble("rate");
                    if (rate != null) {
                        saveProjectRate(context, projectName, rate);
                        if (listener != null) listener.onSyncComplete(true);
                    }
                }
            })
            .addOnFailureListener(e -> {
                if (listener != null) listener.onSyncComplete(false);
            });
    }

    public interface OnRateSyncListener {
        void onSyncComplete(boolean success);
    }
} 