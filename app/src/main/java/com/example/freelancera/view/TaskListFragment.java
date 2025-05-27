package com.example.freelancera.view;

import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
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
import com.example.freelancera.models.Invoice;
import com.example.freelancera.models.SyncHistory;
import com.example.freelancera.models.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class TaskListFragment extends Fragment {
    private static final String TAG = "TaskListFragment";

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private Spinner filterSpinner;
    private List<Task> allTasks = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseUser user;
    private SwipeRefreshLayout swipeRefreshLayout;

    public TaskListFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_list, container, false);

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerViewTasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::fetchTasksFromAsana);
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

        // Initialize status filter
        filterSpinner = view.findViewById(R.id.task_filter_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.task_status_filter,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                showFilteredTasks();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Pobierz zadania z Asany na start
        fetchTasksFromAsana();

        return view;
    }

    private void fetchTasksFromAsana() {
        if (user == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(document -> {
                if (document.contains("asanaToken")) {
                    String token = document.getString("asanaToken");
                    // Pobierz workspaces
                    com.example.freelancera.auth.AsanaApi.getWorkspaces(token, new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            swipeRefreshLayout.post(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Błąd pobierania workspaces: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
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
                                                swipeRefreshLayout.post(() -> {
                                                    swipeRefreshLayout.setRefreshing(false);
                                                    Toast.makeText(getContext(), "Błąd pobierania projektów: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                });
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
                                                                    swipeRefreshLayout.post(() -> {
                                                                        swipeRefreshLayout.setRefreshing(false);
                                                                        Toast.makeText(getContext(), "Błąd pobierania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                                    });
                                                                }
                                                                @Override
                                                                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                                                                    String responseBody = response.body().string();
                                                                    if (response.isSuccessful()) {
                                                                        try {
                                                                            org.json.JSONObject json = new org.json.JSONObject(responseBody);
                                                                            org.json.JSONArray tasks = json.getJSONArray("data");
                                                                            allTasks.clear();
                                                                            for (int i = 0; i < tasks.length(); i++) {
                                                                                org.json.JSONObject taskJson = tasks.getJSONObject(i);
                                                                                com.example.freelancera.models.Task task = com.example.freelancera.models.Task.fromAsanaJson(taskJson);
                                                                                if (task != null && task.getId() != null && !task.getId().isEmpty()) {
                                                                                    allTasks.add(task);
                                                                                }
                                                                            }
                                                                            swipeRefreshLayout.post(() -> {
                                                                                showFilteredTasks();
                                                                                swipeRefreshLayout.setRefreshing(false);
                                                                                Toast.makeText(getContext(), "Pobrano " + allTasks.size() + " zadań z Asana!", Toast.LENGTH_SHORT).show();
                                                                            });
                                                                        } catch (Exception e) {
                                                                            swipeRefreshLayout.post(() -> {
                                                                                swipeRefreshLayout.setRefreshing(false);
                                                                                Toast.makeText(getContext(), "Błąd parsowania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                                            });
                                                                        }
                                                                    } else {
                                                                        swipeRefreshLayout.post(() -> {
                                                                            swipeRefreshLayout.setRefreshing(false);
                                                                            Toast.makeText(getContext(), "Błąd pobierania zadań: " + response.message(), Toast.LENGTH_LONG).show();
                                                                        });
                                                                    }
                                                                }
                                                            });
                                                        } else {
                                                            swipeRefreshLayout.post(() -> {
                                                                swipeRefreshLayout.setRefreshing(false);
                                                                Toast.makeText(getContext(), "Brak projektów w Asana", Toast.LENGTH_LONG).show();
                                                            });
                                                        }
                                                    } catch (Exception e) {
                                                        swipeRefreshLayout.post(() -> {
                                                            swipeRefreshLayout.setRefreshing(false);
                                                            Toast.makeText(getContext(), "Błąd parsowania projektów: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                        });
                                                    }
                                                } else {
                                                    swipeRefreshLayout.post(() -> {
                                                        swipeRefreshLayout.setRefreshing(false);
                                                        Toast.makeText(getContext(), "Błąd pobierania projektów: " + response.message(), Toast.LENGTH_LONG).show();
                                                    });
                                                }
                                            }
                                        });
                                    } else {
                                        swipeRefreshLayout.post(() -> {
                                            swipeRefreshLayout.setRefreshing(false);
                                            Toast.makeText(getContext(), "Brak workspace w Asana", Toast.LENGTH_LONG).show();
                                        });
                                    }
                                } catch (Exception e) {
                                    swipeRefreshLayout.post(() -> {
                                        swipeRefreshLayout.setRefreshing(false);
                                        Toast.makeText(getContext(), "Błąd parsowania workspaces: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                                }
                            } else {
                                swipeRefreshLayout.post(() -> {
                                    swipeRefreshLayout.setRefreshing(false);
                                    Toast.makeText(getContext(), "Błąd pobierania workspaces: " + response.message(), Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Brak tokenu Asana. Połącz najpierw konto.", Toast.LENGTH_LONG).show();
                }
            })
            .addOnFailureListener(e -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd pobierania tokenu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void showFilteredTasks() {
        String filter = filterSpinner.getSelectedItem().toString();
        List<Task> filteredTasks = new ArrayList<>();

        for (Task task : allTasks) {
            if (filter.equals("Wszystkie") || task.getStatus().equals(filter)) {
                filteredTasks.add(task);
            }
        }

        // Sort tasks: New and In Progress first, then Completed
        Collections.sort(filteredTasks, (t1, t2) -> {
            // First New, then In Progress, finally Completed
            List<String> order = List.of("Nowe", "W toku", "Ukończone");
            int i1 = order.indexOf(t1.getStatus());
            int i2 = order.indexOf(t2.getStatus());
            return Integer.compare(i1, i2);
        });

        adapter.updateTasks(filteredTasks);
    }

    private void handleCompletedTask(Task task) {
        // Sprawdź czy mamy dane z Clockify
        if (!task.isHasClockifyTime()) {
            addSyncHistory(task, "CLOCKIFY_CHECK", "Nie masz autoryzacji z Clockify", false, 
                "Połącz konto Clockify aby automatycznie pobierać czas pracy");
        }

        // Generuj fakturę
        if (!task.isHasInvoice()) {
            Invoice invoice = task.generateInvoice();
            firestore.collection("users")
                    .document(user.getUid())
                    .collection("invoices")
                    .add(invoice)
                    .addOnSuccessListener(documentReference -> {
                        // Zaktualizuj task
                        task.setHasInvoice(true);
                        firestore.collection("users")
                                .document(user.getUid())
                                .collection("tasks")
                                .document(task.getId())
                                .update("hasInvoice", true);

                        // Dodaj wpis do historii
                        addSyncHistory(task, "INVOICE_CREATED", 
                            "Wygenerowano fakturę roboczą", true, null);

                        // Dodaj przypomnienie do kalendarza
                        addCalendarReminder(task);
                    })
                    .addOnFailureListener(e -> {
                        addSyncHistory(task, "INVOICE_CREATION_FAILED", 
                            "Błąd generowania faktury", false, e.getMessage());
                    });
        }
    }

    private void addCalendarReminder(Task task) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1); // Przypomnienie na następny dzień

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "Wyślij fakturę: " + task.getName())
                .putExtra(CalendarContract.Events.DESCRIPTION, 
                    "Wyślij fakturę do klienta: " + task.getClient())
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, 
                    calendar.getTimeInMillis() + 60 * 60 * 1000)
                .putExtra(CalendarContract.Events.ALL_DAY, false)
                .putExtra(CalendarContract.Events.HAS_ALARM, true);

        try {
            startActivity(intent);
            addSyncHistory(task, "REMINDER_ADDED", 
                "Dodano przypomnienie do kalendarza", true, null);
        } catch (Exception e) {
            addSyncHistory(task, "REMINDER_FAILED", 
                "Błąd dodawania przypomnienia", false, e.getMessage());
        }
    }

    private void addSyncHistory(Task task, String action, String details, 
                              boolean success, String errorMessage) {
        if (user == null) return;

        SyncHistory history = new SyncHistory(
            task.getId(),
            task.getName(),
            action,
            details,
            success,
            errorMessage
        );

        firestore.collection("users")
                .document(user.getUid())
                .collection("sync_history")
                .add(history)
                .addOnFailureListener(e -> 
                    Toast.makeText(getContext(), 
                        "Błąd zapisu historii: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show());
    }
}