package com.example.freelancera;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.freelancera.auth.AsanaLoginFragment;
import com.example.freelancera.auth.AsanaAuthManager;
import com.example.freelancera.auth.AsanaLoginFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private AsanaAuthManager asanaAuthManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = auth.getCurrentUser();

        // Set up toolbar (with profile icon)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Bottom navigation setup (leave as in your project)
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        // You probably already have a listener for bottomNavigationView here

        // Asana Auth Manager (init, if needed)
        asanaAuthManager = new AsanaAuthManager(this);

        // Optionally, handle Intent extras for Asana redirect result here
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_profile, menu);

        // Set dynamic username in submenu
        MenuItem profileItem = menu.findItem(R.id.action_profile);
        SubMenu subMenu = profileItem.getSubMenu();

        if (user != null) {
            MenuItem usernameItem = subMenu.findItem(R.id.menu_username);
            String displayName = user.getDisplayName();
            String email = user.getEmail();
            String toShow = displayName != null && !displayName.isEmpty() ? displayName : email;
            usernameItem.setTitle(toShow != null ? toShow : "Użytkownik");

            // (Opcjonalnie) Avatar użytkownika - jeśli masz url w Firestore lub FirebaseUser
            // Możesz tu rozbudować o pokazywanie obrazka w oknie dialogowym lub innym miejscu
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_asana) {
            // Połącz konto z Asana
            showAsanaLoginFragment();
            return true;
        } else if (id == R.id.menu_settings) {
            // Ustawienia (możesz dodać nową aktywność/fragment)
            Toast.makeText(this, "Ustawienia (do zrobienia)", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_logout) {
            // Wyloguj użytkownika
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Pokazuje fragment logowania do Asany. Po zakończonej autoryzacji
     * token jest zapisywany do Firestore (dane integracji).
     */
    private void showAsanaLoginFragment() {
        AsanaLoginFragment fragment = new AsanaLoginFragment();
        fragment.setAsanaAuthListener(token -> {
            // Token Asany uzyskany, zapisz do Firestore
            if (user != null) {
                DocumentReference userRef = firestore.collection("users").document(user.getUid());
                userRef.update("asanaToken", token)
                        .addOnSuccessListener(unused -> Toast.makeText(this, "Połączono z Asana!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, "Błąd zapisu tokena Asana: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        fragment.show(getSupportFragmentManager(), "asana_login");
    }
}