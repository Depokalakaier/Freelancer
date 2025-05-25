package com.example.freelancera.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class TaskDetailsFragment extends Fragment {
    private Task task;
    private TextView taskNameTextView;
    private TextView taskStatusTextView;
    private TextView taskAssigneeTextView;
    private TextView clientTextView;
    private TextView workHoursTextView;
    private TextView hourlyRateTextView;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public static TaskDetailsFragment newInstance(Task task) {
        TaskDetailsFragment fragment = new TaskDetailsFragment();
        Bundle args = new Bundle();
        args.putSerializable("task", task);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            task = (Task) getArguments().getSerializable("task", Task.class);
        }
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                getParentFragmentManager().popBackStack();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_details, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        toolbar.setOnMenuItemClickListener(this::handleMenuItemClick);

        taskNameTextView = view.findViewById(R.id.taskNameTextView);
        taskStatusTextView = view.findViewById(R.id.taskStatusTextView);
        taskAssigneeTextView = view.findViewById(R.id.taskAssigneeTextView);
        clientTextView = view.findViewById(R.id.clientTextView);
        workHoursTextView = view.findViewById(R.id.workHoursTextView);
        hourlyRateTextView = view.findViewById(R.id.hourlyRateTextView);

        updateUI();

        return view;
    }

    private boolean handleMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            showEditDialog();
            return true;
        }
        return false;
    }

    private void updateUI() {
        if (task != null) {
            taskNameTextView.setText(task.getName());
            taskStatusTextView.setText(task.getStatus());
            taskAssigneeTextView.setText(task.getAssignee());
            clientTextView.setText(task.getClient() != null ? task.getClient() : "Brak danych");
            workHoursTextView.setText(task.getWorkHours() != null ? task.getFormattedWorkHours() : "Brak danych");
            hourlyRateTextView.setText(task.getFormattedHourlyRate());
        }
    }

    private void showEditDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_task, null);

        TextInputEditText clientInput = dialogView.findViewById(R.id.clientInput);
        TextInputEditText hourlyRateInput = dialogView.findViewById(R.id.hourlyRateInput);

        clientInput.setText(task.getClient());
        hourlyRateInput.setText(String.valueOf(task.getHourlyRate()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edytuj szczegóły zadania")
                .setView(dialogView)
                .setPositiveButton("Zapisz", (dialog, which) -> {
                    String client = clientInput.getText().toString();
                    String hourlyRateStr = hourlyRateInput.getText().toString();
                    double hourlyRate = 0.0;
                    try {
                        hourlyRate = Double.parseDouble(hourlyRateStr);
                    } catch (NumberFormatException e) {
                        // Ignore parse error and use default 0.0
                    }

                    task.setClient(client.isEmpty() ? null : client);
                    task.setHourlyRate(hourlyRate);

                    // Zapisz zmiany w Firestore
                    if (auth.getCurrentUser() != null) {
                        DocumentReference taskRef = firestore
                            .collection("users")
                            .document(auth.getCurrentUser().getUid())
                            .collection("tasks")
                            .document(task.getId());

                        taskRef.update(
                            "client", task.getClient(),
                            "hourlyRate", task.getHourlyRate()
                        ).addOnSuccessListener(unused -> {
                            updateUI();
                        });
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }
} 