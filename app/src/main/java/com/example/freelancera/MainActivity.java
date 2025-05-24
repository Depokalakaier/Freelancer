package com.example.freelancera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = auth.getCurrentUser();

        // ... reszta Twojego kodu (bottom navigation itd.)
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_profile, menu);

        // Dynamicznie ustawiamy nazwę użytkownika i avatar
        MenuItem profileItem = menu.findItem(R.id.action_profile);
        SubMenu subMenu = profileItem.getSubMenu();

        if (user != null) {
            MenuItem usernameItem = subMenu.findItem(R.id.menu_username);
            usernameItem.setTitle(user.getEmail());

            // Jeśli chcesz avatar (jeśli dostępny)
            String photoUrl = (user.getPhotoUrl() != null) ? user.getPhotoUrl().toString() : "";
            if (!photoUrl.isEmpty()) {
                // Własna implementacja pobierania avatara, np. w oknie dialogowym/profilu
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_asana) {
            // Połącz z Asana
            connectWithAsana();
            return true;
        } else if (id == R.id.menu_settings) {
            // Ustawienia
            Toast.makeText(this, "Ustawienia (do zrobienia)", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_logout) {
            // Wyloguj
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connectWithAsana() {
        // Tu implementujesz logikę OAuth z Asana
        // Po uzyskaniu tokena:
        // 1. Zapisz dane integracji w Firestore:
        if (user == null) return;
        String asanaToken = "TUTAJ_TOKEN";
        DocumentReference userRef = firestore.collection("users").document(user.getUid());
        userRef.update("asanaToken", asanaToken);
        Toast.makeText(this, "Połączono z Asana (symulacja)", Toast.LENGTH_SHORT).show();
    }
}