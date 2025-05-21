package com.example.freelancera.controller;

import android.content.Context;
import com.example.freelancera.model.Task;
import com.example.freelancera.model.WorkTime;
import com.example.freelancera.model.Invoice;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.util.JsonLoader;
import com.example.freelancera.util.CalendarUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SyncController {

    private static final double DEFAULT_RATE = 120.0; // domyślna stawka zł/h

    public static void syncTaskCompletion(Context context, Task task) {
        if (!"Ukończone".equals(task.getStatus())) return;

        // Pobierz czas pracy
        WorkTime workTime = JsonLoader.findWorkTimeByTaskId(context, task.getId());
        double hours = workTime != null ? workTime.getTotalHours() : 0;

        // Generuj fakturę
        double amount = hours * DEFAULT_RATE;
        String dueDate = getFutureDate(7);
        Invoice invoice = new Invoice(
                task.getId(),
                task.getClient(),
                amount,
                DEFAULT_RATE,
                hours,
                dueDate,
                false
        );
        // Tu możesz zapisać fakturę do pliku lub bazy

        // Dodaj przypomnienie do kalendarza
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        CalendarUtils.addEventToCalendar(context,
                "Wyślij fakturę do " + task.getClient(),
                "Zadanie: " + task.getTitle(),
                cal.getTimeInMillis()
        );

        // Dodaj wpis do historii
        SyncHistory history = new SyncHistory(
                task.getTitle() + " – faktura utworzona, przypomnienie dodane",
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()),
                "ok"
        );
        // Tu możesz zapisać historię do pliku lub bazy
    }

    private static String getFutureDate(int daysAhead) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead);
        return sdf.format(calendar.getTime());
    }
}