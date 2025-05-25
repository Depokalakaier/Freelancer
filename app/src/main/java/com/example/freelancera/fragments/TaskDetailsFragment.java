package com.example.freelancera.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class TaskDetailsFragment extends Fragment {
    private Task task;
    private TextView taskNameTextView;
    private TextView taskDescriptionTextView;
    private TextView taskStatusTextView;
    private TextView taskClientTextView;
    private TextView taskDueDateTextView;
    private TextView ratePerHourTextView;
    private EditText ratePerHourInput;
    private Button updateRateButton;
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    public static TaskDetailsFragment newInstance(String taskId) {
        TaskDetailsFragment fragment = new TaskDetailsFragment();
        Bundle args = new Bundle();
        args.putString("taskId", taskId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firestore = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        if (getArguments() != null && getArguments().containsKey("taskId")) {
            String taskId = getArguments().getString("taskId");
            loadTask(taskId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_details, container, false);

        // Inicjalizacja widoków
        taskNameTextView = view.findViewById(R.id.taskNameTextView);
        taskDescriptionTextView = view.findViewById(R.id.taskDescriptionTextView);
        taskStatusTextView = view.findViewById(R.id.taskStatusTextView);
        taskClientTextView = view.findViewById(R.id.taskClientTextView);
        taskDueDateTextView = view.findViewById(R.id.taskDueDateTextView);
        ratePerHourTextView = view.findViewById(R.id.ratePerHourTextView);
        ratePerHourInput = view.findViewById(R.id.ratePerHourInput);
        updateRateButton = view.findViewById(R.id.updateRateButton);

        // Konfiguracja przycisku aktualizacji stawki
        updateRateButton.setOnClickListener(v -> updateRatePerHour());

        return view;
    }

    private void loadTask(String taskId) {
        if (user == null) return;

        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    task = documentSnapshot.toObject(Task.class);
                    if (task != null) {
                        updateUI();
                    }
                })
                .addOnFailureListener(e -> 
                    Toast.makeText(getContext(), 
                        "Błąd podczas ładowania zadania: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show());
    }

    private void updateUI() {
        if (task == null) return;

        taskNameTextView.setText(task.getName());
        taskDescriptionTextView.setText(task.getDescription());
        taskStatusTextView.setText(task.getStatus());
        taskClientTextView.setText(task.getClient());

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        if (task.getDueDate() != null) {
            taskDueDateTextView.setText(sdf.format(task.getDueDate()));
        }

        String rateText = String.format(Locale.getDefault(), "%.2f zł/h", task.getRatePerHour());
        ratePerHourTextView.setText(rateText);
        ratePerHourInput.setText(String.valueOf(task.getRatePerHour()));
    }

    private void updateRatePerHour() {
        if (task == null || user == null) return;

        String rateStr = ratePerHourInput.getText().toString();
        if (TextUtils.isEmpty(rateStr)) {
            Toast.makeText(getContext(), "Podaj stawkę godzinową", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double rate = Double.parseDouble(rateStr);
            if (rate <= 0) {
                Toast.makeText(getContext(), "Stawka musi być większa od 0", Toast.LENGTH_SHORT).show();
                return;
            }

            task.setRatePerHour(rate);
            firestore.collection("users")
                    .document(user.getUid())
                    .collection("tasks")
                    .document(task.getId())
                    .update("ratePerHour", rate)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Zaktualizowano stawkę", Toast.LENGTH_SHORT).show();
                        updateUI();
                    })
                    .addOnFailureListener(e -> 
                        Toast.makeText(getContext(), 
                            "Błąd aktualizacji: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show());

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Nieprawidłowy format stawki", Toast.LENGTH_SHORT).show();
        }
    }
} 