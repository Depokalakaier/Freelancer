package com.example.freelancera.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.adapter.InvoiceAdapter;
import com.example.freelancera.models.Invoice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.freelancera.util.LocalInvoiceStorage;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.Context;
import android.provider.CalendarContract;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

public class InvoiceListFragment extends Fragment {
    private RecyclerView recyclerView;
    private InvoiceAdapter adapter;
    private List<Invoice> invoices = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseUser user;
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 2001;
    private Invoice pendingCalendarInvoice = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invoice_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_invoices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InvoiceAdapter(invoices, position -> {
            Invoice invoice = adapter.getInvoiceAt(position);
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, InvoiceDetailFragment.newInstance(invoice))
                .addToBackStack(null)
                .commit();
        });
        adapter.setOnPaidListener(inv -> {
            Fragment history = requireActivity().getSupportFragmentManager().findFragmentByTag("history");
            if (history instanceof com.example.freelancera.view.HistoryFragment) {
                ((com.example.freelancera.view.HistoryFragment) history).loadPaidInvoices();
            }
        });
        recyclerView.setAdapter(adapter);
        loadInvoices();
    }

    private void loadInvoices() {
        invoices.clear();
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        if (user == null) {
            Toast.makeText(getContext(), "Brak zalogowanego użytkownika!", Toast.LENGTH_SHORT).show();
            Log.i("InvoiceSync", "Brak zalogowanego użytkownika - nie można zsynchronizować faktur");
            return;
        }
        Toast.makeText(getContext(), "Synchronizuję faktury z zadaniami...", Toast.LENGTH_SHORT).show();
        Log.i("InvoiceSync", "Rozpoczynam synchronizację faktur z zadaniami...");
        firestore.collection("users").document(user.getUid())
            .collection("invoices")
            .get()
            .addOnSuccessListener(invoiceSnapshots -> {
                List<String> paidTaskIds = new ArrayList<>();
                for (QueryDocumentSnapshot invDoc : invoiceSnapshots) {
                    Invoice inv = invDoc.toObject(Invoice.class);
                    if ("PAID".equals(inv.getStatus()) || Boolean.TRUE.equals(invDoc.getBoolean("isArchived"))) {
                        paidTaskIds.add(inv.getTaskId());
                        Log.d("InvoiceSync", "Faktura opłacona/zarchiwizowana: " + inv.getId());
                        continue;
                    }
                    invoices.add(inv);
                }
                // Teraz pobierz zadania i generuj faktury tylko dla tych, które nie są opłacone
                firestore.collection("users").document(user.getUid())
                    .collection("tasks")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        int count = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            com.example.freelancera.models.Task task = doc.toObject(com.example.freelancera.models.Task.class);
                            if (task.getStatus() != null && task.getStatus().startsWith("Ukończone") && !paidTaskIds.contains(task.getId())) {
                                // Zawsze generuj/aktualizuj fakturę na podstawie zadania
                                double hours;
                                if (task.getTogglTrackedSeconds() > 0) {
                                    hours = task.getTogglTrackedSeconds() / 3600.0;
                                    Log.d("InvoiceSync", "Użyto czasu z Toggl: " + hours + "h dla zadania: " + task.getName());
                                } else {
                                    hours = task.getTotalTimeInSeconds() / 3600.0;
                                    Log.d("InvoiceSync", "Użyto czasu lokalnego: " + hours + "h dla zadania: " + task.getName());
                                }
                                double rate = task.getRatePerHour();
                                double amount = hours * rate;
                                com.example.freelancera.models.Invoice invoice = null;
                                // Szukaj istniejącej faktury po taskId
                                for (Invoice inv : invoices) {
                                    if (inv.getTaskId() != null && inv.getTaskId().equals(task.getId())) {
                                        invoice = inv;
                                        break;
                                    }
                                }
                                boolean isNewInvoice = (invoice == null);
                                if (isNewInvoice) {
                                    invoice = new com.example.freelancera.models.Invoice(task.getId(), task.getClient(), task.getName(), rate);
                                    invoice.setHours(hours);
                                    invoice.setTotalAmount(amount);
                                    invoice.setId("FV-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new java.util.Date()));
                                    invoice.setLocalOnly(false);
                                }
                                invoice.setTaskName(task.getName());
                                invoice.setClientName(task.getClient());
                                // Zaokrąglanie godzin jak w zadaniach
                                int intHours = (int) hours;
                                int minutes = (int) ((hours - intHours) * 60);
                                double rounded = intHours;
                                if (minutes >= 16 && minutes <= 44) rounded += 0.5;
                                else if (minutes >= 45) rounded += 1.0;
                                else if (minutes > 0) rounded += 1.0;
                                invoice.setHours(rounded);
                                invoice.setRatePerHour(rate);
                                invoice.setTotalAmount(rounded * rate);
                                java.util.Date issue = task.getCompletedAt() != null ? task.getCompletedAt() : new java.util.Date();
                                invoice.setIssueDate(issue);
                                // Termin płatności = 7 dni po dacie zakończenia
                                java.util.Date due = new java.util.Date(issue.getTime() + 7 * 24 * 60 * 60 * 1000);
                                invoice.setDueDate(due);
                                if (!invoices.contains(invoice)) {
                                    invoices.add(invoice);
                                }
                                LocalInvoiceStorage.saveInvoice(getContext(), invoice);
                                if (isNewInvoice) {
                                    // Dodaj tylko nowe faktury do Firestore
                                    final Invoice invoiceToSave = invoice;
                                    firestore.collection("users").document(user.getUid())
                                        .collection("invoices").document(invoiceToSave.getId())
                                        .set(invoiceToSave)
                                        .addOnSuccessListener(unused -> Log.d("InvoiceSync", "Faktura zapisana w Firestore: " + invoiceToSave.getId()))
                                        .addOnFailureListener(e -> Log.e("InvoiceSync", "Błąd zapisu faktury w Firestore: " + e.getMessage()));
                                    Log.d("InvoiceSync", "Dodano nową fakturę: " + invoiceToSave.getId() + ", klient: " + invoiceToSave.getClientName() + ", kwota: " + invoiceToSave.getTotalAmount());
                                } else {
                                    Log.d("InvoiceSync", "Pominięto nadpisanie istniejącej faktury: " + invoice.getId());
                                }
                                count++;
                            }
                        }
                        adapter.notifyDataSetChanged();
                        Toast.makeText(getContext(), "Zsynchronizowano faktury: " + count, Toast.LENGTH_SHORT).show();
                        Log.i("InvoiceSync", "Zakończono synchronizację. Liczba faktur: " + count);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Błąd synchronizacji faktur: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("InvoiceSync", "Błąd synchronizacji faktur: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(getContext(), "Błąd synchronizacji faktur: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("InvoiceSync", "Błąd synchronizacji faktur: " + e.getMessage());
            });
    }

    private void addCalendarReminder(Context context, Invoice invoice) {
        if (context == null || invoice == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            // Poproś o uprawnienia
            if (getActivity() != null) {
                pendingCalendarInvoice = invoice;
                ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR},
                    CALENDAR_PERMISSION_REQUEST_CODE);
                Toast.makeText(context, "Poproś o uprawnienia do kalendarza, by dodać przypomnienie", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        // Pobierz domyślny kalendarz
        long calendarId = getPrimaryCalendarId(context);
        if (calendarId == -1) {
            Toast.makeText(context, "Nie znaleziono domyślnego kalendarza", Toast.LENGTH_SHORT).show();
            Log.w("InvoiceSync", "Brak domyślnego kalendarza, nie dodano przypomnienia");
            return;
        }
        long startMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000; // następny dzień
        String eventTitle = "Faktura: " + invoice.getId();
        // Sprawdź, czy już istnieje przypomnienie z tym tytułem i datą
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
            Log.e("InvoiceSync", "Błąd sprawdzania duplikatu przypomnienia", e);
        }
        if (exists) {
            Log.i("InvoiceSync", "Przypomnienie dla tej faktury już istnieje, nie dodaję duplikatu: " + invoice.getId());
            return;
        }
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000);
        values.put(CalendarContract.Events.TITLE, eventTitle);
        values.put(CalendarContract.Events.DESCRIPTION, "Faktura dla: " + invoice.getClientName() + ", zadanie: " + invoice.getTaskName());
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
        context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
        Toast.makeText(context, "Dodano przypomnienie do kalendarza", Toast.LENGTH_SHORT).show();
        Log.i("InvoiceSync", "Dodano przypomnienie do kalendarza dla faktury: " + invoice.getId());
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
                // Jeśli nie ma primary, zwróć pierwszy
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
        } catch (Exception e) {
            Log.e("InvoiceSync", "Błąd pobierania kalendarza", e);
        }
        return -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingCalendarInvoice != null) {
                    addCalendarReminder(getContext(), pendingCalendarInvoice);
                    pendingCalendarInvoice = null;
                }
            } else {
                Toast.makeText(getContext(), "Brak zgody na dostęp do kalendarza", Toast.LENGTH_SHORT).show();
            }
        }
    }
}