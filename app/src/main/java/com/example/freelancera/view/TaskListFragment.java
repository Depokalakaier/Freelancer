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
import com.example.freelancera.storage.TaskStorage;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.freelancera.util.CalendarUtils;
import android.widget.Button;
import com.example.freelancera.util.SyncWorker;
import com.example.freelancera.util.InvoiceSyncHelper;
import java.util.function.Consumer;

/**
 * TaskListFragment - fragment wyświetlający listę zadań użytkownika.
 * Obsługuje synchronizację z Asana i Toggl, filtrowanie, sortowanie oraz UI listy zadań.
 */
public class TaskListFragment extends Fragment {
    private static final String TAG = "TaskListFragment";

    // Views
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ChipGroup statusFilterGroup;
    private TextInputEditText searchEditText;
    private LinearLayout filtersPanel;
    private Button filterButton;

    // Adapter
    private TaskAdapter adapter;

    // Data
    private List<Task> allTasks = new ArrayList<>();
    private String currentStatus = "all";

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    private TaskStorage taskStorage;

    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 1001;
    private Task pendingCalendarTask = null;

    /**
     * Konstruktor domyślny.
     */
    public TaskListFragment() {}

    /**
     * Tworzy i inicjalizuje widok fragmentu.
     */
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
        filterButton = view.findViewById(R.id.filterButton);

        // Setup RecyclerView
        setupRecyclerView();

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchAndSyncTasksFromAsana();
            if (getActivity() instanceof com.example.freelancera.MainActivity) {
                ((com.example.freelancera.MainActivity) getActivity()).syncTogglData();
            }
        });
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

        // Setup filters
        setupFilters();

        // Ładuj zadania z lokalnego storage
        allTasks.clear();
        allTasks.addAll(Task.loadTasks(getContext()));
        filterOutPaidCompletedTasks();

        // Obsługa wyszukiwania
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterOutPaidCompletedTasks();
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (filterButton != null && filtersPanel != null) {
            filterButton.setOnClickListener(v -> {
                if (filtersPanel.getVisibility() == View.VISIBLE) {
                    filtersPanel.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        filtersPanel.setVisibility(View.GONE);
                        filtersPanel.setAlpha(1f);
                    });
                } else {
                    filtersPanel.setAlpha(0f);
                    filtersPanel.setVisibility(View.VISIBLE);
                    filtersPanel.animate().alpha(1f).setDuration(200).start();
                }
            });
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        taskStorage = new TaskStorage(requireContext());
        swipeRefreshLayout.setRefreshing(true);
        allTasks.clear();
        allTasks.addAll(Task.loadTasks(getContext()));
        filterOutPaidCompletedTasks();
        if (getActivity() instanceof com.example.freelancera.MainActivity) {
            ((com.example.freelancera.MainActivity) getActivity()).syncTogglData();
        }
    }

    private void loadTasks() {
        if (user == null) {
            Toast.makeText(getContext(), "Nie jesteś zalogowany", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .collection("tasks")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                allTasks.clear();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Task task = document.toObject(Task.class);
                    task.setId(document.getId());
                    task.setContext(getContext());
                    allTasks.add(task);
                    taskStorage.saveTask(task);
                    if (task.isCompletedStatus() && !task.isHasInvoice()) {
                        // handleCompletedTask(task);
                    }
                }
                adapter.updateTasks(allTasks);
                swipeRefreshLayout.setRefreshing(false);
                // Dodaj sprawdzanie przypomnień o fakturach
                if (user != null && getContext() != null) {
                    SyncWorker.checkAndAddInvoiceReminders(getContext(), user.getUid());
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(getContext(), "Błąd podczas ładowania zadań: " + e.getMessage(), Toast.LENGTH_LONG).show();
                swipeRefreshLayout.setRefreshing(false);
                // Load from local storage on failure
                allTasks.clear();
                allTasks.addAll(Task.loadTasks(getContext()));
                adapter.updateTasks(allTasks);
                // Dodaj sprawdzanie przypomnień o fakturach
                if (user != null && getContext() != null) {
                    SyncWorker.checkAndAddInvoiceReminders(getContext(), user.getUid());
                }
            });
    }

    /**
     * Synchronizuje zadania z Asana i Toggl.
     */
    public void fetchAndSyncTasksFromAsana() {
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
                        // --- DODAJ: Po zakończeniu synchronizacji z Asaną, odśwież Toggl ---
                        if (getActivity() instanceof com.example.freelancera.MainActivity) {
                            ((com.example.freelancera.MainActivity) getActivity()).syncTogglData();
                        }
                    } else {
                        Log.e(TAG, "fetchAndSyncTasksFromAsana: no Asana token found");
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Brak tokenu Asany. Połącz konto w ustawieniach.", Toast.LENGTH_LONG).show();
                        loadTasksFromFirestore(user.getUid());
                        // Dodaj sprawdzanie przypomnień o fakturach
                        if (user != null && getContext() != null) {
                            SyncWorker.checkAndAddInvoiceReminders(getContext(), user.getUid());
                        }
                    }
                } else {
                    Log.e(TAG, "fetchAndSyncTasksFromAsana: user document doesn't exist");
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Błąd: Nie znaleziono dokumentu użytkownika", Toast.LENGTH_LONG).show();
                    loadTasksFromFirestore(user.getUid());
                    // Dodaj sprawdzanie przypomnień o fakturach
                    if (user != null && getContext() != null) {
                        SyncWorker.checkAndAddInvoiceReminders(getContext(), user.getUid());
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "fetchAndSyncTasksFromAsana: error getting user document", e);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd przy pobieraniu tokenu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                loadTasksFromFirestore(user.getUid());
                // Dodaj sprawdzanie przypomnień o fakturach
                if (user != null && getContext() != null) {
                    SyncWorker.checkAndAddInvoiceReminders(getContext(), user.getUid());
                }
            });
        // Po zakończeniu synchronizacji, nadpisz całą lokalną bazę zadań
        taskStorage.clearAllTasks();
        for (Task task : allTasks) {
            taskStorage.saveTask(task);
        }

        // Po zakończeniu odświeżania zadań:
        InvoiceSyncHelper.syncInvoicesWithTasks(getContext(), firestore, user, () -> {
            // Możesz dodać tu np. Toast lub log
        }, invoices -> {
            // Możesz dodać tu logikę odświeżenia UI faktur, jeśli chcesz
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
                            .url("https://app.asana.com/api/1.0/tasks/" + gid + "?opt_fields=gid,name,completed,due_on,created_at,projects,assignee,notes")
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
            // Dodaj description
            String description = task.optString("notes", "");
            Log.d("ASANA_DESCRIPTION", "Zadanie: " + task.getString("name") + ", description: " + description);
            taskMap.put("description", description);
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
            if (task.has("completed_at") && !task.isNull("completed_at")) {
                // Jeśli completed_at jest, spróbuj sparsować na Date
                String completedAtStr = task.getString("completed_at");
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                    java.util.Date completedAtDate = sdf.parse(completedAtStr);
                    taskMap.put("completedAt", completedAtDate);
                } catch (Exception e) {
                    taskMap.put("completedAt", new java.util.Date()); // fallback: dziś
                }
            } else if (completed) {
                // Jeśli zadanie ukończone, ale nie ma completed_at, zapisz dzisiejszą datę
                taskMap.put("completedAt", new java.util.Date());
            } else {
                taskMap.put("completedAt", null);
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
        if (!isAdded() || getContext() == null) {
            Log.e(TAG, "loadTasksFromFirestore: fragment not attached");
            return;
        }
        firestore.collection("users").document(uid)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded() || getContext() == null) return;
                    allTasks.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Task task = document.toObject(Task.class);
                        task.setId(document.getId());
                        task.setContext(getContext());
                        allTasks.add(task);
                        taskStorage.saveTask(task);
                        if (task.isCompletedStatus() && !task.isHasInvoice()) {
                            // handleCompletedTask(task);
                        }
                    }
                    firestore.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                        if (!isAdded() || getContext() == null) return;
                        String togglToken = userDoc.getString("togglToken");
                        if (togglToken == null || togglToken.isEmpty()) {
                            if (!isAdded() || getContext() == null) return;
                            requireActivity().runOnUiThread(() -> {
                                filterOutPaidCompletedTasks();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                            return;
                        }
                        // Pobierz workspaceId z API Toggl
                        OkHttpClient client = new OkHttpClient();
                        String auth = okhttp3.Credentials.basic(togglToken, "api_token");
                        Request meReq = new Request.Builder()
                                .url("https://api.track.toggl.com/api/v9/me")
                                .addHeader("Authorization", auth)
                                .build();
                        client.newCall(meReq).enqueue(new okhttp3.Callback() {
                            @Override
                            public void onFailure(okhttp3.Call call, IOException e) {
                                requireActivity().runOnUiThread(() -> {
                                    filterOutPaidCompletedTasks();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                            @Override
                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                                if (!response.isSuccessful()) {
                                    requireActivity().runOnUiThread(() -> {
                                        filterOutPaidCompletedTasks();
                                        swipeRefreshLayout.setRefreshing(false);
                                    });
                                    return;
                                }
                                String body = response.body().string();
                                try {
                                    org.json.JSONObject me = new org.json.JSONObject(body);
                                    long workspaceId = me.getLong("default_workspace_id");
                                    // Teraz pobierz projekty z Firestore
                                    firestore.collection("users").document(uid)
                                        .collection("toggl_projects").document(String.valueOf(workspaceId))
                                        .collection("projects")
                                        .get()
                                        .addOnSuccessListener(querySnapshot -> {
                                            List<org.json.JSONObject> togglProjects = new ArrayList<>();
                                            for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                                togglProjects.add(new org.json.JSONObject(doc.getData()));
                                                    }
                                            org.json.JSONArray togglProjectsArray = new org.json.JSONArray(togglProjects);
                                            mergeAsanaWithTogglProjects(togglProjectsArray, null, uid);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Błąd pobierania projektów z Firestore: " + e.getMessage(), e);
                                            mergeAsanaWithTogglProjects(new org.json.JSONArray(), null, uid);
                                        });
                                                                } catch (Exception e) {
                                                    requireActivity().runOnUiThread(() -> {
                                        filterOutPaidCompletedTasks();
                                                        swipeRefreshLayout.setRefreshing(false);
                                                    });
                                            }
                                        }
                                    });
                    });
                });
    }

    // Nowa metoda: merge Asana <-> Toggl (live z API)
    private void mergeAsanaWithTogglProjects(org.json.JSONArray projects, org.json.JSONArray clients, String uid) {
        try {
            for (Task task : allTasks) {
                for (int p = 0; p < projects.length(); p++) {
                    org.json.JSONObject proj = projects.getJSONObject(p);
                    String togglProjectName = proj.optString("name", null);
                    String togglProjectId = proj.optString("id", null);
                    String togglClientName = proj.optString("client_name", null);
                        String clientId = proj.optString("client_id", null);
                    boolean match = false;
                    // Dopasowanie po nazwie
                    if (togglProjectName != null && togglProjectName.equalsIgnoreCase(task.getName())) {
                        match = true;
                    }
                    // Dopasowanie po ID projektu
                    if (task.getTogglProjectId() != null && togglProjectId != null &&
                        task.getTogglProjectId().equals(togglProjectId)) {
                        match = true;
                    }
                    // Dopasowanie po nazwie klienta
                    if (togglClientName != null && togglClientName.equalsIgnoreCase(task.getClient())) {
                        match = true;
                                }
                    if (match) {
                        // Uzupełnij klienta z projektu Toggl
                        if (togglClientName != null && !togglClientName.isEmpty()) {
                            task.setTogglClientName(togglClientName);
                            task.setClient(togglClientName);
                        }
                        // Ustaw togglProjectId na id projektu z Toggl
                        if (togglProjectId != null) {
                            task.setTogglProjectId(togglProjectId);
                        }
                        // Uzupełnij czas z actual_seconds (jeśli jest)
                        long actualSeconds = proj.optLong("actual_seconds", 0);
                        if (actualSeconds > 0) {
                            task.setTogglTrackedSeconds(actualSeconds);
                        }
                        // Zapisz zaktualizowane zadanie do Firestore
                        firestore.collection("users").document(uid)
                                .collection("tasks").document(task.getId())
                                .set(task)
                                .addOnSuccessListener(unused -> {
                                    Log.d(TAG, "Zaktualizowano zadanie z danymi Toggl (live API): " + task.getName());
                                    // Zapisz też lokalnie!
                                    taskStorage.saveTask(task);
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Błąd zapisu zadania z Toggl (live API): " + e.getMessage()));
                        break; // tylko pierwszy pasujący projekt
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "mergeAsanaWithTogglProjects: " + e.getMessage(), e);
        }
        // ZABEZPIECZENIE przed wywołaniem na niepodpiętym fragmencie
        if (!isAdded() || getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            filterOutPaidCompletedTasks();
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
        filterOutPaidCompletedTasks();
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
            filterOutPaidCompletedTasks();
        });
    }

    private void filterOutPaidCompletedTasks() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                .collection("invoices")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> paidTaskIds = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String status = doc.getString("status");
                        String taskId = doc.getString("taskId");
                        if ("PAID".equalsIgnoreCase(status) && taskId != null) {
                            paidTaskIds.add(taskId);
                        }
                    }
                    List<Task> filtered = new ArrayList<>();
                    for (Task t : allTasks) {
                        // UKRYJ tylko te zadania, które są ukończone i mają powiązaną fakturę PAID
                        if (!(t.isCompletedStatus() && paidTaskIds.contains(t.getId()))) {
                            filtered.add(t);
        }
                    }
                    // Sortowanie i aktualizacja adaptera
                    filtered.sort((t1, t2) -> {
                        String s1 = getTaskStatusForFilter(t1);
                        String s2 = getTaskStatusForFilter(t2);
                        boolean t1Completed = t1.isCompletedStatus();
                        boolean t2Completed = t2.isCompletedStatus();
                        if ("Ukończone".equals(currentStatus)) {
                            if (t1Completed && !t2Completed) return -1;
                            if (!t1Completed && t2Completed) return 1;
                        } else {
                            if (t1Completed && !t2Completed) return 1;
                            if (!t1Completed && t2Completed) return -1;
                        }
                        if (currentStatus.equals(s1) && !currentStatus.equals(s2)) return -1;
                        if (!currentStatus.equals(s1) && currentStatus.equals(s2)) return 1;
                        if (t1.getDueDate() != null && t2.getDueDate() != null) {
                            return t1.getDueDate().compareTo(t2.getDueDate());
                        }
                        if (t1.getDueDate() != null) return -1;
                        if (t2.getDueDate() != null) return 1;
            return 0;
        });
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                            adapter.updateTasks(filtered);
                swipeRefreshLayout.setRefreshing(false);
            });
        }
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            adapter.updateTasks(allTasks);
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                });
        }
    }

    // Pomocnicza metoda do filtrowania statusów zgodnie z logiką adaptera
    private String getTaskStatusForFilter(Task task) {
        if (task.isCompletedStatus()) return "Ukończone";
        if (task.getDueDate() != null) {
            java.util.Calendar calDue = java.util.Calendar.getInstance();
            calDue.setTime(task.getDueDate());
            java.util.Calendar calToday = java.util.Calendar.getInstance();
            boolean isDueToday = calDue.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR)
                    && calDue.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR);
            if (isDueToday) return "Nowe";
            else return "W toku";
        }
        return task.getStatus();
    }

    private void onTaskClick(Task task) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, TaskDetailsFragment.newInstance(task.getId()))
                .addToBackStack(null)
                .commit();
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter(requireContext(), new ArrayList<>(), task -> {
            if (getActivity() instanceof MainActivity) {
                // Save task locally before opening details
                taskStorage.saveTask(task);
                
                // Show task details as bottom sheet
                TaskDetailsFragment bottomSheet = TaskDetailsFragment.newInstance(task.getId());
                bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }
}