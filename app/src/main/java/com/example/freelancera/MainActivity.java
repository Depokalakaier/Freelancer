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

import com.bumptech.glide.Glide;
import com.example.freelancera.auth.AsanaAuthManager;
import com.example.freelancera.auth.AsanaLoginFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

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
                AsanaAuthManager.handleOAuthCallback(data, this, new AsanaAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(AsanaAuthManager.AuthResult result) {
                        if (user != null) {
                            DocumentReference userRef = firestore.collection("users").document(user.getUid());
                            userRef.update(
                                "asanaToken", result.accessToken,
                                "asanaIdToken", result.idToken,
                                "asanaEmail", result.email,
                                "asanaConnected", true
                            )
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(MainActivity.this, "Połączono z Asana!", Toast.LENGTH_SHORT).show();
                                updateAsanaConnectionUI(true);
                                loadAsanaTasks();
                            })
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, 
                                "Błąd zapisu tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, "Błąd autoryzacji Asana: " + error, Toast.LENGTH_LONG).show();
                        updateAsanaConnectionUI(false);
                    }
                });
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
            // Remove the default title from the Toolbar
            toolbar.setTitle("");
            setSupportActionBar(toolbar);
            
            ShapeableImageView profileIcon = findViewById(R.id.profileIcon);
            profileIcon.setOnClickListener(v -> showProfileBottomSheet());
            
            // Załaduj zdjęcie profilowe jeśli istnieje
            if (user != null) {
                StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                    .child("profile_images")
                    .child(user.getUid() + ".jpg");
                
                storageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Glide.with(this)
                            .load(uri)
                            .circleCrop()
                            .placeholder(R.drawable.ic_account_circle_24)
                            .into(profileIcon);
                    })
                    .addOnFailureListener(e -> {
                        // Użyj domyślnej ikony w przypadku błędu
                        profileIcon.setImageResource(R.drawable.ic_account_circle_24);
                    });
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

        // Ustawienie emaila użytkownika
        if (user != null && user.getEmail() != null) {
            ((android.widget.TextView) bottomSheetView.findViewById(R.id.emailText))
                .setText(user.getEmail());
        }

        // Obsługa zdjęcia profilowego
        profileImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        // Obsługa przycisku Asana
        connectAsanaButton.setOnClickListener(v -> {
            profileBottomSheet.dismiss();
            connectWithAsana();
        });

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
                        updateAsanaConnectionUI(true);
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

    private void disconnectFromAsana() {
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
                updateAsanaConnectionUI(false);
            })
            .addOnFailureListener(e -> Toast.makeText(this, 
                "Błąd podczas rozłączania z Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void updateAsanaConnectionUI(boolean isConnected) {
        MaterialButton connectAsanaButton = findViewById(R.id.connectAsanaButton);
        if (connectAsanaButton != null) {
            if (isConnected) {
                connectAsanaButton.setText("Rozłącz konto Asana");
                connectAsanaButton.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_light));
                connectAsanaButton.setOnClickListener(v -> disconnectFromAsana());
            } else {
                connectAsanaButton.setText("Połącz z Asana");
                connectAsanaButton.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_blue_light));
                connectAsanaButton.setOnClickListener(v -> connectWithAsana());
            }
        }
    }

    private void checkAsanaConnection() {
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    boolean isConnected = document.contains("asanaToken") && 
                                        document.getBoolean("asanaConnected") == Boolean.TRUE;
                    updateAsanaConnectionUI(isConnected);
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

    private void loadAsanaTasks() {
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
                            String responseBody = response.body().string();
                            Log.d(TAG, "Odpowiedź workspaces: " + responseBody);
                            
                            if (response.isSuccessful()) {
                                try {
                                    JSONObject json = new JSONObject(responseBody);
                                    JSONArray data = json.getJSONArray("data");
                                    if (data.length() > 0) {
                                        // Użyj pierwszego workspace do pobrania projektów
                                        JSONObject workspace = data.getJSONObject(0);
                                        String workspaceId = workspace.getString("gid");
                                        Log.d(TAG, "Znaleziono workspace: " + workspaceId + ", pobieram projekty...");
                                        
                                        // Pobierz projekty dla tego workspace
                                        com.example.freelancera.auth.AsanaApi.getProjects(token, new okhttp3.Callback() {
                                            @Override
                                            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                                                Log.e(TAG, "Błąd pobierania projektów: " + e.getMessage(), e);
                                                runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                    "Błąd pobierania projektów: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                            }
                                            
                                            @Override
                                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                                                String responseBody = response.body().string();
                                                Log.d(TAG, "Odpowiedź projekty: " + responseBody);
                                                
                                                if (response.isSuccessful()) {
                                                    try {
                                                        JSONObject json = new JSONObject(responseBody);
                                                        JSONArray projects = json.getJSONArray("data");
                                                        
                                                        if (projects.length() > 0) {
                                                            // Użyj pierwszego projektu do pobrania zadań
                                                            JSONObject project = projects.getJSONObject(0);
                                                            String projectId = project.getString("gid");
                                                            Log.d(TAG, "Znaleziono projekt: " + projectId + ", pobieram zadania...");
                                                            
                                                            // Pobierz zadania dla tego projektu
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
                                                                        runOnUiThread(() -> {
                                                                            try {
                                                                                JSONObject json = new JSONObject(responseBody);
                                                                                JSONArray tasks = json.getJSONArray("data");
                                                                                Toast.makeText(MainActivity.this, 
                                                                                    "Pobrano " + tasks.length() + " zadań z Asana!", 
                                                                                    Toast.LENGTH_SHORT).show();
                                                                                // TODO: Wyświetl zadania w UI
                                                                            } catch (Exception e) {
                                                                                Log.e(TAG, "Błąd parsowania zadań: " + e.getMessage(), e);
                                                                                Toast.makeText(MainActivity.this, 
                                                                                    "Błąd parsowania zadań: " + e.getMessage(), 
                                                                                    Toast.LENGTH_LONG).show();
                                                                            }
                                                                        });
                                                                    } else {
                                                                        Log.e(TAG, "Błąd pobierania zadań: " + response.code() + " - " + responseBody);
                                                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                                            "Błąd pobierania zadań: " + response.message(), 
                                                                            Toast.LENGTH_LONG).show());
                                                                    }
                                                                }
                                                            });
                                                        } else {
                                                            Log.d(TAG, "Brak projektów w workspace");
                                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                                "Brak projektów w workspace", Toast.LENGTH_SHORT).show());
                                                        }
                                                    } catch (Exception e) {
                                                        Log.e(TAG, "Błąd parsowania projektów: " + e.getMessage(), e);
                                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                            "Błąd parsowania projektów: " + e.getMessage(), 
                                                            Toast.LENGTH_LONG).show());
                                                    }
                                                } else {
                                                    Log.e(TAG, "Błąd pobierania projektów: " + response.code() + " - " + responseBody);
                                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                                        "Błąd pobierania projektów: " + response.message(), 
                                                        Toast.LENGTH_LONG).show());
                                                }
                                            }
                                        });
                                    } else {
                                        Log.d(TAG, "Brak dostępnych workspaces");
                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                            "Brak dostępnych workspaces", Toast.LENGTH_SHORT).show());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Błąd parsowania workspaces: " + e.getMessage(), e);
                                    runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                        "Błąd parsowania workspaces: " + e.getMessage(), 
                                        Toast.LENGTH_LONG).show());
                                }
                            } else {
                                Log.e(TAG, "Błąd pobierania workspaces: " + response.code() + " - " + responseBody);
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                                    "Błąd pobierania workspaces: " + response.message(), 
                                    Toast.LENGTH_LONG).show());
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

        if (id == R.id.menu_asana) {
            connectWithAsana();
            return true;
        } else if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}