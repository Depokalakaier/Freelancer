package com.example.freelancera.view;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import com.example.freelancera.model.WorkTime;
import com.example.freelancera.model.Invoice;
import com.example.freelancera.util.JsonLoader;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.textfield.TextInputEditText;
import com.example.freelancera.util.ClockifyManager;
import com.example.freelancera.models.clockify.ClockifyTimeEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TaskDetailFragment extends Fragment {

    private static final String ARG_TASK_ID = "task_id";
    private String taskId;
    private Task task;
    private WorkTime workTime;
    private boolean isWorking = false;
    private long workStartMillis = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int sessionSeconds = 0;

    // Views
    private TextView titleText;
    private TextView descriptionText;
    private TextView statusText;
    private TextView clientText;
    private TextView dueDateText;
    private TextView timeText;
    private TextView amountText;
    private MaterialButton startStopButton;
    private MaterialButton completeButton;
    private MaterialButton saveButton;
    private TextView togglProjectText;
    private TextView togglClientText;
    private TextView clientHintText;
    private TextView timeHeaderText;
    private TextView hoursText;
    private TextView rateText;

    // Przechowuje ostatnio wygenerowaną fakturę
    private Invoice lastInvoice = null;

    private FirebaseFirestore firestore;
    private FirebaseUser user;

    public static TaskDetailFragment newInstance(String taskId) {
        TaskDetailFragment fragment = new TaskDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            taskId = getArguments().getString(ARG_TASK_ID);
        }
        firestore = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_detail, container, false);

        // Initialize views
        titleText = view.findViewById(R.id.text_task_title);
        descriptionText = view.findViewById(R.id.text_task_description);
        clientText = view.findViewById(R.id.text_task_client);
        dueDateText = view.findViewById(R.id.text_task_due_date);
        timeText = view.findViewById(R.id.text_task_time);
        amountText = view.findViewById(R.id.text_task_amount);
        saveButton = view.findViewById(R.id.button_save);
        clientHintText = view.findViewById(R.id.text_task_client_hint);
        timeHeaderText = view.findViewById(R.id.text_task_time_header);
        hoursText = view.findViewById(R.id.text_task_hours);
        rateText = view.findViewById(R.id.text_task_rate);

        // Ukryj status, togglProjectText, togglClientText, startStopButton, completeButton
        if (view.findViewById(R.id.text_task_status) != null) view.findViewById(R.id.text_task_status).setVisibility(View.GONE);
        if (view.findViewById(R.id.text_task_toggl_project) != null) view.findViewById(R.id.text_task_toggl_project).setVisibility(View.GONE);
        if (view.findViewById(R.id.text_task_toggl_client) != null) view.findViewById(R.id.text_task_toggl_client).setVisibility(View.GONE);
        if (view.findViewById(R.id.button_start_stop) != null) view.findViewById(R.id.button_start_stop).setVisibility(View.GONE);
        if (view.findViewById(R.id.button_complete) != null) view.findViewById(R.id.button_complete).setVisibility(View.GONE);

        // Obsługa kliknięcia klienta
        clientText.setOnClickListener(v -> {
            if (task == null) return;
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
            builder.setTitle("Dodaj klienta");
            final android.widget.EditText input = new android.widget.EditText(getContext());
            input.setText(task.getClient() != null && !task.getClient().equals("Brak klienta") ? task.getClient() : "");
            builder.setView(input);
            builder.setPositiveButton("Zapisz", (dialog, which) -> {
                String value = input.getText().toString();
                if (!value.isEmpty()) {
                    clientText.setText("Klient: " + value);
                    clientHintText.setVisibility(View.GONE);
                    task.setClient(value);
                    FirebaseFirestore.getInstance().collection("users").document(user.getUid()).collection("tasks").document(task.getId()).update("client", value);
                }
            });
            builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        // Obsługa kliknięcia stawki
        rateText.setOnClickListener(v -> {
            if (task == null) return;
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
            builder.setTitle("Podaj stawkę za godzinę (PLN)");
            final android.widget.EditText input = new android.widget.EditText(getContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.valueOf(task.getRatePerHour()));
            builder.setView(input);
            builder.setPositiveButton("Zapisz", (dialog, which) -> {
                String value = input.getText().toString().replace(",", ".");
                try {
                    double rate = Double.parseDouble(value);
                    task.setRatePerHour(rate);
                    FirebaseFirestore.getInstance().collection("users").document(user.getUid()).collection("tasks").document(task.getId()).update("ratePerHour", rate);
                    updateUI();
                } catch (Exception ignored) {}
            });
            builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        // Toolbar tylko w szczegółach zadania
        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(task != null ? task.getName() : "");
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadTask();
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveChanges());
        }
    }

    private void loadTask() {
        if (user == null || taskId == null) {
            Toast.makeText(getContext(), "Błąd: Brak dostępu do danych", Toast.LENGTH_LONG).show();
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        task = documentSnapshot.toObject(Task.class);
                        if (task != null) {
                            // Upewnij się że ID jest ustawione
                            task.setId(documentSnapshot.getId());
                            
                            // Upewnij się, że status jest prawidłowy
                            if (task.getStatus() == null) {
                                task.setStatus("Nowe");
                            }
                            
                            updateUI();
                            if (task.isTimerRunning()) {
                                startTimer();
                            }
                        } else {
                            Toast.makeText(getContext(), "Błąd: Nie można załadować zadania", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Błąd: Zadanie nie istnieje", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), 
                        "Błąd podczas ładowania zadania: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
    }

    private void updateUI() {
        if (task == null || getContext() == null) return;
        // Tytuł
        if (titleText != null) titleText.setText(task.getName());
        // Opis
        if (descriptionText != null) {
            String desc = task.getDescription();
            if (desc == null || desc.trim().isEmpty()) {
                descriptionText.setText("Brak opisu\n\n\n");
            } else {
                descriptionText.setText(desc);
            }
        }
        // Klient
        if (clientText != null && clientHintText != null) {
            if (task.getClient() == null || task.getClient().isEmpty() || task.getClient().equals("Brak klienta")) {
                clientText.setText("Klient: Brak");
                clientHintText.setVisibility(View.VISIBLE);
            } else {
                clientText.setText("Klient: " + task.getClient());
                clientHintText.setVisibility(View.GONE);
            }
        }
        // Termin
        if (dueDateText != null && task.getDueDate() != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
            dueDateText.setText("Termin: " + sdf.format(task.getDueDate()));
        }
        // Godziny i stawka
        if (hoursText != null && rateText != null) {
            long sec = task.getTogglTrackedSeconds();
            int hours = (int) (sec / 3600);
            int minutes = (int) ((sec % 3600) / 60);
            double rounded = hours;
            if (minutes >= 16 && minutes <= 44) rounded += 0.5;
            else if (minutes >= 45) rounded += 1.0;
            else if (minutes > 0) rounded += 1.0;
            hoursText.setText("Przepracowane godziny " + String.format(java.util.Locale.getDefault(), "%.1f", rounded));
            rateText.setText("Stawka " + String.format(java.util.Locale.getDefault(), "%.2f", task.getRatePerHour()) + "/h PLN");
        }
    }

    private void updateTimeAndAmount() {
        if (task == null || timeText == null || amountText == null) return;

        long totalSeconds = task.getTotalTimeInSeconds();
        if (task.isTimerRunning()) {
            totalSeconds += (System.currentTimeMillis() - task.getLastStartTime()) / 1000;
        }

        // Format time
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        timeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hours, minutes));

        // Format amount
        double amount = (totalSeconds / 3600.0) * task.getRatePerHour();
        amountText.setText(String.format(Locale.getDefault(), "%.2f PLN", amount));
    }

    private void updateButtons() {
        if (task == null || startStopButton == null || completeButton == null) return;

        boolean isCompleted = "Ukończone".equals(task.getStatus());
        
        startStopButton.setEnabled(!isCompleted);
        startStopButton.setText(task.isTimerRunning() ? "Stop" : "Start");
        
        completeButton.setEnabled(!isCompleted);
        completeButton.setText(isCompleted ? "Ukończone" : "Oznacz jako ukończone");
    }

    private void toggleTimer() {
        if (task == null) return;

        ClockifyManager clockifyManager = ClockifyManager.getInstance(requireContext());
        if (!clockifyManager.isConfigured()) {
            Toast.makeText(getContext(), 
                "Skonfiguruj Clockify w ustawieniach aplikacji", 
                Toast.LENGTH_LONG).show();
            return;
        }

        task.setTimerRunning(!task.isTimerRunning());
        if (task.isTimerRunning()) {
            // Start timer in Clockify
            ClockifyTimeEntry timeEntry = new ClockifyTimeEntry();
            timeEntry.setDescription(task.getName());
            ClockifyTimeEntry.TimeInterval interval = new ClockifyTimeEntry.TimeInterval();
            interval.setStart(new Date());
            timeEntry.setTimeInterval(interval);

            clockifyManager.getApi()
                .createTimeEntry(clockifyManager.getWorkspaceId(), timeEntry)
                .enqueue(new retrofit2.Callback<ClockifyTimeEntry>() {
                    @Override
                    public void onResponse(retrofit2.Call<ClockifyTimeEntry> call, 
                                        retrofit2.Response<ClockifyTimeEntry> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            task.setClockifyTimeEntryId(response.body().getId());
                            task.setLastStartTime(System.currentTimeMillis());
                            startTimer();
                            updateTask();
                        } else {
                            task.setTimerRunning(false);
                            Toast.makeText(getContext(), 
                                "Błąd podczas uruchamiania timera w Clockify", 
                                Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ClockifyTimeEntry> call, Throwable t) {
                        task.setTimerRunning(false);
                        Toast.makeText(getContext(), 
                            "Błąd połączenia z Clockify: " + t.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    }
                });
        } else {
            // Stop timer in Clockify
            if (task.getClockifyTimeEntryId() != null) {
                ClockifyTimeEntry timeEntry = new ClockifyTimeEntry();
                ClockifyTimeEntry.TimeInterval interval = new ClockifyTimeEntry.TimeInterval();
                interval.setEnd(new Date());
                timeEntry.setTimeInterval(interval);

                clockifyManager.getApi()
                    .updateTimeEntry(
                        clockifyManager.getWorkspaceId(), 
                        task.getClockifyTimeEntryId(), 
                        timeEntry)
                    .enqueue(new retrofit2.Callback<ClockifyTimeEntry>() {
                        @Override
                        public void onResponse(retrofit2.Call<ClockifyTimeEntry> call, 
                                            retrofit2.Response<ClockifyTimeEntry> response) {
                            if (response.isSuccessful()) {
                                stopTimer();
                                long elapsedTime = System.currentTimeMillis() - task.getLastStartTime();
                                task.setTotalTimeInSeconds(task.getTotalTimeInSeconds() + (elapsedTime / 1000));
                                task.setClockifyTimeEntryId(null);
                                updateTask();
                            } else {
                                task.setTimerRunning(true);
                                Toast.makeText(getContext(), 
                                    "Błąd podczas zatrzymywania timera w Clockify", 
                                    Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<ClockifyTimeEntry> call, Throwable t) {
                            task.setTimerRunning(true);
                            Toast.makeText(getContext(), 
                                "Błąd połączenia z Clockify: " + t.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        }
                    });
            }
        }
    }

    private void startTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeAndAmount();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    private void completeTask() {
        if (task == null) return;

        if (task.isTimerRunning()) {
            stopTimer();
            long elapsedTime = System.currentTimeMillis() - task.getLastStartTime();
            task.setTotalTimeInSeconds(task.getTotalTimeInSeconds() + (elapsedTime / 1000));
            task.setTimerRunning(false);
        }

        task.setStatus("Ukończone");
        updateTask();
    }

    private void updateTask() {
        if (user == null || task == null) return;

        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .document(task.getId())
                .set(task)
                .addOnSuccessListener(aVoid -> {
                    updateUI();
                    Toast.makeText(getContext(), "Zadanie zaktualizowane", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), 
                        "Błąd podczas aktualizacji zadania: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
    }

    private void generateAndSaveInvoice() {
        double hourlyRate = 100.0; // PLN za godzinę (zmień według uznania)
        if (workTime == null) {
            showInvoiceDialog("Brak danych o czasie pracy dla tego zadania!");
            return;
        }
        double hoursWorked = workTime.getTotalHours();
        double totalAmount = Math.round(hourlyRate * hoursWorked * 100.0) / 100.0;

        // Poprawne daty na Androidzie
        String issueDate, dueDate;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        issueDate = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 14);
        dueDate = sdf.format(cal.getTime());

        Invoice invoice = new Invoice(
                task.getId(),
                task.getName(),
                task.getClient(),
                totalAmount,
                hourlyRate,
                hoursWorked,
                dueDate,
                issueDate,
                false
        );

        JsonLoader.saveInvoice(getContext(), invoice);

        // Zapamiętaj ostatnią fakturę do eksportu PDF!
        lastInvoice = invoice;

        showInvoiceDialog("Faktura wygenerowana!\n\n" +
                "Klient: " + invoice.getClient() +
                "\nZadanie: " + invoice.getTitle() +
                "\nGodzin: " + String.format(Locale.getDefault(), "%.2f", invoice.getHoursWorked()) +
                "\nStawka: " + invoice.getHourlyRate() + " PLN/h" +
                "\nKwota: " + invoice.getTotalAmount() + " PLN" +
                "\nTermin płatności: " + invoice.getDueDate());
    }

    private void showInvoiceDialog(String msg) {
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Faktura")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportInvoiceToPdf(Invoice invoice) {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 400, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = 25;
        int dy = 25;

        paint.setTextSize(14);
        canvas.drawText("Faktura", 100, y, paint); y += dy + 10;
        paint.setTextSize(10);

        canvas.drawText("Data wystawienia: " + invoice.getIssueDate(), 10, y, paint); y += dy;
        canvas.drawText("Termin płatności: " + invoice.getDueDate(), 10, y, paint); y += dy;
        canvas.drawText("Klient: " + invoice.getClient(), 10, y, paint); y += dy;
        canvas.drawText("Zadanie: " + invoice.getTitle(), 10, y, paint); y += dy;
        canvas.drawText("Godzin: " + String.format(Locale.getDefault(), "%.2f", invoice.getHoursWorked()), 10, y, paint); y += dy;
        canvas.drawText("Stawka: " + invoice.getHourlyRate() + " PLN/h", 10, y, paint); y += dy;
        canvas.drawText("Kwota: " + invoice.getTotalAmount() + " PLN", 10, y, paint); y += dy;

        pdfDocument.finishPage(page);

        // Nazwa pliku PDF
        String fileName = "Faktura_" + invoice.getClient() + "_" + invoice.getIssueDate() + ".pdf";
        fileName = fileName.replace(" ", "_");

        // Ścieżka do katalogu Downloads
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File pdfFile = new File(downloadsDir, fileName);

        try {
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.close();
            Toast.makeText(getContext(), "Zapisano PDF: " + pdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // Otwórz PDF po zapisaniu
            openPdfFile(pdfFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Błąd PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        pdfDocument.close();
    }

    private void openPdfFile(File pdfFile) {
        try {
            Uri pdfUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider",
                    pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Nie można otworzyć PDF (brak aplikacji)?", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveChanges() {
        if (task == null || user == null) return;
        // Zapisz aktualny stan task do Firestore
        firestore.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .document(task.getId())
                .set(task)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Zapisano zmiany", Toast.LENGTH_SHORT).show();
                    updateTimeAndAmount();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Błąd zapisu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}