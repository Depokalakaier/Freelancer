package com.example.freelancera;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class FreelanceraApp extends Application {
    private static final String TAG = "FreelanceraApp";

    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.d(TAG, "Inicjalizacja Firebase w Application class");
                
                FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId("freelancer-6dda0")
                    .setApplicationId("1:584924654820:android:705a8728ee30b6473810f1")
                    .setApiKey("AIzaSyDQMjECeF4IR5K0APtCFftpRcuzoC7jToE")
                    .build();
                
                FirebaseApp.initializeApp(this, options);
                
                // Konfiguracja Firestore
                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
                firestore.setFirestoreSettings(settings);
                
                Log.d(TAG, "Firebase został pomyślnie zainicjalizowany z konfiguracją");
            } else {
                Log.d(TAG, "Firebase już zainicjalizowany");
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd podczas inicjalizacji Firebase: " + e.getMessage(), e);
        }
    }
} 