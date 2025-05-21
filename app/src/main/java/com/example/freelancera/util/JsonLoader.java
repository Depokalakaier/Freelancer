package com.example.freelancera.util;

import android.content.Context;
import android.text.TextUtils;

import com.example.freelancera.model.Invoice;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.model.Task;
import com.example.freelancera.model.WorkTime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class JsonLoader {
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

    public static List<WorkTime> loadWorkTimesFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("worktimes.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<WorkTime>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static WorkTime findWorkTimeByTaskId(Context context, String taskId) {
        for (WorkTime w : loadWorkTimesFromAssets(context)) {
            if (TextUtils.equals(w.getTaskId(), taskId)) return w;
        }
        return null;
    }

    // DODAJ DO ISTNIEJĄCEGO PLIKU
// Nowe metody do obsługi faktur i historii

    public static List<Invoice> loadInvoicesFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("invoices.json");
            return new Gson().fromJson(new InputStreamReader(is), new TypeToken<List<Invoice>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
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