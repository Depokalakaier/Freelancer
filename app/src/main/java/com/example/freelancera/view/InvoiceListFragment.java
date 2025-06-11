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
import com.example.freelancera.util.NotificationHelper;
import java.util.function.Consumer;
import com.example.freelancera.util.InvoiceSyncHelper;

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
        InvoiceSyncHelper.syncInvoicesWithTasks(getContext(), firestore, user, () -> {
            adapter.notifyDataSetChanged();
            Toast.makeText(getContext(), "Zsynchronizowano faktury: " + invoices.size(), Toast.LENGTH_SHORT).show();
            Log.i("InvoiceSync", "Zakończono synchronizację. Liczba faktur: " + invoices.size());
        }, invoices -> {
            this.invoices.clear();
            this.invoices.addAll(invoices);
            for (Invoice invoice : invoices) {
                LocalInvoiceStorage.saveInvoice(getContext(), invoice);
                addCalendarReminder(getContext(), invoice);
            }
        });
    }

    public void addCalendarReminder(Context context, Invoice invoice) {
        if (context == null || invoice == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            if (getActivity() != null) {
                pendingCalendarInvoice = invoice;
                ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR},
                    CALENDAR_PERMISSION_REQUEST_CODE);
                Toast.makeText(context, "Poproś o uprawnienia do kalendarza, by dodać przypomnienie", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        long calendarId = getPrimaryCalendarId(context);
        if (calendarId == -1) {
            Toast.makeText(context, "Nie znaleziono domyślnego kalendarza", Toast.LENGTH_SHORT).show();
            Log.w("InvoiceSync", "Brak domyślnego kalendarza, nie dodano przypomnienia");
            addSyncHistory(invoice, "REMINDER_FAILED", "Brak domyślnego kalendarza", false, "Brak domyślnego kalendarza");
            return;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 15);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long startMillis = cal.getTimeInMillis();
        String eventTitle = "Faktura: " + invoice.getId();
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
            addSyncHistory(invoice, "REMINDER_FAILED", "Błąd sprawdzania duplikatu przypomnienia", false, e.getMessage());
        }
        if (exists) {
            Log.i("InvoiceSync", "Przypomnienie dla tej faktury już istnieje, nie dodaję duplikatu: " + invoice.getId());
            return;
        }
        try {
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
            NotificationHelper.showNotification(context, "Przypomnienie o fakturze", "Dodano przypomnienie o zapłaceniu faktury: " + invoice.getId(), (int) (System.currentTimeMillis() % Integer.MAX_VALUE));
        } catch (Exception e) {
            Log.e("InvoiceSync", "Błąd dodawania przypomnienia do kalendarza", e);
            addSyncHistory(invoice, "REMINDER_FAILED", "Błąd dodawania przypomnienia do kalendarza", false, e.getMessage());
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

    private void addSyncHistory(Invoice invoice, String action, String details, boolean success, String errorMessage) {
        if (user == null || firestore == null) return;
        com.example.freelancera.models.SyncHistory history = new com.example.freelancera.models.SyncHistory(
            invoice.getTaskId(),
            invoice.getTaskName(),
            action,
            details,
            success,
            errorMessage
        );
        firestore.collection("users")
                .document(user.getUid())
                .collection("sync_history")
                .add(history);
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