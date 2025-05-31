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
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.Toast;
import com.example.freelancera.MainActivity;
import com.example.freelancera.R;
import com.example.freelancera.adapter.TaskAdapter;
import com.example.freelancera.models.Invoice;
import com.example.freelancera.models.SyncHistory;
import com.example.freelancera.models.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.util.Log;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TaskListFragment extends Fragment {
    private static final String TAG = "TaskListFragment";

    // Views
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ChipGroup statusFilterGroup;
    private TextInputEditText searchEditText;
    private LinearLayout filtersPanel;
    private TextView btnToggleFilters;

    // Adapter
    private TaskAdapter adapter;

    // Data
    private List<Task> allTasks = new ArrayList<>();
    private String currentStatus = "all";

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    public TaskListFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_list, container, false);

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        // Initialize views
        recyclerView = view.findViewById(R.id.tasks_recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        statusFilterGroup = view.findViewById(R.id.status_filter_group);
        searchEditText = view.findViewById(R.id.search_edit_text);
        filtersPanel = view.findViewById(R.id.filters_panel);
        btnToggleFilters = view.findViewById(R.id.btn_toggle_filters);

        // Setup RecyclerView
        adapter = new TaskAdapter(new ArrayList<>(), task -> {
            if (getActivity() instanceof MainActivity) {
                // Otwórz szczegóły zadania w TaskDetailFragment
                getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, TaskDetailFragment.newInstance(task.getId()))
                    .addToBackStack(null)
                    .commit();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::fetchAndSyncTasksFromAsana);
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

        // Setup filters
        setupFilters();

        // Initial data load - only load local tasks
        loadTasksFromFirestore(user.getUid());

        // Obsługa wyszukiwania
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterAndSortTasks();
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnToggleFilters != null && filtersPanel != null) {
            btnToggleFilters.setOnClickListener(v -> {
                if (filtersPanel.getVisibility() == View.VISIBLE) {
                    filtersPanel.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        filtersPanel.setVisibility(View.GONE);
                        filtersPanel.setAlpha(1f);
                    });
                    btnToggleFilters.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24, 0);
                } else {
                    filtersPanel.setAlpha(0f);
                    filtersPanel.setVisibility(View.VISIBLE);
                    filtersPanel.animate().alpha(1f).setDuration(200).start();
                    btnToggleFilters.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_up_24, 0);
                }
            });
        }

        return view;
    }

    private void fetchAndSyncTasksFromAsana() {
        if (user == null) {
            Log.e(TAG, "fetchAndSyncTasksFromAsana: user is null");
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(getContext(), "Błąd: Użytkownik nie jest zalogowany", Toast.LENGTH_LONG).show();
            return;
        }

        if (getContext() == null) {
            Log.e(TAG, "fetchAndSyncTasksFromAsana: context is null");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        
        Log.d(TAG, "fetchAndSyncTasksFromAsana: starting task sync");
        
        firestore.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String asanaToken = documentSnapshot.getString("asanaToken");
                    if (asanaToken != null) {
                        Log.d(TAG, "fetchAndSyncTasksFromAsana: found Asana token, fetching tasks");
                        fetchTasksFromAsanaAndSave(asanaToken, user.getUid());
                    } else {
                        Log.e(TAG, "fetchAndSyncTasksFromAsana: no Asana token found");
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Brak tokenu Asany. Połącz konto w ustawieniach.", Toast.LENGTH_LONG).show();
                        loadTasksFromFirestore(user.getUid());
                    }
                } else {
                    Log.e(TAG, "fetchAndSyncTasksFromAsana: user document doesn't exist");
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Błąd: Nie znaleziono dokumentu użytkownika", Toast.LENGTH_LONG).show();
                    loadTasksFromFirestore(user.getUid());
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "fetchAndSyncTasksFromAsana: error getting user document", e);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd przy pobieraniu tokenu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                loadTasksFromFirestore(user.getUid());
            });
    }

    private void fetchTasksFromAsanaAndSave(String token, String uid) {
        Log.d(TAG, "fetchTasksFromAsanaAndSave: fetching workspaces");
        OkHttpClient client = new OkHttpClient();
        Request wsRequest = new Request.Builder()
                .url("https://app.asana.com/api/1.0/workspaces")
                .addHeader("Authorization", "Bearer " + token)
                .build();
                
        client.newCall(wsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchTasksFromAsanaAndSave: workspace request failed", e);
                swipeRefreshLayout.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Błąd pobierania workspace: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Spróbuj załadować lokalne zadania
                    loadTasksFromFirestore(uid);
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "fetchTasksFromAsanaAndSave: workspace response: " + responseBody);
                        JSONObject wsJson = new JSONObject(responseBody);
                        JSONArray workspaces = wsJson.getJSONArray("data");
                        if (workspaces.length() > 0) {
                            String workspaceId = workspaces.getJSONObject(0).getString("gid");
                            Log.d(TAG, "fetchTasksFromAsanaAndSave: found workspace: " + workspaceId);
                            fetchTasksFromAsana(token, uid, workspaceId);
                        } else {
                            Log.e(TAG, "fetchTasksFromAsanaAndSave: no workspaces found");
                            swipeRefreshLayout.post(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Brak workspace w Asana", Toast.LENGTH_LONG).show();
                                // Spróbuj załadować lokalne zadania
                                loadTasksFromFirestore(uid);
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "fetchTasksFromAsanaAndSave: error parsing workspace response", e);
                        swipeRefreshLayout.post(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Błąd parsowania workspace: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            // Spróbuj załadować lokalne zadania
                            loadTasksFromFirestore(uid);
                        });
                    }
                } else {
                    Log.e(TAG, "fetchTasksFromAsanaAndSave: workspace request failed with code: " + response.code());
                    String responseBody = response.body().string();
                    Log.e(TAG, "fetchTasksFromAsanaAndSave: error response: " + responseBody);
                    swipeRefreshLayout.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        if (response.code() == 401) {
                            Toast.makeText(getContext(), "Token Asany wygasł. Połącz konto ponownie.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Błąd pobierania workspace: " + response.message(), Toast.LENGTH_LONG).show();
                        }
                        // Spróbuj załadować lokalne zadania
                        loadTasksFromFirestore(uid);
                    });
                }
            }
        });
    }

    private void fetchTasksFromAsana(String token, String uid, String workspaceId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
            .url("https://app.asana.com/api/1.0/tasks?assignee=me&workspace=" + workspaceId + "&opt_fields=gid")
            .addHeader("Authorization", "Bearer " + token)
            .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                swipeRefreshLayout.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Błąd połączenia z Asana: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        JSONArray tasks = jsonObject.getJSONArray("data");
                        if (tasks.length() == 0) {
                            swipeRefreshLayout.post(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Brak zadań w Asana", Toast.LENGTH_LONG).show();
                            });
                            return;
                        }
                        // Pobierz szczegóły każdego zadania
                        fetchTaskDetailsAndSave(tasks, token, uid);
                    } catch (JSONException e) {
                        swipeRefreshLayout.post(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Błąd parsowania listy zadań: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    swipeRefreshLayout.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Błąd API Asana: " + response.code(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void fetchTaskDetailsAndSave(JSONArray tasks, String token, String uid) {
        OkHttpClient client = new OkHttpClient();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference taskCollection = db.collection("users").document(uid).collection("tasks");
        deleteOldTasks(taskCollection, () -> {
            AtomicInteger counter = new AtomicInteger(0);
            for (int i = 0; i < tasks.length(); i++) {
                try {
                    String gid = tasks.getJSONObject(i).getString("gid");
                    Request detailRequest = new Request.Builder()
                            .url("https://app.asana.com/api/1.0/tasks/" + gid + "?opt_fields=gid,name,completed,due_on,created_at,projects,assignee")
                            .addHeader("Authorization", "Bearer " + token)
                            .build();
                    client.newCall(detailRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            if (counter.incrementAndGet() == tasks.length()) {
                                loadTasksFromFirestore(uid);
                            }
                        }
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                try {
                                    JSONObject detailJson = new JSONObject(response.body().string());
                                    JSONObject task = detailJson.getJSONObject("data");
                                    // Pobierz stories i znajdź datę przypisania do użytkownika
                                    fetchAssignmentDateAndSave(task, token, taskCollection, counter, tasks.length(), uid);
                                } catch (JSONException e) {
                                    Log.e("JSON", "Błąd parsowania szczegółów zadania", e);
                                    if (counter.incrementAndGet() == tasks.length()) {
                                        loadTasksFromFirestore(uid);
                                    }
                                }
                            } else {
                                if (counter.incrementAndGet() == tasks.length()) {
                                    loadTasksFromFirestore(uid);
                                }
                            }
                        }
                    });
                } catch (JSONException e) {
                    if (counter.incrementAndGet() == tasks.length()) {
                        loadTasksFromFirestore(uid);
                    }
                }
            }
        });
    }

    private void fetchAssignmentDateAndSave(JSONObject task, String token, CollectionReference taskCollection, AtomicInteger counter, int total, String uid) {
        OkHttpClient client = new OkHttpClient();
        String gid = task.optString("gid");
        Request storiesRequest = new Request.Builder()
                .url("https://app.asana.com/api/1.0/tasks/" + gid + "/stories?opt_fields=type,created_at,resource_subtype,text,assignee")
                .addHeader("Authorization", "Bearer " + token)
                .build();
        client.newCall(storiesRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                saveTaskWithStatus(task, null, taskCollection, counter, total, uid);
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String assignedAt = null;
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray stories = json.getJSONArray("data");
                        for (int i = 0; i < stories.length(); i++) {
                            JSONObject story = stories.getJSONObject(i);
                            if ("system".equals(story.optString("type")) && "assigned".equals(story.optString("resource_subtype"))) {
                                assignedAt = story.optString("created_at");
                                break;
                            }
                        }
                    } catch (JSONException e) {
                        // ignoruj
                    }
                }
                saveTaskWithStatus(task, assignedAt, taskCollection, counter, total, uid);
            }
        });
    }

    private void saveTaskWithStatus(JSONObject task, String createdAt, CollectionReference taskCollection, AtomicInteger counter, int total, String uid) {
        try {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("id", task.getString("gid"));
            taskMap.put("name", task.getString("name"));
            // Status: Ukończone jeśli completed, w przeciwnym razie data utworzenia lub "Nowe"
            String status;
            boolean completed = task.has("completed") && task.getBoolean("completed");
            if (completed) {
                status = "Ukończone";
            } else if (createdAt != null) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                    java.util.Date createdDate = sdf.parse(createdAt);
                    java.util.Calendar cal1 = java.util.Calendar.getInstance();
                    java.util.Calendar cal2 = java.util.Calendar.getInstance();
                    cal2.setTime(createdDate);
                    boolean isToday = cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
                            && cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR);
                    if (isToday) {
                        status = "Nowe";
                    } else {
                        java.text.SimpleDateFormat outFormat = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
                        status = outFormat.format(createdDate);
                    }
                } catch (Exception e) {
                    status = "Nowe";
                }
            } else {
                status = "Nowe";
            }
            taskMap.put("status", status);
            taskMap.put("client", "Brak klienta");
            taskMap.put("ratePerHour", 0.0);
            if (task.has("due_on") && !task.isNull("due_on")) {
                taskMap.put("dueDate", task.getString("due_on"));
            } else {
                taskMap.put("dueDate", null);
            }
            if (createdAt != null) {
                taskMap.put("createdAt", createdAt);
            } else {
                taskMap.put("createdAt", null);
            }
            taskCollection.document(task.getString("gid"))
                    .set(taskMap)
                    .addOnSuccessListener(aVoid -> Log.d("Firestore", "Zapisano zadanie"))
                    .addOnFailureListener(e -> Log.e("Firestore", "Błąd zapisu", e));
        } catch (JSONException e) {
            Log.e("JSON", "Błąd parsowania zadania", e);
        }
        if (counter.incrementAndGet() == total) {
            loadTasksFromFirestore(uid);
        }
    }

    private void deleteOldTasks(CollectionReference taskCollection, Runnable onComplete) {
        taskCollection.get().addOnSuccessListener(querySnapshot -> {
            WriteBatch batch = FirebaseFirestore.getInstance().batch();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().addOnSuccessListener(aVoid -> {
                Log.d("Firestore", "Usunięto stare zadania");
                onComplete.run();
            });
        });
    }

    private void loadTasksFromFirestore(String uid) {
        Log.d(TAG, "loadTasksFromFirestore: loading tasks for user " + uid);
        
        if (getContext() == null) {
            Log.e(TAG, "loadTasksFromFirestore: context is null");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        firestore.collection("users").document(uid)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allTasks.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Task task = document.toObject(Task.class);
                        task.setId(document.getId());
                        allTasks.add(task);
                    }
                    filterAndSortTasks();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadTasksFromFirestore: error loading tasks", e);
                    Toast.makeText(getContext(), "Błąd podczas ładowania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void showFilteredTasks() {
        List<Task> filteredTasks = new ArrayList<>();

        for (Task task : allTasks) {
            if ("all".equals(currentStatus) || task.getStatus().equals(currentStatus)) {
                filteredTasks.add(task);
            }
        }

        // Sort tasks based on current sort settings
        filterAndSortTasks();
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

    private void setupFilters() {
        statusFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) group.getChildAt(i);
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(getResources().getColor(R.color.gray, null));
            }
            com.google.android.material.chip.Chip selectedChip = group.findViewById(checkedId);
            if (selectedChip != null) {
                selectedChip.setChipBackgroundColorResource(android.R.color.white);
                selectedChip.setTextColor(getResources().getColor(android.R.color.black, null));
            }
            if (checkedId == R.id.chip_new) {
                currentStatus = "Nowe";
            } else if (checkedId == R.id.chip_in_progress) {
                currentStatus = "W toku";
            } else if (checkedId == R.id.chip_completed) {
                currentStatus = "Ukończone";
            } else if (checkedId == R.id.chip_due) {
                currentStatus = "Termin";
            }
            filterAndSortTasks();
        });
    }

    private void filterAndSortTasks() {
        List<Task> filteredTasks = new ArrayList<>(allTasks);

        // Filtrowanie po statusie
        if (!"all".equals(currentStatus)) {
            filteredTasks.removeIf(task -> !currentStatus.equals(task.getStatus()) && !(currentStatus.equals("Termin") && task.getDueDate() != null));
        }

        // Filtrowanie po wyszukiwaniu
        if (searchEditText != null && searchEditText.getText() != null) {
            String query = searchEditText.getText().toString().toLowerCase();
            if (!query.isEmpty()) {
                filteredTasks.removeIf(task -> task.getName() == null || !task.getName().toLowerCase().contains(query));
            }
        }

        // Sortowanie: Ukończone na dole, Nowe i W toku na górze
        filteredTasks.sort((t1, t2) -> {
            boolean t1Done = "Ukończone".equals(t1.getStatus());
            boolean t2Done = "Ukończone".equals(t2.getStatus());
            if (t1Done && !t2Done) return 1;
            if (!t1Done && t2Done) return -1;
            // Pozostałe sortuj po dacie utworzenia malejąco
            if (t1.getCreatedAt() == null && t2.getCreatedAt() == null) return 0;
            if (t1.getCreatedAt() == null) return 1;
            if (t2.getCreatedAt() == null) return -1;
            return t2.getCreatedAt().compareTo(t1.getCreatedAt());
        });

        adapter.updateTasks(filteredTasks);
    }
}