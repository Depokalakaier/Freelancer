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
        swipeRefreshLayout.setOnRefreshListener(this::fetchAndSyncTasksFromAsana);
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

        fetchAndSyncTasksFromAsana();

        return view;
    }

    private void fetchAndSyncTasksFromAsana() {
        if (user == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String asanaToken = documentSnapshot.getString("asanaToken");
                    if (asanaToken != null) {
                        fetchTasksFromAsanaAndSave(asanaToken, user.getUid());
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Brak tokenu Asany", Toast.LENGTH_LONG).show();
                    }
                }
            })
            .addOnFailureListener(e -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd przy pobieraniu tokenu", Toast.LENGTH_LONG).show();
            });
    }

    private void fetchTasksFromAsanaAndSave(String token, String uid) {
        OkHttpClient client = new OkHttpClient();
        // Najpierw pobierz workspace (zakładamy pierwszy)
        Request wsRequest = new Request.Builder()
                .url("https://app.asana.com/api/1.0/workspaces")
                .addHeader("Authorization", "Bearer " + token)
                .build();
        client.newCall(wsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                swipeRefreshLayout.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Błąd pobierania workspace: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject wsJson = new JSONObject(response.body().string());
                        JSONArray workspaces = wsJson.getJSONArray("data");
                        if (workspaces.length() > 0) {
                            String workspaceId = workspaces.getJSONObject(0).getString("gid");
                            fetchTasksFromAsana(token, uid, workspaceId);
                        } else {
                            swipeRefreshLayout.post(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Brak workspace w Asana", Toast.LENGTH_LONG).show();
                            });
                        }
                    } catch (JSONException e) {
                        swipeRefreshLayout.post(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Błąd parsowania workspace: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    swipeRefreshLayout.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Błąd pobierania workspace: " + response.message(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void fetchTasksFromAsana(String token, String uid, String workspaceId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
            .url("https://app.asana.com/api/1.0/tasks?assignee=me&workspace=" + workspaceId)
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
                    saveTasksToFirestore(json, uid);
                } else {
                    swipeRefreshLayout.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Błąd API Asana: " + response.code(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void saveTasksToFirestore(String jsonResponse, String uid) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray tasks = jsonObject.getJSONArray("data");
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            CollectionReference taskCollection = db.collection("users").document(uid).collection("tasks");
            deleteOldTasks(taskCollection, () -> {
                for (int i = 0; i < tasks.length(); i++) {
                    try {
                        JSONObject task = tasks.getJSONObject(i);
                        Map<String, Object> taskMap = new HashMap<>();
                        taskMap.put("id", task.getString("gid"));
                        taskMap.put("name", task.getString("name"));
                        // Domyślne wartości, by nie było nulli
                        taskMap.put("status", task.has("completed") && task.getBoolean("completed") ? "Ukończone" : "Nowe");
                        taskMap.put("client", "Brak klienta");
                        taskMap.put("ratePerHour", 0.0);
                        if (task.has("due_on") && !task.isNull("due_on")) {
                            taskMap.put("dueDate", task.getString("due_on"));
                        } else {
                            taskMap.put("dueDate", null);
                        }
                        taskCollection.document(task.getString("gid"))
                            .set(taskMap)
                            .addOnSuccessListener(aVoid -> Log.d("Firestore", "Zapisano zadanie"))
                            .addOnFailureListener(e -> Log.e("Firestore", "Błąd zapisu", e));
                    } catch (JSONException e) {
                        Log.e("JSON", "Błąd parsowania zadania", e);
                    }
                }
                // Po zapisie pobierz i wyświetl
                loadTasksFromFirestore(uid);
            });
        } catch (JSONException e) {
            swipeRefreshLayout.post(() -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd przetwarzania danych z Asany", Toast.LENGTH_LONG).show();
            });
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
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).collection("tasks")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                allTasks.clear();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    String id = doc.getString("id");
                    String name = doc.getString("name");
                    Task task = new Task();
                    task.setId(id);
                    task.setName(name);
                    allTasks.add(task);
                }
                swipeRefreshLayout.setRefreshing(false);
                showFilteredTasks();
            })
            .addOnFailureListener(e -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Błąd pobierania zadań z Firestore", Toast.LENGTH_LONG).show();
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