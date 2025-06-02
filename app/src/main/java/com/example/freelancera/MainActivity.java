package com.example.freelancera;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.freelancera.auth.AsanaAuthManager;
import com.example.freelancera.auth.AsanaLoginFragment;
import com.example.freelancera.models.Task;
import com.example.freelancera.view.TaskListFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * MainActivity - główna aktywność aplikacji Freelancer.
 * Obsługuje nawigację, logikę startową, przełączanie zakładek i integracje z Asana/Toggl.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private BottomNavigationView bottomNavigationView;
    private BottomSheetDialog profileBottomSheet;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicjalizacja Firebase
        initializeFirebase();

        // Konfiguracja Toolbar
        setupToolbar();

        // Konfiguracja Bottom Navigation
        setupBottomNavigation();

        // Inicjalizacja image picker
        setupImagePicker();

        // Otwórz od razu fragment z zadaniami po zalogowaniu
        if (user != null && savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new com.example.freelancera.view.TaskListFragment())
                .commit();
            // Odśwież zadania po 5 sekundach od startu
            new android.os.Handler().postDelayed(() -> {
                androidx.fragment.app.Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (current instanceof com.example.freelancera.view.TaskListFragment) {
                    ((com.example.freelancera.view.TaskListFragment) current).fetchAndSyncTasksFromAsana();
                }
            }, 5000);
            syncTogglData();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: otrzymano intent: " + intent);

        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            Log.d(TAG, "onNewIntent: URI: " + data.toString());

            if (data.getScheme().equals("freelancera") && data.getHost().equals("oauth")) {
                AsanaLoginFragment.handleOAuthResponse(data, this);
            } else if (data.getScheme().equals("myapp") && data.getHost().equals("callback")) {
                String token = data.getQueryParameter("token");
                if (token != null && user != null) {
                    token = token.trim();
                    final String finalToken = token;
                    Log.d("TogglToken", "Token przekazany do OkHttp: '" + finalToken + "'");
                    // Zapisz do Firestore ZAWSZE
                    firestore.collection("users").document(user.getUid())
                        .update("togglToken", finalToken)
                        .addOnSuccessListener(unused -> Log.d(TAG, "Toggl token zapisany w Firestore: " + finalToken))
                        .addOnFailureListener(e -> Log.e(TAG, "Błąd zapisu Toggl token: " + e.getMessage()));
                    // Zapisz do SharedPreferences
                    getSharedPreferences("toggl_prefs", MODE_PRIVATE)
                        .edit().putString("api_key", finalToken).apply();

                    // Weryfikacja API Key Toggl (tylko Toast i logi)
                    OkHttpClient client = new OkHttpClient();
                    String auth = okhttp3.Credentials.basic(finalToken, "api_token");
                    Request meReq = new Request.Builder()
                        .url("https://api.track.toggl.com/api/v9/me")
                        .addHeader("Authorization", auth)
                        .build();
                    client.newCall(meReq).enqueue(new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, IOException e) {
                            Log.e(TAG, "Toggl API connection error: " + e.getMessage());
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd połączenia z Toggl: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                Log.e(TAG, "Toggl API connection error: " + response.message());
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd połączenia z Toggl: " + response.message(), Toast.LENGTH_LONG).show());
                                return;
                            }
                            String body = response.body().string();
                            String msg = "Toggl API response: " + response.code() + " " + body;
                            Log.d(TAG, msg);
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Token: '" + finalToken + "'\nKod: " + response.code() + "\n" + body, Toast.LENGTH_LONG).show();
                                if (response.isSuccessful()) {
                                    Toast.makeText(MainActivity.this, "Połączono z Toggl!", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Niepoprawny API Key Toggl!", Toast.LENGTH_LONG).show();
                                }
                                // Odśwież profil, jeśli otwarty
                                if (profileBottomSheet != null && profileBottomSheet.isShowing()) {
                                    profileBottomSheet.dismiss();
                                    showProfileBottomSheet();
                                }
                            });
                        }
                    });
                }
            }
        }
    }

    private void initializeFirebase() {
        try {
            auth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
            user = auth.getCurrentUser();

            if (user == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Błąd inicjalizacji Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("");
            setSupportActionBar(toolbar);
            
            // Wyśrodkuj tytuł
            TextView toolbarTitle = toolbar.findViewById(R.id.toolbarTitle);
            if (toolbarTitle != null) {
                toolbarTitle.setText("Fleekly");
                toolbarTitle.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            }

            // Ikona profilu
            ShapeableImageView profileIcon = toolbar.findViewById(R.id.profileIcon);
            if (profileIcon != null) {
                profileIcon.setOnClickListener(v -> showProfileBottomSheet());
                // Załaduj zdjęcie profilowe jeśli istnieje, w przeciwnym razie domyślna ikona aplikacji
                if (user != null && user.getPhotoUrl() != null) {
                    Glide.with(this)
                        .load(user.getPhotoUrl())
                        .placeholder(R.mipmap.ic_launcher)
                        .error(R.mipmap.ic_launcher)
                        .into(profileIcon);
                } else {
                    profileIcon.setImageResource(R.mipmap.ic_launcher);
                }
            }
        }
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_tasks) {
                    // Zawsze wstaw nowy TaskListFragment
                    getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new com.example.freelancera.view.TaskListFragment())
                        .commit();
                    // Odśwież zadania po krótkim opóźnieniu
                    new android.os.Handler().postDelayed(() -> {
                        androidx.fragment.app.Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                        if (current instanceof com.example.freelancera.view.TaskListFragment) {
                            ((com.example.freelancera.view.TaskListFragment) current).fetchAndSyncTasksFromAsana();
                        }
                    }, 500);
                    return true;
                } else if (itemId == R.id.navigation_invoices) {
                    // Zakładka Faktury - dedykowany fragment
                    getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new com.example.freelancera.view.InvoiceListFragment())
                        .commit();
                    return true;
                } else if (itemId == R.id.navigation_history) {
                    // Zakładka Historia - pusta
                    getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new androidx.fragment.app.Fragment())
                        .commit();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    uploadProfileImage(imageUri);
                }
            }
        );
    }

    private void showProfileBottomSheet() {
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_profile, null);
        profileBottomSheet = new BottomSheetDialog(this);
        profileBottomSheet.setContentView(bottomSheetView);

        // Inicjalizacja widoków
        ShapeableImageView profileImage = bottomSheetView.findViewById(R.id.profileImage);
        MaterialButton connectAsanaButton = bottomSheetView.findViewById(R.id.connectAsanaButton);
        MaterialButton changePasswordButton = bottomSheetView.findViewById(R.id.changePasswordButton);
        MaterialButton logoutButton = bottomSheetView.findViewById(R.id.logoutButton);
        MaterialButton connectTogglButton = bottomSheetView.findViewById(R.id.connectTogglButton);

        // Ustawienie emaila użytkownika
        if (user != null && user.getEmail() != null) {
            ((android.widget.TextView) bottomSheetView.findViewById(R.id.emailText))
                .setText(user.getEmail());
        }

        // Sprawdź status połączenia z Asana
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    boolean isConnected = document.contains("asanaToken") && 
                                        document.getBoolean("asanaConnected") == Boolean.TRUE;
                    updateAsanaConnectionUI(connectAsanaButton, isConnected);
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                    "Błąd podczas sprawdzania połączenia z Asana", 
                    Toast.LENGTH_SHORT).show());
        }

        // Sprawdź status połączenia z Toggl
        boolean togglConnected = false;
        String togglToken;
        if (user != null) {
            togglToken = getSharedPreferences("toggl_prefs", MODE_PRIVATE).getString("api_key", null);
        } else {
            togglToken = null;
        }
        if (togglToken != null && !togglToken.isEmpty()) {
            togglConnected = true;
        }

        // Obsługa zdjęcia profilowego
        profileImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        // Obsługa przycisku Asana
        connectAsanaButton.setOnClickListener(v -> {
            if (connectAsanaButton.getText().toString().equals("Rozłącz konto Asana")) {
                disconnectFromAsana(connectAsanaButton);
            } else {
                connectWithAsana();
            }
            profileBottomSheet.dismiss();
        });

        // Obsługa przycisku Toggl
        if (connectTogglButton != null) {
            if (togglConnected) {
                connectTogglButton.setText("Rozłącz z Toggl");
                connectTogglButton.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_light));
                connectTogglButton.setOnClickListener(v -> {
                    // Usuń token z Firestore i SharedPreferences
                    if (user != null) {
                        firestore.collection("users").document(user.getUid())
                            .update("togglToken", null)
                            .addOnSuccessListener(unused -> Log.d(TAG, "Toggl token usunięty z Firestore"))
                            .addOnFailureListener(e -> Log.e(TAG, "Błąd usuwania Toggl token: " + e.getMessage()));
                    }
                    getSharedPreferences("toggl_prefs", MODE_PRIVATE).edit().remove("api_key").apply();
                    Toast.makeText(this, "Rozłączono z Toggl", Toast.LENGTH_SHORT).show();
                    profileBottomSheet.dismiss();
                    showProfileBottomSheet();
                });
            } else {
                connectTogglButton.setText("Połącz z Toggl");
                connectTogglButton.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_blue_light));
                connectTogglButton.setOnClickListener(v -> {
                    // Otwórz stronę do pobrania tokenu w Custom Tabs
                    String togglConnectUrl = "https://depokalakaier.github.io/Freelancer/connect-toggle.html";
                    androidx.browser.customtabs.CustomTabsIntent customTabsIntent = new androidx.browser.customtabs.CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build();
                    customTabsIntent.launchUrl(this, android.net.Uri.parse(togglConnectUrl));
        });
            }
        }

        // Obsługa zmiany hasła
        changePasswordButton.setOnClickListener(v -> {
            if (user != null && user.getEmail() != null) {
                auth.sendPasswordResetEmail(user.getEmail())
                    .addOnSuccessListener(unused -> Toast.makeText(MainActivity.this,
                        "Link do zmiany hasła został wysłany na Twój email", Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this,
                        "Błąd: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });

        // Obsługa wylogowania
        logoutButton.setOnClickListener(v -> {
            profileBottomSheet.dismiss();
            logout();
        });

        profileBottomSheet.show();
    }

    private void updateAsanaConnectionUI(MaterialButton button, boolean isConnected) {
        if (button != null) {
            if (isConnected) {
                button.setText("Rozłącz konto Asana");
                button.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_light));
            } else {
                button.setText("Połącz z Asana");
                button.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_blue_light));
            }
        }
    }

    private void disconnectFromAsana(MaterialButton button) {
        if (user != null) {
            DocumentReference userRef = firestore.collection("users").document(user.getUid());
            userRef.update(
                "asanaToken", null,
                "asanaIdToken", null,
                "asanaEmail", null,
                "asanaConnected", false
            )
            .addOnSuccessListener(unused -> {
                Toast.makeText(this, "Rozłączono z Asana!", Toast.LENGTH_SHORT).show();
                updateAsanaConnectionUI(button, false);
            })
            .addOnFailureListener(e -> Toast.makeText(this, 
                "Błąd podczas rozłączania z Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void connectWithAsana() {
        try {
            AsanaLoginFragment fragment = new AsanaLoginFragment();
            fragment.setAsanaAuthListener(authResult -> {
                if (user != null) {
                    DocumentReference userRef = firestore.collection("users").document(user.getUid());
                    userRef.update(
                        "asanaToken", authResult.accessToken,
                        "asanaIdToken", authResult.idToken,
                        "asanaEmail", authResult.email,
                        "asanaConnected", true
                    )
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Połączono z Asana!", Toast.LENGTH_SHORT).show();
                        // Aktualizuj UI w następnym bottom sheet
                        if (profileBottomSheet != null && profileBottomSheet.isShowing()) {
                            View bottomSheetView = profileBottomSheet.findViewById(android.R.id.content);
                            if (bottomSheetView != null) {
                                MaterialButton connectButton = bottomSheetView.findViewById(R.id.connectAsanaButton);
                                if (connectButton != null) {
                                    updateAsanaConnectionUI(connectButton, true);
                                }
                            }
                        }
                        loadAsanaTasks();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, 
                        "Błąd zapisu tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            fragment.show(getSupportFragmentManager(), "asana_login");
        } catch (Exception e) {
            Toast.makeText(this, "Błąd podczas łączenia z Asana: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Błąd podczas wylogowywania: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void loadAsanaTasks() {
        if (user == null) return;
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(document -> {
                if (document.contains("asanaToken")) {
                    String token = document.getString("asanaToken");
                    Log.d(TAG, "Znaleziono token Asana, pobieram workspaces...");

                    // Najpierw pobierz workspaces
                    com.example.freelancera.auth.AsanaApi.getWorkspaces(token, new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            Log.e(TAG, "Błąd pobierania workspaces: " + e.getMessage(), e);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                "Błąd pobierania workspaces: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            if (response.isSuccessful()) {
                                String responseBody = response.body().string();
                                Log.d(TAG, "ASANA WORKSPACES: " + responseBody);
                                try {
                                    org.json.JSONObject json = new org.json.JSONObject(responseBody);
                                    org.json.JSONArray workspaces = json.getJSONArray("data");
                                    if (workspaces.length() > 0) {
                                        String workspaceId = workspaces.getJSONObject(0).getString("gid");
                                        // Pobierz projekty
                                        com.example.freelancera.auth.AsanaApi.getProjects(token, workspaceId, new okhttp3.Callback() {
                                            @Override
                                            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                                                Log.e(TAG, "Błąd pobierania projektów: " + e.getMessage(), e);
                                                runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                    "Błąd pobierania projektów: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                            }

                                            @Override
                                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                                                if (response.isSuccessful()) {
                                                    String projectsBody = response.body().string();
                                                    Log.d(TAG, "ASANA PROJECTS: " + projectsBody);
                                                    try {
                                                        org.json.JSONArray projects;
                                                        // Sprawdź czy odpowiedź to tablica czy obiekt
                                                        if (projectsBody.trim().startsWith("[")) {
                                                            projects = new org.json.JSONArray(projectsBody);
                                                        } else if (projectsBody.trim().startsWith("{")) {
                                                            org.json.JSONObject json = new org.json.JSONObject(projectsBody);
                                                            projects = json.getJSONArray("data");
                                                        } else {
                                                            Log.e(TAG, "[TOGGL] Nieoczekiwany format odpowiedzi: " + projectsBody);
                                                            return;
                                                        }
                                                        Log.d(TAG, "[TOGGL] Liczba projektów do zapisu: " + projects.length());
                                                        if (projects.length() > 0) {
                                                            String projectId = projects.getJSONObject(0).getString("gid");
                                                            // Pobierz zadania
                                                            com.example.freelancera.auth.AsanaApi.getTasks(token, projectId, new okhttp3.Callback() {
                                                                @Override
                                                                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                                                                    Log.e(TAG, "Błąd pobierania zadań: " + e.getMessage(), e);
                                                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                                        "Błąd pobierania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                                                }

                                                                @Override
                                                                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                                                                    String responseBody = response.body().string();
                                                                    Log.d(TAG, "ASANA TASKS: " + responseBody);
                                                                    if (response.isSuccessful()) {
                                                                        try {
                                                                            org.json.JSONObject json = new org.json.JSONObject(responseBody);
                                                                            org.json.JSONArray tasks = json.getJSONArray("data");
                                                                            List<com.example.freelancera.models.Task> taskList = new java.util.ArrayList<>();
                                                                            for (int i = 0; i < tasks.length(); i++) {
                                                                                org.json.JSONObject taskJson = tasks.getJSONObject(i);
                                                                                com.example.freelancera.models.Task task = com.example.freelancera.models.Task.fromAsanaJson(taskJson);
                                                                                if (task != null && task.getId() != null && !task.getId().isEmpty()) {
                                                                                    // Zapisz do Firestore
                                                                                    firestore.collection("users").document(user.getUid())
                                                                                        .collection("tasks").document(task.getId())
                                                                                        .set(task);
                                                                                    taskList.add(task);
                                                                                } else {
                                                                                    Log.e(TAG, "Nie zapisano taska z Asany, brak id! JSON: " + taskJson.toString());
                                                                                }
                                                                            }
                                                                            runOnUiThread(() -> {
                                                                                Toast.makeText(MainActivity.this, "Pobrano " + taskList.size() + " zadań z Asana!", Toast.LENGTH_SHORT).show();
                                                                                // Odśwież fragment z listą zadań
                                                                                getSupportFragmentManager()
                                                                                    .beginTransaction()
                                                                                    .replace(R.id.fragment_container, new com.example.freelancera.view.TaskListFragment())
                                                                                    .commit();
                                                                            });
                                                                        } catch (Exception e) {
                                                                            Log.e(TAG, "Błąd parsowania zadań: " + e.getMessage(), e);
                                                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd parsowania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                                                        }
                                                                    } else {
                                                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd pobierania zadań: " + response.message(), Toast.LENGTH_LONG).show());
                                                                    }
                                                                }
                                                            });
                                                        } else {
                                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Brak projektów w Asana", Toast.LENGTH_LONG).show());
                                                        }
                                                    } catch (Exception e) {
                                                        Log.e(TAG, "Błąd parsowania projektów: " + e.getMessage(), e);
                                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd parsowania projektów: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                                    }
                                                } else {
                                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd pobierania projektów: " + response.message(), Toast.LENGTH_LONG).show());
                                                }
                                            }
                                        });
                                    } else {
                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Brak workspace w Asana", Toast.LENGTH_LONG).show());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Błąd parsowania workspaces: " + e.getMessage(), e);
                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd parsowania workspaces: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            } else {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd pobierania workspaces: " + response.message(), Toast.LENGTH_LONG).show());
                            }
                        }
                    });
                } else {
                    Log.d(TAG, "Brak tokenu Asana");
                    Toast.makeText(this, "Brak tokenu Asana. Połącz najpierw konto.", Toast.LENGTH_LONG).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Błąd pobierania tokenu z Firestore: " + e.getMessage(), e);
                Toast.makeText(this, "Błąd pobierania tokenu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    public void syncTogglData() {
        Log.d(TAG, "syncTogglData: UID = " + (user != null ? user.getUid() : "user null"));
        if (user == null) {
            Log.e(TAG, "syncTogglData: user == null");
            return;
        }
        if (firestore == null) {
            Log.e(TAG, "syncTogglData: firestore == null");
            return;
        }
        firestore.collection("users").document(user.getUid()).get().addOnSuccessListener(document -> {
            String togglToken = document.getString("togglToken");
            if (togglToken == null || togglToken.isEmpty()) {
                Log.e(TAG, "syncTogglData: brak tokena");
                return;
    }
            OkHttpClient client = new OkHttpClient();
            String auth = okhttp3.Credentials.basic(togglToken, "api_token");
            // 1. Pobierz workspaceId
            Request meReq = new Request.Builder()
                    .url("https://api.track.toggl.com/api/v9/me")
                    .addHeader("Authorization", auth)
                    .build();
            client.newCall(meReq).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    Log.e(TAG, "[TOGGL] Błąd pobierania me: " + e.getMessage());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "[TOGGL] Błąd pobierania me: " + response.message());
                        return;
                    }
                    String body = response.body().string();
                    try {
                        org.json.JSONObject me = new org.json.JSONObject(body);
                        long workspaceId = me.getLong("default_workspace_id");
                        // 2. Pobierz WSZYSTKIE projekty
                        String projectsUrl = "https://api.track.toggl.com/api/v9/workspaces/" + workspaceId + "/projects";
                        Request projectsReq = new Request.Builder()
                                .url(projectsUrl)
                                .addHeader("Authorization", auth)
                                .build();
                        client.newCall(projectsReq).enqueue(new okhttp3.Callback() {
                            @Override
                            public void onFailure(okhttp3.Call call, IOException e) {
                                Log.e(TAG, "[TOGGL] Błąd pobierania projektów: " + e.getMessage());
                            }
                            @Override
                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                if (!response.isSuccessful()) {
                                    Log.e(TAG, "[TOGGL] Błąd pobierania projektów: " + response.message());
                                    return;
                                }
                                String projectsBody = response.body().string();
                                try {
                                    org.json.JSONArray projects;
                                    // Sprawdź czy odpowiedź to tablica czy obiekt
                                    if (projectsBody.trim().startsWith("[")) {
                                        projects = new org.json.JSONArray(projectsBody);
                                    } else if (projectsBody.trim().startsWith("{")) {
                                        org.json.JSONObject json = new org.json.JSONObject(projectsBody);
                                        projects = json.getJSONArray("data");
                                    } else {
                                        Log.e(TAG, "[TOGGL] Nieoczekiwany format odpowiedzi: " + projectsBody);
                                        return;
                                    }
                                    Log.d(TAG, "[TOGGL] Liczba projektów do zapisu: " + projects.length());
                                    // Zbierz unikalne workspace_id
                                    java.util.Set<String> workspaceIds = new java.util.HashSet<>();
                                    for (int i = 0; i < projects.length(); i++) {
                                        org.json.JSONObject project = projects.getJSONObject(i);
                                        String workspaceIdStr = project.optString("workspace_id", null);
                                        if (workspaceIdStr != null) workspaceIds.add(workspaceIdStr);
                                    }
                                    // Dla każdego workspace_id: pobierz klientów, usuń i zapisz projekty
                                    for (String workspaceIdStr : workspaceIds) {
                                        // Pobierz klientów dla workspace
                                        String clientsUrl = "https://api.track.toggl.com/api/v9/workspaces/" + workspaceIdStr + "/clients";
                                        Request clientsReq = new Request.Builder()
                                                .url(clientsUrl)
                                                .addHeader("Authorization", auth)
                                                .build();
                                        client.newCall(clientsReq).enqueue(new okhttp3.Callback() {
                                            @Override
                                            public void onFailure(okhttp3.Call call, IOException e) {
                                                Log.e(TAG, "[TOGGL] Błąd pobierania klientów: " + e.getMessage());
                                                // Kontynuuj bez klientów
                                                saveProjectsForWorkspace(projects, workspaceIdStr, null);
                                            }
                                            @Override
                                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                                org.json.JSONArray clients = null;
                    if (response.isSuccessful()) {
                                                    try {
                                                        String clientsBody = response.body().string();
                                                        clients = new org.json.JSONArray(clientsBody);
                                                    } catch (Exception e) {
                                                        Log.e(TAG, "[TOGGL] Błąd parsowania klientów: " + e.getMessage());
                                                    }
                                                }
                                                saveProjectsForWorkspace(projects, workspaceIdStr, clients);
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "[TOGGL] Błąd parsowania projektów: " + e.getMessage(), e);
                                }
                            }
                        });
                        // 3. Pobierz WSZYSTKIE time entries (bez zmian)
                        String timeEntriesUrl = "https://api.track.toggl.com/api/v9/me/time_entries";
                        Request timeReq = new Request.Builder()
                                .url(timeEntriesUrl)
                                .addHeader("Authorization", auth)
                                .build();
                        client.newCall(timeReq).enqueue(new okhttp3.Callback() {
                            @Override
                            public void onFailure(okhttp3.Call call, IOException e) {
                                Log.e(TAG, "[TOGGL] Błąd pobierania time_entries: " + e.getMessage());
                            }
                            @Override
                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                if (!response.isSuccessful()) {
                                    Log.e(TAG, "[TOGGL] Błąd pobierania time_entries: " + response.message());
                                    return;
                                }
                                String timeBody = response.body().string();
                                try {
                                    org.json.JSONArray timeEntries = new org.json.JSONArray(timeBody);
                                    for (int i = 0; i < timeEntries.length(); i++) {
                                        org.json.JSONObject entry = timeEntries.getJSONObject(i);
                                        String entryId = entry.optString("id", null);
                                        String workspaceIdStr = entry.optString("workspace_id", null);
                                        String docId = (workspaceIdStr != null ? workspaceIdStr + "_" : "") + (entryId != null ? entryId : "brak_id");
                                        if (entryId != null) {
                                            Map<String, Object> data = new java.util.HashMap<>();
                                            java.util.Iterator<String> keys = entry.keys();
                                            while (keys.hasNext()) {
                                                String key = keys.next();
                                                Object value = entry.opt(key);
                                                if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Map || value instanceof java.util.List) {
                                                    data.put(key, value);
                                                } else if (value != null && (value instanceof org.json.JSONArray || value instanceof org.json.JSONObject)) {
                                                    data.put(key, value.toString());
                                                }
                                            }
                                            Log.d(TAG, "[TOGGL] Dane do zapisu (time_entry): " + data.toString());
                                            firestore.collection("users").document(user.getUid())
                                                    .collection("toggl_time_entries").document(docId)
                                                    .set(data)
                                                    .addOnSuccessListener(unused -> Log.d(TAG, "[TOGGL] Zapisano time_entry: " + docId))
                                                    .addOnFailureListener(e -> Log.e(TAG, "[TOGGL] Błąd zapisu time_entry: " + e.getMessage(), e));
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "[TOGGL] Błąd parsowania time_entries: " + e.getMessage(), e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "[TOGGL] Błąd parsowania me: " + e.getMessage(), e);
                    }
                }
            });
        });
    }

    // Pomocnicza metoda do usuwania i zapisywania projektów dla workspace
    private void saveProjectsForWorkspace(org.json.JSONArray projects, String workspaceIdStr, org.json.JSONArray clients) {
        firestore.collection("users").document(user.getUid())
                .collection("toggl_projects").document(workspaceIdStr)
                .collection("projects").get()
                .addOnSuccessListener(querySnapshot -> {
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                        count++;
                    }
                    Log.d(TAG, "[TOGGL] Usunięto " + count + " stare projekty z workspace_id=" + workspaceIdStr);
                    // Zapisz nowe projekty z tego workspace_id
                    for (int i = 0; i < projects.length(); i++) {
                        try {
                            org.json.JSONObject innerProject = projects.getJSONObject(i);
                            String innerProjectId = innerProject.optString("id", null);
                            String wsId = innerProject.optString("workspace_id", null);
                            if (innerProjectId != null && wsId != null && wsId.equals(workspaceIdStr)) {
                                Map<String, Object> data = new java.util.HashMap<>();
                                java.util.Iterator<String> keys = innerProject.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    Object value = innerProject.opt(key);
                                    if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Map || value instanceof java.util.List) {
                                        data.put(key, value);
                                    } else if (value != null && (value instanceof org.json.JSONArray || value instanceof org.json.JSONObject)) {
                                        data.put(key, value.toString());
                }
                                }
                                // Jeśli nie ma client_name, spróbuj znaleźć po client_id
                                if (!data.containsKey("client_name") && data.containsKey("client_id") && clients != null) {
                                    String clientId = String.valueOf(data.get("client_id"));
                                    for (int c = 0; c < clients.length(); c++) {
                                        try {
                                            org.json.JSONObject cl = clients.getJSONObject(c);
                                            if (cl.optString("id").equals(clientId)) {
                                                String cname = cl.optString("name", null);
                                                if (cname != null) {
                                                    data.put("client_name", cname);
                                                    Log.d(TAG, "[TOGGL] Uzupełniono client_name: " + cname);
                                                }
                                                break;
                                            }
                                        } catch (Exception ignore) {}
                                    }
                                }
                                if (data.containsKey("client_name")) {
                                    Log.d(TAG, "[TOGGL] Zapis klienta: " + data.get("client_name"));
                                }
                                Log.d(TAG, "[TOGGL] Dane do zapisu (project): workspace_id=" + wsId + ", project_id=" + innerProjectId + ", data=" + data.toString());
                                firestore.collection("users").document(user.getUid())
                                        .collection("toggl_projects").document(wsId)
                                        .collection("projects").document(innerProjectId)
                                        .set(data)
                                        .addOnSuccessListener(unused -> Log.d(TAG, "[TOGGL] Zapisano projekt: workspace_id=" + wsId + ", project_id=" + innerProjectId + " (" + data.getOrDefault("name", "brak nazwy") + ")"))
                                        .addOnFailureListener(e -> Log.e(TAG, "[TOGGL] Błąd zapisu projektu: " + e.getMessage(), e));
                    } else {
                                Log.w(TAG, "[TOGGL] Pominięto projekt bez workspace_id lub id: " + innerProject.toString());
                            }
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "[TOGGL] Błąd parsowania projektu: " + e.getMessage(), e);
                    }
                }
            });
    }

    private void checkAsanaConnection() {
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    boolean isConnected = document.contains("asanaToken") && 
                                        document.getBoolean("asanaConnected") == Boolean.TRUE;
                    if (isConnected) {
                        loadAsanaTasks();
                    } else {
                        Toast.makeText(this, 
                            "Połącz najpierw konto z Asana. Kliknij ikonę profilu w prawym górnym rogu.", 
                            Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                    "Błąd podczas sprawdzania połączenia z Asana", 
                    Toast.LENGTH_SHORT).show());
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        if (user == null) return;

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
            .child("profile_images")
            .child(user.getUid() + ".jpg");

        // Dodaj metadata
        StorageMetadata metadata = new StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build();

        storageRef.putFile(imageUri, metadata)
            .addOnSuccessListener(taskSnapshot -> {
                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    // Zaktualizuj URL zdjęcia w Firestore
                    DocumentReference userRef = firestore.collection("users").document(user.getUid());
                    userRef.update("photoUrl", uri.toString())
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(MainActivity.this, "Zdjęcie profilowe zaktualizowane", Toast.LENGTH_SHORT).show();
                            // Odśwież zdjęcie w toolbarze
                            ShapeableImageView profileIcon = findViewById(R.id.profileIcon);
                            Glide.with(this)
                                .load(uri)
                                .placeholder(R.mipmap.ic_launcher)
                                .error(R.mipmap.ic_launcher)
                                .circleCrop()
                                .into(profileIcon);
                            // Odśwież bottom sheet
                            if (profileBottomSheet != null && profileBottomSheet.isShowing()) {
                                profileBottomSheet.dismiss();
                                showProfileBottomSheet();
                            }
                        })
                        .addOnFailureListener(e -> Toast.makeText(MainActivity.this,
                            "Błąd aktualizacji zdjęcia: " + e.getMessage(), Toast.LENGTH_LONG).show());
                });
            })
            .addOnFailureListener(e -> Toast.makeText(MainActivity.this,
                "Błąd przesyłania zdjęcia: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}