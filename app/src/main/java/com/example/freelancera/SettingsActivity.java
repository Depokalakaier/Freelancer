package com.example.freelancera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.freelancera.auth.AsanaLoginFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREF_DARK_MODE = "dark_mode";

    private Switch themeSwitch;
    private Button connectAsanaButton;
    private TextView asanaStatusText;
    private SharedPreferences prefs;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        themeSwitch = findViewById(R.id.themeSwitch);
        connectAsanaButton = findViewById(R.id.connectAsanaButton);
        asanaStatusText = findViewById(R.id.asanaStatusText);

        // Ustawienie początkowego stanu przełącznika motywu
        boolean isDarkMode = prefs.getBoolean(PREF_DARK_MODE, false);
        themeSwitch.setChecked(isDarkMode);

        // Obsługa zmiany motywu
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_DARK_MODE, isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Sprawdzenie czy użytkownik ma już połączone konto Asana
        if (auth.getCurrentUser() != null) {
            firestore.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.contains("asanaToken")) {
                            asanaStatusText.setText("Połączono z Asana");
                            connectAsanaButton.setText("Rozłącz");
                        }
                    });
        }

        // Obsługa przycisku połączenia z Asana
        connectAsanaButton.setOnClickListener(v -> {
            if (connectAsanaButton.getText().toString().equals("Rozłącz")) {
                disconnectAsana();
            } else {
                connectWithAsana();
            }
        });
    }

    private void connectWithAsana() {
        AsanaLoginFragment fragment = new AsanaLoginFragment();
        fragment.setAsanaAuthListener(authResult -> {
            if (auth.getCurrentUser() != null) {
                saveAsanaToken(authResult.accessToken);
            }
        });
        fragment.show(getSupportFragmentManager(), "asana_login");
    }

    private void disconnectAsana() {
        if (auth.getCurrentUser() != null) {
            firestore.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .update("asanaToken", null)
                    .addOnSuccessListener(aVoid -> {
                        asanaStatusText.setText("Nie połączono");
                        connectAsanaButton.setText("Połącz z Asana");
                        Toast.makeText(this, "Rozłączono z Asana", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error disconnecting Asana: " + e.getMessage());
                        Toast.makeText(this, "Błąd podczas rozłączania z Asana", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void saveAsanaToken(String token) {
        if (auth.getCurrentUser() != null) {
            firestore.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .update("asanaToken", token)
                    .addOnSuccessListener(aVoid -> {
                        asanaStatusText.setText("Połączono z Asana");
                        connectAsanaButton.setText("Rozłącz");
                        Toast.makeText(this, "Połączono z Asana!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving Asana token: " + e.getMessage());
                        Toast.makeText(this, "Błąd podczas zapisywania tokenu Asana", Toast.LENGTH_SHORT).show();
                    });
        }
    }
} 