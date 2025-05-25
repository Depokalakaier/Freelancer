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
import com.example.freelancera.model.Task;
import com.example.freelancera.model.WorkTime;
import com.example.freelancera.model.Invoice;
import com.example.freelancera.util.JsonLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TaskDetailFragment extends Fragment {

    private static final String ARG_TASK_ID = "task_id";
    private Task task;
    private WorkTime workTime;
    private boolean isWorking = false;
    private long workStartMillis = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int sessionSeconds = 0;

    private TextView titleText, descText, statusText, clientText, workTimeText;
    private Button startStopBtn;
    private Button generateInvoiceBtn;
    private Button exportPdfBtn;

    // Przechowuje ostatnio wygenerowaną fakturę
    private Invoice lastInvoice = null;

    public static TaskDetailFragment newInstance(String taskId) {
        TaskDetailFragment fragment = new TaskDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    public TaskDetailFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_detail, container, false);

        titleText = view.findViewById(R.id.text_task_title);
        descText = view.findViewById(R.id.text_task_desc);
        statusText = view.findViewById(R.id.text_task_status);
        clientText = view.findViewById(R.id.text_task_client);
        workTimeText = view.findViewById(R.id.text_work_time);
        startStopBtn = view.findViewById(R.id.btn_start_stop_work);
        generateInvoiceBtn = view.findViewById(R.id.btn_generate_invoice);
        exportPdfBtn = view.findViewById(R.id.btn_export_pdf);

        String taskId = getArguments() != null ? getArguments().getString(ARG_TASK_ID) : null;
        if (taskId != null) {
            task = JsonLoader.findTaskById(getContext(), taskId);
            workTime = JsonLoader.findWorkTimeByTaskId(getContext(), taskId);
        }

        if (task != null) {
            titleText.setText(task.getTitle());
            descText.setText(task.getDescription());
            statusText.setText(task.getStatus());
            clientText.setText(task.getClient());
        }

        updateWorkTimeText();

        startStopBtn.setOnClickListener(v -> {
            if (!isWorking) {
                // START pracy
                isWorking = true;
                workStartMillis = System.currentTimeMillis();
                startStopBtn.setText("Zakończ pracę");
                startTimer();
            } else {
                // STOP pracy
                isWorking = false;
                sessionSeconds += (System.currentTimeMillis() - workStartMillis) / 1000;
                addSessionToWorkTime();
                saveWorkTime();
                startStopBtn.setText("Rozpocznij pracę");
                stopTimer();
            }
        });

        generateInvoiceBtn.setOnClickListener(v -> generateAndSaveInvoice());

        // OBSŁUGA PRZYCISKU EKSPORTU PDF
        exportPdfBtn.setOnClickListener(v -> {
            if (lastInvoice != null) {
                exportInvoiceToPdf(lastInvoice);
            } else {
                Toast.makeText(getContext(), "Najpierw wygeneruj fakturę!", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                int elapsed = sessionSeconds;
                if (isWorking) {
                    elapsed += (System.currentTimeMillis() - workStartMillis) / 1000;
                }
                int totalMinutes = elapsed / 60;
                int hours = totalMinutes / 60 + (workTime != null ? workTime.getHours() : 0);
                int minutes = totalMinutes % 60 + (workTime != null ? workTime.getMinutes() : 0);
                if (minutes >= 60) {
                    hours += minutes / 60;
                    minutes = minutes % 60;
                }
                workTimeText.setText(String.format(Locale.getDefault(),
                        "Czas pracy: %d godz. %d min.", hours, minutes));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        handler.removeCallbacks(timerRunnable);
        updateWorkTimeText();
    }

    private void updateWorkTimeText() {
        int hours = workTime != null ? workTime.getHours() : 0;
        int minutes = workTime != null ? workTime.getMinutes() : 0;
        workTimeText.setText(String.format(Locale.getDefault(),
                "Czas pracy: %d godz. %d min.", hours, minutes));
    }

    private void addSessionToWorkTime() {
        int totalMinutes = sessionSeconds / 60;
        int sessionHours = totalMinutes / 60;
        int sessionMinutes = totalMinutes % 60;

        if (workTime == null) {
            workTime = new WorkTime(task.getId(), sessionHours, sessionMinutes);
        } else {
            int newMinutes = workTime.getMinutes() + sessionMinutes;
            int newHours = workTime.getHours() + sessionHours + newMinutes / 60;
            newMinutes = newMinutes % 60;
            workTime.setHours(newHours);
            workTime.setMinutes(newMinutes);
        }
        sessionSeconds = 0;
    }

    private void saveWorkTime() {
        JsonLoader.saveWorkTime(getContext(), workTime);
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
                task.getTitle(),
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
}