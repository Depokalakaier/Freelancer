package com.example.freelancera;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.freelancera.view.TaskListFragment;
import com.example.freelancera.view.InvoiceFragment;
import com.example.freelancera.view.HistoryFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Freelancer); // Dla płynnego przejścia ze splash screen
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setOnNavigationItemSelectedListener(item -> {
            Fragment fragment;
            if (item.getItemId() == R.id.nav_tasks) {
                fragment = new TaskListFragment();
            } else if (item.getItemId() == R.id.nav_invoices) {
                fragment = new InvoiceFragment();
            } else {
                fragment = new HistoryFragment();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        });

        // Domyślnie pokaż listę zadań
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_tasks);
        }
    }
}