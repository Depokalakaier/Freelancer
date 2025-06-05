package com.example.freelancera.view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.widget.Toolbar;

import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import com.example.freelancera.storage.TaskStorage;
import com.example.freelancera.storage.RateStorage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class TaskDetailsFragment extends BottomSheetDialogFragment {

    private static final String ARG_TASK_ID = "task_id";
    private String taskId;
    private Task task;
    private Handler handler;
    private Runnable updateRunnable;
    private TaskStorage taskStorage;
    private BottomSheetBehavior<View> behavior;

    // Views
    private TextView titleText;
    private TextView descriptionText;
    private TextView clientText;
    private TextView clientHintText;
    private TextView dueDateText;
    private TextView timeText;
    private TextView amountText;
    private TextView hoursText;
    private TextView rateText;
    private MaterialButton saveButton;

    public static TaskDetailsFragment newInstance(String taskId) {
        TaskDetailsFragment fragment = new TaskDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialog);
        if (getArguments() != null) {
            taskId = getArguments().getString(ARG_TASK_ID);
        }
        handler = new Handler(Looper.getMainLooper());
        taskStorage = new TaskStorage(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_details, container, false);

        // Initialize views
        titleText = view.findViewById(R.id.text_task_title);
        descriptionText = view.findViewById(R.id.text_task_description);
        clientText = view.findViewById(R.id.text_task_client);
        clientHintText = view.findViewById(R.id.text_task_client_hint);
        dueDateText = view.findViewById(R.id.text_task_due_date);
        timeText = view.findViewById(R.id.text_task_time);
        amountText = view.findViewById(R.id.text_task_amount);
        hoursText = view.findViewById(R.id.text_task_hours);
        rateText = view.findViewById(R.id.text_task_rate);
        saveButton = view.findViewById(R.id.button_save);

        // Get the bottom sheet behavior
        View bottomSheet = view.findViewById(R.id.bottom_sheet);
        behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Setup toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }

        // Setup click listeners
        setupClickListeners();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadTask();
        startPeriodicUpdates();
    }

    private void setupClickListeners() {
        rateText.setOnClickListener(v -> showRateDialog());
        clientText.setOnClickListener(v -> showClientDialog());
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveChanges());
        }
    }

    private void showRateDialog() {
        if (task == null) return;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Podaj stawkę za godzinę (PLN)");

        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", task.getRatePerHour()));
        builder.setView(input);

        builder.setPositiveButton("Zapisz", (dialog, which) -> {
            try {
                String value = input.getText().toString().trim().replace(",", ".");
                if (!value.isEmpty()) {
                    double rate = Double.parseDouble(value);
                    if (rate >= 0) {
                        updateRate(rate);
                    } else {
                        Toast.makeText(getContext(), "Stawka nie może być ujemna", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Nieprawidłowy format liczby", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateRate(double rate) {
        if (task == null) return;

        task.setRatePerHour(rate);
        
        // Save rate separately
        if (task.getName() != null && !task.getName().isEmpty()) {
            RateStorage.saveProjectRate(requireContext(), task.getName(), rate);
        }
        
        // Update amount
        long seconds = task.getTogglTrackedSeconds() > 0 ? task.getTogglTrackedSeconds() : task.getTotalTimeInSeconds();
        double hours = seconds / 3600.0;
        double amount = hours * rate;
        task.setTotalAmount(amount);
        
        // Save task
        taskStorage.saveTask(task);
        
        // Update UI
        updateUI();
        Toast.makeText(getContext(), "Zapisano stawkę", Toast.LENGTH_SHORT).show();
    }

    private void showClientDialog() {
        if (task == null) return;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Dodaj klienta");
        
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(task.getClient() != null && !task.getClient().equals("Brak klienta") ? task.getClient() : "");
        builder.setView(input);
        
        builder.setPositiveButton("Zapisz", (dialog, which) -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                updateClient(value);
            }
        });
        
        builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateClient(String client) {
        if (task == null) return;

        task.setClient(client);
        taskStorage.saveTask(task);
        updateUI();
        Toast.makeText(getContext(), "Zapisano klienta", Toast.LENGTH_SHORT).show();
    }

    private void loadTask() {
        if (taskId == null) {
            Toast.makeText(getContext(), "Błąd: Brak ID zadania", Toast.LENGTH_LONG).show();
            return;
        }

        // Load task and sync with Firestore
        taskStorage.syncWithFirestore(taskId, success -> {
            if (success) {
                task = taskStorage.getTask(taskId);
                if (task != null && task.getName() != null && !task.getName().isEmpty()) {
                    // Sync rate with Firestore
                    RateStorage.syncWithFirestore(requireContext(), task.getName(), rateSuccess -> {
                        if (rateSuccess) {
                            double savedRate = RateStorage.getProjectRate(requireContext(), task.getName());
                            if (savedRate > 0) {
                                task.setRatePerHour(savedRate);
                                taskStorage.saveTask(task);
                            }
                        }
                        requireActivity().runOnUiThread(this::updateUI);
                    });
                } else {
                    requireActivity().runOnUiThread(this::updateUI);
                }
            } else {
                task = taskStorage.getTask(taskId);
                requireActivity().runOnUiThread(this::updateUI);
            }
        });
    }

    private double roundHours(double rawHours) {
        // Get the whole hours and minutes
        int wholeHours = (int) rawHours;
        double minutesPart = (rawHours - wholeHours) * 60;
        int minutes = (int) minutesPart;

        // Apply rounding rules
        if (minutes >= 0 && minutes <= 15) {
            return wholeHours;
        } else if (minutes >= 16 && minutes <= 44) {
            return wholeHours + 0.5;
        } else {
            return wholeHours + 1;
        }
    }

    private void updateUI() {
        if (task == null || !isAdded()) return;

        titleText.setText(task.getName());
        
        String desc = task.getDescription();
        descriptionText.setText(desc != null && !desc.trim().isEmpty() ? desc : "Brak opisu\n\n\n");

        String client = task.getClient();
        boolean hasClient = client != null && !client.isEmpty() && !client.equals("Brak klienta");
        clientText.setText(hasClient ? "Klient: " + client : "Klient: Brak");
        clientHintText.setVisibility(hasClient ? View.GONE : View.VISIBLE);

        if (task.getDueDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            String dateStr = sdf.format(task.getDueDate());
            
            long daysOverdue = (System.currentTimeMillis() - task.getDueDate().getTime()) / (24 * 60 * 60 * 1000);
            if (daysOverdue > 0) {
                dueDateText.setText(String.format(Locale.getDefault(), 
                    "%d dni po terminie (%s)", daysOverdue, dateStr));
                dueDateText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
            } else {
                dueDateText.setText("Termin: " + dateStr);
                dueDateText.setTextColor(requireContext().getColor(R.color.gray));
            }
        }

        long seconds = task.getTogglTrackedSeconds() > 0 ? task.getTogglTrackedSeconds() : task.getTotalTimeInSeconds();
        double rawHours = seconds / 3600.0;
        double roundedHours = roundHours(rawHours);

        hoursText.setText(String.format(Locale.getDefault(), 
            "Przepracowane godziny %.2f", roundedHours));

        rateText.setText(String.format(Locale.getDefault(), 
            "Stawka %.0f PLN/h", task.getRatePerHour()));

        View rateHintView = getView() != null ? getView().findViewById(R.id.text_hint_add_rate) : null;
        if (rateHintView != null) {
            rateHintView.setVisibility(task.getRatePerHour() > 0 ? View.GONE : View.VISIBLE);
        }

        timeText.setText(String.format(Locale.getDefault(), "%.2f h", roundedHours));

        double amount = roundedHours * task.getRatePerHour();
        task.setTotalAmount(amount);
        amountText.setText(String.format(Locale.getDefault(), "%.2f PLN", amount));
    }

    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    updateUI();
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private void saveChanges() {
        if (task == null) return;

        // Recalculate amount before saving
        long seconds = task.getTogglTrackedSeconds() > 0 ? task.getTogglTrackedSeconds() : task.getTotalTimeInSeconds();
        double hours = seconds / 3600.0;
        double amount = hours * task.getRatePerHour();
        task.setTotalAmount(amount);

        taskStorage.saveTask(task);
        Toast.makeText(getContext(), "Zapisano zmiany", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (task != null) {
            taskStorage.saveTask(task);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (task != null) {
            taskStorage.saveTask(task);
        }
        stopPeriodicUpdates();
    }
} 