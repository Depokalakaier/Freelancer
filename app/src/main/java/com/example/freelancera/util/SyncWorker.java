package com.example.freelancera.util;

import android.content.Context;
import android.content.ContentValues;
import android.provider.CalendarContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.freelancera.models.Task;
import com.example.freelancera.models.Invoice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.example.freelancera.util.NotificationHelper;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.Calendar;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        CountDownLatch latch = new CountDownLatch(2);
        // 1. Synchronizacja zadań -> faktury
        firestore.collection("users").document(user.getUid())
            .collection("tasks")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    Task task = doc.toObject(Task.class);
                    if (task.getStatus() != null && task.getStatus().startsWith("Ukończone")) {
                        checkAndCreateInvoice(task, firestore, user.getUid());
                    }
                }
                latch.countDown();
            })
            .addOnFailureListener(e -> {
                latch.countDown();
            });
        // 2. Sprawdzanie nieopłaconych faktur i przypomnień
        firestore.collection("users").document(user.getUid())
            .collection("invoices")
            .get()
            .addOnSuccessListener(invoiceSnapshots -> {
                for (QueryDocumentSnapshot invDoc : invoiceSnapshots) {
                    Invoice invoice = invDoc.toObject(Invoice.class);
                    if (invoice == null) continue;
                    if ("PAID".equals(invoice.getStatus()) || invoice.isPaid()) continue;
                    // Sprawdź, czy istnieje przypomnienie na następny dzień o 15:00
                    Context context = getApplicationContext();
                    long calendarId = getPrimaryCalendarId(context);
                    if (calendarId == -1) continue;
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 15);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long startMillis = cal.getTimeInMillis();
                    String eventTitle = "Faktura: " + invoice.getTaskName();
                    String selection = CalendarContract.Events.TITLE + "=? AND " + CalendarContract.Events.DTSTART + "=? AND " + CalendarContract.Events.CALENDAR_ID + "=?";
                    String[] selectionArgs = new String[]{eventTitle, String.valueOf(startMillis), String.valueOf(calendarId)};
                    boolean exists = false;
                    try (android.database.Cursor cursor = context.getContentResolver().query(
                            CalendarContract.Events.CONTENT_URI,
                            new String[]{CalendarContract.Events._ID},
                            selection,
                            selectionArgs,
                            null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            exists = true;
                        }
                    } catch (Exception e) {
                        Log.e("SyncWorker", "Błąd sprawdzania duplikatu przypomnienia", e);
                    }
                    if (!exists) {
                        // Dodaj przypomnienie
                        ContentValues values = new ContentValues();
                        values.put(CalendarContract.Events.DTSTART, startMillis);
                        values.put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000);
                        values.put(CalendarContract.Events.TITLE, eventTitle);
                        values.put(CalendarContract.Events.DESCRIPTION, "Faktura dla: " + invoice.getClientName() + ", zadanie: " + invoice.getTaskName());
                        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
                        context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                        Log.i("SyncWorker", "Dodano przypomnienie do kalendarza dla faktury: " + invoice.getTaskName());
                        // Powiadomienie w aplikacji
                        NotificationHelper.showNotification(context, "Przypomnienie o fakturze", "Dodano przypomnienie o zapłaceniu faktury: " + invoice.getTaskName(), (int) (System.currentTimeMillis() % Integer.MAX_VALUE));
                    }
                }
                latch.countDown();
            })
            .addOnFailureListener(e -> {
                latch.countDown();
            });
        try { latch.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Log.e("SyncWorker", "Timeout", e); }
        return Result.success();
    }

    private void checkAndCreateInvoice(Task task, FirebaseFirestore firestore, String uid) {
        firestore.collection("users").document(uid)
            .collection("invoices")
            .whereEqualTo("taskId", task.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    Log.i("SyncWorker", "Tworzę fakturę dla zadania: " + task.getName());
                    Invoice invoice = new Invoice(task.getId(), task.getClient(), task.getName(), task.getRatePerHour());
                    double hours = (task.getTogglTrackedSeconds() > 0 ? task.getTogglTrackedSeconds() : task.getTotalTimeInSeconds()) / 3600.0;
                    invoice.setHours(hours);
                    invoice.setTotalAmount(hours * task.getRatePerHour());
                    invoice.setIssueDate(task.getCompletedAt() != null ? task.getCompletedAt() : new java.util.Date());
                    java.util.Date due = new java.util.Date(invoice.getIssueDate().getTime() + 7 * 24 * 60 * 60 * 1000);
                    invoice.setDueDate(due);
                    invoice.setStatus("DRAFT");
                    invoice.setPaid(false);
                    firestore.collection("users").document(uid)
                        .collection("invoices")
                        .add(invoice)
                        .addOnSuccessListener(ref -> {
                            Log.i("SyncWorker", "Dodano fakturę do Firestore: " + invoice.getTaskName());
                            addCalendarReminder(getApplicationContext(), invoice);
                        })
                        .addOnFailureListener(e -> Log.e("SyncWorker", "Błąd zapisu faktury: " + e.getMessage()));
                } else {
                    Log.i("SyncWorker", "Faktura już istnieje dla zadania: " + task.getName());
                }
            })
            .addOnFailureListener(e -> Log.e("SyncWorker", "Błąd sprawdzania faktury: " + e.getMessage()));
    }

    private void addCalendarReminder(Context context, Invoice invoice) {
        try {
            long calendarId = getPrimaryCalendarId(context);
            if (calendarId == -1) {
                Log.w("SyncWorker", "Brak domyślnego kalendarza, nie dodano przypomnienia");
                return;
            }
            String eventTitle = "Faktura: " + invoice.getId() + " - Wyślij fakturę";
            // Sprawdź, czy już istnieje wydarzenie z tym tytułem
            String selection = CalendarContract.Events.TITLE + "=? AND " + CalendarContract.Events.DTSTART + ">=?";
            String[] selectionArgs = new String[]{
                eventTitle,
                String.valueOf(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000) // ostatni tydzień
            };
            try (android.database.Cursor cursor = context.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID},
                    selection,
                    selectionArgs,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    Log.i("SyncWorker", "Przypomnienie już istnieje w kalendarzu dla: " + eventTitle);
                    return;
                }
            }
            long startMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000; // następny dzień
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, startMillis);
            values.put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000);
            values.put(CalendarContract.Events.TITLE, eventTitle);
            values.put(CalendarContract.Events.DESCRIPTION, "Faktura dla: " + invoice.getClientName());
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
            context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            Log.i("SyncWorker", "Dodano przypomnienie do kalendarza dla: " + eventTitle);
        } catch (Exception e) {
            Log.e("SyncWorker", "Błąd dodawania przypomnienia do kalendarza", e);
        }
    }

    private long getPrimaryCalendarId(Context context) {
        String[] projection = new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY};
        try (android.database.Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    int isPrimary = 0;
                    int idx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY);
                    if (idx != -1) isPrimary = cursor.getInt(idx);
                    if (isPrimary == 1) return id;
                }
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
        } catch (Exception e) {
            Log.e("SyncWorker", "Błąd pobierania kalendarza", e);
        }
        return -1;
    }

    /**
     * Sprawdza wszystkie nieopłacone faktury i dodaje przypomnienie w kalendarzu na następny dzień o 15:00,
     * jeśli jeszcze nie istnieje. Wywołuje powiadomienie tylko po dodaniu nowego przypomnienia.
     */
    public static void checkAndAddInvoiceReminders(Context context, String userId) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("users").document(userId)
            .collection("invoices")
            .get()
            .addOnSuccessListener(invoiceSnapshots -> {
                for (QueryDocumentSnapshot invDoc : invoiceSnapshots) {
                    Invoice invoice = invDoc.toObject(Invoice.class);
                    if (invoice == null) continue;
                    if ("PAID".equals(invoice.getStatus()) || invoice.isPaid()) continue;
                    long calendarId = getPrimaryCalendarIdStatic(context);
                    if (calendarId == -1) continue;
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 15);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long startMillis = cal.getTimeInMillis();
                    String eventTitle = "Faktura: " + invoice.getTaskName();
                    String selection = CalendarContract.Events.TITLE + "=? AND " + CalendarContract.Events.DTSTART + "=? AND " + CalendarContract.Events.CALENDAR_ID + "=?";
                    String[] selectionArgs = new String[]{eventTitle, String.valueOf(startMillis), String.valueOf(calendarId)};
                    boolean exists = false;
                    try (android.database.Cursor cursor = context.getContentResolver().query(
                            CalendarContract.Events.CONTENT_URI,
                            new String[]{CalendarContract.Events._ID},
                            selection,
                            selectionArgs,
                            null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            exists = true;
                        }
                    } catch (Exception e) {
                        Log.e("SyncWorker", "[FAKTURY] Błąd sprawdzania duplikatu przypomnienia", e);
                    }
                    if (!exists) {
                        ContentValues values = new ContentValues();
                        values.put(CalendarContract.Events.DTSTART, startMillis);
                        values.put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000);
                        values.put(CalendarContract.Events.TITLE, eventTitle);
                        values.put(CalendarContract.Events.DESCRIPTION, "Faktura dla: " + invoice.getClientName() + ", zadanie: " + invoice.getTaskName());
                        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
                        context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                        Log.i("SyncWorker", "[FAKTURY] Dodano przypomnienie do kalendarza dla: " + invoice.getTaskName() + " (możliwe, że poprzednie zostało usunięte przez użytkownika)");
                        NotificationHelper.showNotification(context, "Przypomnienie o fakturze", "Dodano przypomnienie o zapłaceniu faktury: " + invoice.getTaskName(), (int) (System.currentTimeMillis() % Integer.MAX_VALUE));
                    } else {
                        Log.i("SyncWorker", "[FAKTURY] Przypomnienie już istnieje w kalendarzu dla: " + invoice.getTaskName());
                    }
                }
            });
    }

    // Statyczna wersja getPrimaryCalendarId do użycia w statycznej metodzie
    private static long getPrimaryCalendarIdStatic(Context context) {
        String[] projection = new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY};
        try (android.database.Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    int isPrimary = 0;
                    int idx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY);
                    if (idx != -1) isPrimary = cursor.getInt(idx);
                    if (isPrimary == 1) return id;
                }
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
        } catch (Exception e) {
            Log.e("SyncWorker", "Błąd pobierania kalendarza", e);
        }
        return -1;
    }
} 