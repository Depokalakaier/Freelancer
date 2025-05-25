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
        Log.d(TAG, "onNewIntent: action = " + (intent != null ? intent.getAction() : "null"));
        Log.d(TAG, "onNewIntent: categories = " + (intent != null ? intent.getCategories() : "null"));
        Log.d(TAG, "onNewIntent: scheme = " + (intent != null && intent.getData() != null ? intent.getData().getScheme() : "null"));
        Log.d(TAG, "onNewIntent: host = " + (intent != null && intent.getData() != null ? intent.getData().getHost() : "null"));
        Log.d(TAG, "onNewIntent: path = " + (intent != null && intent.getData() != null ? intent.getData().getPath() : "null"));
        Log.d(TAG, "onNewIntent: query = " + (intent != null && intent.getData() != null ? intent.getData().getQuery() : "null"));

        Toast.makeText(this, "onNewIntent wywołane", Toast.LENGTH_LONG).show();

        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            Log.d(TAG, "onNewIntent: URI: " + data.toString());
            Toast.makeText(this, "Callback URI: " + data.toString(), Toast.LENGTH_LONG).show();
            // Automatyczne przechwycenie kodu autoryzacyjnego
            String code = null;
            String state = null;
            try {
                String query = data.getQuery();
                Log.d(TAG, "Query: " + query);
                Toast.makeText(this, "Query: " + query, Toast.LENGTH_LONG).show();
                if (query != null) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        Log.d(TAG, "Parametr: " + param);
                        if (param.startsWith("code=")) {
                            code = param.substring(5);
                            Log.d(TAG, "Znaleziono kod: " + code);
                        } else if (param.startsWith("state=")) {
                            state = param.substring(6);
                            Log.d(TAG, "Znaleziono state: " + state);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Błąd parsowania kodu autoryzacyjnego: " + e.getMessage(), e);
                Toast.makeText(this, "Błąd parsowania kodu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            Log.d(TAG, "Wyciągnięty code: " + code + ", state: " + state);
            Toast.makeText(this, "Code: " + code + ", state: " + state, Toast.LENGTH_LONG).show();
            if (code != null) {
                Log.d(TAG, "Wysyłam request do Asana z code: " + code);
                Toast.makeText(this, "Wysyłam request do Asana z code: " + code, Toast.LENGTH_LONG).show();
                // Ręczna wymiana kodu na token Asana
                OkHttpClient client = new OkHttpClient();
                RequestBody body = new FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", "1210368184403679")
                    .add("client_secret", "37984949b203b0a9ad86bc7b2d1d4d41")
                    .add("redirect_uri", "https://depokalakaier.github.io/Freelancer")
                    .add("code", code)
                    .build();

                Log.d(TAG, "Wysyłam request do Asana z parametrami:");
                Log.d(TAG, "client_id: 1210368184403679");
                Log.d(TAG, "redirect_uri: https://depokalakaier.github.io/Freelancer");
                Log.d(TAG, "code: " + code);

                Request request = new Request.Builder()
                    .url("https://app.asana.com/-/oauth_token")
                    .post(body)
                    .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Błąd połączenia z Asana: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Błąd połączenia z Asana: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Kod odpowiedzi Asana: " + response.code());
                        Log.d(TAG, "Odpowiedź Asana: " + responseBody);
                        
                        if (response.isSuccessful()) {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String accessToken = json.getString("access_token");
                                Log.d(TAG, "Otrzymano token dostępu");
                                
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Połączono z Asana!", Toast.LENGTH_SHORT).show();
                                    // Zapisz token do Firestore
                                    if (user != null) {
                                        firestore.collection("users").document(user.getUid())
                                            .update("asanaToken", accessToken)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Token Asana zapisany w Firestore");
                                                Toast.makeText(MainActivity.this, "Token Asana zapisany", Toast.LENGTH_SHORT).show();
                                                loadAsanaTasks(); // Od razu próbujemy załadować zadania
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Błąd zapisu tokena Asana: " + e.getMessage(), e);
                                                Toast.makeText(MainActivity.this, "Błąd zapisu tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            });
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Błąd parsowania odpowiedzi Asana: " + e.getMessage(), e);
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd parsowania tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        } else {
                            Log.e(TAG, "Błąd odpowiedzi Asana: " + response.code() + " - " + responseBody);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd Asana: " + response.message() + "\n" + responseBody, Toast.LENGTH_LONG).show());
                        }
                    }
                });
            } else {
                Log.e(TAG, "Nie znaleziono kodu autoryzacyjnego w URI");
                Toast.makeText(this, "Nie znaleziono kodu autoryzacyjnego w URI", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "onNewIntent: brak danych w intent");
            Toast.makeText(this, "onNewIntent: brak danych w intent", Toast.LENGTH_LONG).show();
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
            fragment.setAsanaAuthListener(token -> {
                if (user != null) {
                    DocumentReference userRef = firestore.collection("users").document(user.getUid());
                    userRef.update("asanaToken", token)
                            .addOnSuccessListener(unused -> Toast.makeText(this, "Połączono z Asana!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Błąd zapisu tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
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

    private void checkAsanaConnection() {
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.contains("asanaToken")) {
                        // TODO: Załaduj zadania z Asana
                        loadAsanaTasks();
                    } else {
                        // Pokaż komunikat o konieczności połączenia z Asana
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