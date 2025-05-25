package com.example.freelancera;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private EditText emailEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton, goToLoginButton;
    private TextView typewriterText;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private Handler handler;
    private int currentIndex = 0;
    private boolean isDeleting = false;
    private static final String TEXT_TO_TYPE = "zarejestruj sie by zacząć swoja przygodę...";
    private static final long TYPING_DELAY = 150;
    private static final long DELETING_DELAY = 50;
    private static final long PAUSE_BEFORE_DELETE = 2000;
    private static final long PAUSE_BEFORE_RETYPE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        try {
            // Inicjalizacja Firebase z dodatkowym logowaniem
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.d(TAG, "Firebase nie jest zainicjalizowany. Próba inicjalizacji...");
                FirebaseApp.initializeApp(this);
            }
            
            auth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
            
            Log.d(TAG, "Firebase został pomyślnie zainicjalizowany");
            
            if (auth == null) {
                Log.e(TAG, "FirebaseAuth nie został prawidłowo zainicjalizowany!");
                Toast.makeText(this, "Błąd inicjalizacji Firebase Auth", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd podczas inicjalizacji Firebase: " + e.getMessage(), e);
            Toast.makeText(this, "Błąd inicjalizacji Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        registerButton = findViewById(R.id.registerButton);
        goToLoginButton = findViewById(R.id.goToLoginButton);
        typewriterText = findViewById(R.id.typewriterText);
        handler = new Handler(Looper.getMainLooper());

        setupTypewriterAnimation();
        setupButtons();
    }

    private void setupTypewriterAnimation() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isDeleting) {
                    if (currentIndex <= TEXT_TO_TYPE.length()) {
                        typewriterText.setText(TEXT_TO_TYPE.substring(0, currentIndex));
                        currentIndex++;
                        handler.postDelayed(this, TYPING_DELAY);
                    } else {
                        handler.postDelayed(() -> {
                            isDeleting = true;
                            handler.post(this);
                        }, PAUSE_BEFORE_DELETE);
                    }
                } else {
                    if (currentIndex > 0) {
                        typewriterText.setText(TEXT_TO_TYPE.substring(0, currentIndex - 1));
                        currentIndex--;
                        handler.postDelayed(this, DELETING_DELAY);
                    } else {
                        isDeleting = false;
                        handler.postDelayed(() -> {
                            currentIndex = 0;
                            handler.post(this);
                        }, PAUSE_BEFORE_RETYPE);
                    }
                }
            }
        });
    }

    private void setupButtons() {
        registerButton.setOnClickListener(view -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Hasła nie są identyczne", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("email", email);
                                userData.put("uid", user.getUid());

                                firestore.collection("users").document(user.getUid())
                                        .set(userData)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Dane użytkownika zapisane pomyślnie");
                                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Błąd podczas zapisywania danych użytkownika", e);
                                            Toast.makeText(RegisterActivity.this, 
                                                "Błąd podczas tworzenia profilu: " + e.getMessage(), 
                                                Toast.LENGTH_LONG).show();
                                        });
                            }
                        } else {
                            String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Nieznany błąd";
                            Toast.makeText(RegisterActivity.this, 
                                "Błąd rejestracji: " + errorMessage, 
                                Toast.LENGTH_LONG).show();
                        }
                    });
        });

        goToLoginButton.setOnClickListener(view -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) 
            getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());
            return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }
}