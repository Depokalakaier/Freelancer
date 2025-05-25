package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import com.example.freelancera.R;
import com.example.freelancera.adapter.TaskAdapter;
import com.example.freelancera.models.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private Spinner filterSpinner;
    private List<Task> allTasks = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    public TaskListFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_list, container, false);

        // Inicjalizacja Firebase
        firestore = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        recyclerView = view.findViewById(R.id.recyclerViewTasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Filtrowanie statusu
        filterSpinner = view.findViewById(R.id.task_filter_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.task_status_filter,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        // Załaduj zadania z Firestore
        loadTasksFromFirestore();

        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                showFilteredTasks();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        return view;
    }

    private void loadTasksFromFirestore() {
        if (user == null) return;

        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allTasks.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Task task = document.toObject(Task.class);
                        allTasks.add(task);
                    }
                    showFilteredTasks();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), 
                        "Błąd podczas pobierania zadań: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
    }

    private void showFilteredTasks() {
        String filter = filterSpinner.getSelectedItem().toString();
        List<Task> filtered = new ArrayList<>();
        for (Task t : allTasks) {
            if (filter.equals("Wszystkie") || t.getStatus().equals(filter)) {
                filtered.add(t);
            }
        }
        // Sortowanie: Ukończone na dole, Nowe na górze
        Collections.sort(filtered, (o1, o2) -> {
            // Najpierw Nowe, potem W toku, na końcu Ukończone
            List<String> order = List.of("Nowe", "W toku", "Ukończone");
            int i1 = order.indexOf(o1.getStatus());
            int i2 = order.indexOf(o2.getStatus());
            return Integer.compare(i1, i2);
        });

        adapter = new TaskAdapter(filtered, task -> {
            // Otwórz szczegóły zadania
            TaskDetailFragment fragment = TaskDetailFragment.newInstance(task.getId());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);
    }
}