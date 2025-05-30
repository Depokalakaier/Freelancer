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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
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
            toolbar.setTitle("");  // Pusty tytuł, bo mamy własny TextView
            setSupportActionBar(toolbar);
            
            // Ustawienie tytułu w TextView
            TextView toolbarTitle = toolbar.findViewById(R.id.toolbarTitle);
            if (toolbarTitle != null) {
                toolbarTitle.setText(getString(R.string.app_name));
            }

            // Ustawienie ikony profilu
            ShapeableImageView profileIcon = toolbar.findViewById(R.id.profileIcon);
            if (profileIcon != null) {
                profileIcon.setOnClickListener(v -> showProfileBottomSheet());
                
                // Załaduj zdjęcie profilowe jeśli istnieje
                if (user != null && user.getPhotoUrl() != null) {
                    Glide.with(this)
                        .load(user.getPhotoUrl())
                        .placeholder(R.drawable.ic_account_circle_24)
                        .error(R.drawable.ic_account_circle_24)
                        .into(profileIcon);
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
                    // Sprawdź czy użytkownik ma połączone konto Asana
                    checkAsanaConnection();
                    return true;
                } else if (itemId == R.id.navigation_invoices) {
                    // TODO: Implementacja widoku faktur
                    return true;
                } else if (itemId == R.id.navigation_history) {
                    // TODO: Implementacja widoku historii
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
        SwitchMaterial darkModeSwitch = bottomSheetView.findViewById(R.id.darkModeSwitch);
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
            connectTogglButton.setOnClickListener(v -> showTogglApiKeyDialog());
        }

        // Obsługa przełącznika ciemnego motywu
        darkModeSwitch.setChecked(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppCompatDelegate.setDefaultNightMode(
                isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

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
                                                    try {
                                                        org.json.JSONObject projectsJson = new org.json.JSONObject(projectsBody);
                                                        org.json.JSONArray projects = projectsJson.getJSONArray("data");
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
                                                                    Log.d(TAG, "Odpowiedź zadania: " + responseBody);
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

    private void syncLocalChangesToAsana(String token, String projectId) {
        if (user == null) return;

        // Get all tasks that need syncing
        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .whereEqualTo("needsSync", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Task task = document.toObject(Task.class);
                        
                        if (task.getAsanaId() != null) {
                            // Update existing task in Asana
                            updateTaskInAsana(token, task);
                        } else {
                            // Create new task in Asana
                            createTaskInAsana(token, projectId, task);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Błąd pobierania zadań do synchronizacji: " + e.getMessage());
                });
    }

    private void updateTaskInAsana(String token, Task task) {
        JSONObject taskData = new JSONObject();
        try {
            taskData.put("name", task.getName());
            taskData.put("notes", task.getDescription());
            taskData.put("completed", task.getStatus().equals("Ukończone"));
            
            if (task.getDueDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                taskData.put("due_on", sdf.format(task.getDueDate()));
            }

            com.example.freelancera.auth.AsanaApi.updateTask(token, task.getAsanaId(), taskData, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    Log.e(TAG, "Błąd aktualizacji zadania w Asana: " + e.getMessage());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        // Mark task as synced
                        task.setNeedsSync(false);
                        task.setLastSyncDate(new Date());
                        
                        firestore.collection("users")
                                .document(user.getUid())
                                .collection("tasks")
                                .document(task.getId())
                                .set(task);
                                
                        Log.d(TAG, "Zadanie zaktualizowane w Asana: " + task.getName());
                    } else {
                        Log.e(TAG, "Błąd aktualizacji zadania w Asana: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON dla zadania: " + e.getMessage());
        }
    }

    private void createTaskInAsana(String token, String projectId, Task task) {
        JSONObject taskData = new JSONObject();
        try {
            taskData.put("name", task.getName());
            taskData.put("notes", task.getDescription());
            taskData.put("projects", new JSONArray().put(projectId));
            taskData.put("completed", task.getStatus().equals("Ukończone"));
            
            if (task.getDueDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                taskData.put("due_on", sdf.format(task.getDueDate()));
            }

            com.example.freelancera.auth.AsanaApi.createTask(token, taskData, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    Log.e(TAG, "Błąd tworzenia zadania w Asana: " + e.getMessage());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            JSONObject data = json.getJSONObject("data");
                            String asanaId = data.getString("gid");
                            
                            // Update task with Asana ID
                            task.setAsanaId(asanaId);
                            task.setNeedsSync(false);
                            task.setLastSyncDate(new Date());
                            task.setSource("asana");
                            
                            firestore.collection("users")
                                    .document(user.getUid())
                                    .collection("tasks")
                                    .document(task.getId())
                                    .set(task);
                                    
                            Log.d(TAG, "Zadanie utworzone w Asana: " + task.getName());
                        } catch (JSONException e) {
                            Log.e(TAG, "Błąd parsowania odpowiedzi z Asana: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "Błąd tworzenia zadania w Asana: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON dla zadania: " + e.getMessage());
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_profile, menu);

        try {
            // Dynamiczne ustawienie nazwy użytkownika w submenu profilu
            MenuItem profileItem = menu.findItem(R.id.action_profile);
            if (profileItem != null) {
                SubMenu subMenu = profileItem.getSubMenu();
                if (subMenu != null && user != null) {
                    MenuItem usernameItem = subMenu.findItem(R.id.menu_username);
                    if (usernameItem != null) {
                        String displayName = user.getDisplayName();
                        String email = user.getEmail();
                        String toShow = (displayName != null && !displayName.isEmpty()) ? displayName : email;
                        usernameItem.setTitle(toShow != null ? toShow : "Użytkownik");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Błąd podczas tworzenia menu: " + e.getMessage());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showTogglApiKeyDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Wklej swój Toggl API Key");

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Połącz z Toggl")
            .setView(input)
            .setPositiveButton("Zapisz", (dialog, which) -> {
                String apiKey = input.getText().toString();
                saveTogglApiKey(apiKey);
            })
            .setNegativeButton("Anuluj", null)
            .show();
    }

    private void saveTogglApiKey(String apiKey) {
        android.content.SharedPreferences prefs = getSharedPreferences("toggl_prefs", MODE_PRIVATE);
        prefs.edit().putString("api_key", apiKey).apply();
        Toast.makeText(this, "Zapisano API Key Toggl", Toast.LENGTH_SHORT).show();
    }
}