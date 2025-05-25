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
        swipeRefreshLayout.setOnRefreshListener(this::refreshTasks);
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

        // Load tasks from Firestore
        loadTasksFromFirestore();

        return view;
    }

    private void refreshTasks() {
        loadTasksFromFirestore();
    }

    private void loadTasksFromFirestore() {
        if (user == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
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
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), 
                        "Błąd podczas pobierania zadań: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    swipeRefreshLayout.setRefreshing(false);
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