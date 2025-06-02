package com.example.freelancera.controller;

import android.content.Context;
import com.example.freelancera.models.Task;
import com.example.freelancera.model.WorkTime;
import com.example.freelancera.model.Invoice;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.util.JsonLoader;
import com.example.freelancera.util.CalendarUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SyncController {

    private static final double DEFAULT_RATE = 120.0; // domyślna stawka zł/h

    public static void syncTaskCompletion(Context context, Task task) {
        if (!"Ukończone".equals(task.getStatus())) return;

        // 1. Pobierz mockowany czas pracy (z symulacji/mocka)
        WorkTime workTime = JsonLoader.findWorkTimeByTaskId(context, task.getId());
        double hours = workTime != null ? workTime.getTotalHours() : 0;

        // 2. Wygeneruj mockowaną fakturę
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
        // TODO: Zapisz fakturę do pliku lub bazy (np. InvoiceRepository.save(invoice))

        // 3. Dodaj przypomnienie do kalendarza (na następny dzień)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        CalendarUtils.addEventToCalendar(
                context,
                "Wyślij fakturę do " + task.getClient(),
                "Zadanie: " + task.getName(),
                cal.getTimeInMillis()
        );

        // 4. Dodaj wpis do historii synchronizacji
        SyncHistory history = new SyncHistory(
                task.getName() + " – faktura utworzona, przypomnienie dodane",
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()),
                "ok"
        );
        // TODO: Zapisz historię do pliku lub bazy (np. SyncHistoryRepository.save(history))
    }

    private static String getFutureDate(int daysAhead) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead);
        return sdf.format(calendar.getTime());
    }
}