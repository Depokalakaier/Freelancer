package com.example.freelancera.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.freelancera.model.Invoice;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.models.Task;
import com.example.freelancera.model.WorkTime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JsonLoader {
    // Zadania z assets
    public static List<Task> loadTasksFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("tasks.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<Task>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static Task findTaskById(Context context, String id) {
        for (Task t : loadTasksFromAssets(context)) {
            if (TextUtils.equals(t.getId(), id)) return t;
        }
        return null;
    }

    // --- WorkTime: odczyt i zapis ---

    public static List<WorkTime> loadWorkTimesFromAppFiles(Context context) {
        try {
            File file = new File(context.getFilesDir(), "worktimes.json");
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                List<WorkTime> list = new Gson().fromJson(new InputStreamReader(fis), new TypeToken<List<WorkTime>>(){}.getType());
                fis.close();
                return list != null ? list : new ArrayList<>();
            } else {
                return loadWorkTimesFromAssets(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return loadWorkTimesFromAssets(context);
        }
    }

    public static List<WorkTime> loadWorkTimesFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("worktimes.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<WorkTime>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveWorkTime(Context context, WorkTime workTime) {
        List<WorkTime> times = new ArrayList<>(loadWorkTimesFromAppFiles(context));
        boolean found = false;
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i).getTaskId().equals(workTime.getTaskId())) {
                times.set(i, workTime);
                found = true;
                break;
            }
        }
        if (!found) times.add(workTime);
        try {
            File file = new File(context.getFilesDir(), "worktimes.json");
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(new Gson().toJson(times));
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static WorkTime findWorkTimeByTaskId(Context context, String taskId) {
        for (WorkTime w : loadWorkTimesFromAppFiles(context)) {
            if (TextUtils.equals(w.getTaskId(), taskId)) return w;
        }
        return null;
    }

    // --- Invoices i SyncHistory ---

    // Zapisuje lub aktualizuje fakturę (jeśli już istnieje dla tego zadania i daty - zamiana, nie duplikat!)
    public static void saveInvoice(Context context, Invoice invoice) {
        List<Invoice> invoices = new ArrayList<>(loadInvoicesFromAppFiles(context));
        boolean updated = false;
        for (int i = 0; i < invoices.size(); i++) {
            Invoice inv = invoices.get(i);
            // Unikalność: po taskId i issueDate (jeśli nie masz unikalnego id faktury)
            if (inv.getTaskId().equals(invoice.getTaskId()) && inv.getIssueDate().equals(invoice.getIssueDate())) {
                invoices.set(i, invoice); // nadpisz istniejącą
                updated = true;
                break;
            }
        }
        if (!updated) {
            invoices.add(invoice);
            if (invoices.isEmpty()) return;
            try {
                File file = new File(context.getFilesDir(), "invoices.json");
                FileOutputStream fos = new FileOutputStream(file);
                OutputStreamWriter writer = new OutputStreamWriter(fos);
                writer.write(new Gson().toJson(invoices));
                writer.close();
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Invoice> loadInvoicesFromAppFiles(Context context) {
        try {
            File file = new File(context.getFilesDir(), "invoices.json");
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                List<Invoice> list = new Gson().fromJson(new InputStreamReader(fis), new TypeToken<List<Invoice>>(){}.getType());
                fis.close();
                return list != null ? list : new ArrayList<>();
            } else {
                return loadInvoicesFromAssets(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return loadInvoicesFromAssets(context);
        }
    }



    public static List<Invoice> loadInvoicesFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("invoices.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<Invoice>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Kopiuje plik invoices.json z assets do files jeśli jeszcze nie istnieje.
     */
    public static void ensureInvoicesFileExists(Context context) {
        Log.d("INVOICE", "ensureInvoicesFileExists wywołane");
        File file = new File(context.getFilesDir(), "invoices.json");
        if (!file.exists()) {
            try (InputStream is = context.getAssets().open("invoices.json");
                 FileOutputStream fos = new FileOutputStream(file)) {
                Log.d("INVOICE", "Kopiuje invoices.json z assets do files");
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("INVOICE", "Blad kopiowania pliku: " + e.getMessage());
            }
        } else {
            Log.d("INVOICE", "Plik invoices.json juz istnieje");
        }
    }

    public static List<SyncHistory> loadHistoryFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("history.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<SyncHistory>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}