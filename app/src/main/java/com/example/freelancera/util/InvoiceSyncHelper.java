package com.example.freelancera.util;

import android.content.Context;
import android.util.Log;
import com.example.freelancera.models.Invoice;
import com.example.freelancera.models.Task;
import com.example.freelancera.view.InvoiceListFragment;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InvoiceSyncHelper {
    public static void syncInvoicesWithTasks(Context context, FirebaseFirestore firestore, FirebaseUser user, Runnable onComplete, Consumer<List<Invoice>> onInvoicesReady) {
        if (user == null || firestore == null || context == null) {
            if (onComplete != null) onComplete.run();
            if (onInvoicesReady != null) onInvoicesReady.accept(new ArrayList<>());
            return;
        }
        firestore.collection("users").document(user.getUid())
            .collection("invoices")
            .get()
            .addOnSuccessListener(invoiceSnapshots -> {
                List<Invoice> invoices = new ArrayList<>();
                List<String> paidTaskIds = new ArrayList<>();
                for (QueryDocumentSnapshot invDoc : invoiceSnapshots) {
                    Invoice inv = invDoc.toObject(Invoice.class);
                    if ("PAID".equals(inv.getStatus()) || Boolean.TRUE.equals(invDoc.getBoolean("isArchived"))) {
                        paidTaskIds.add(inv.getTaskId());
                        continue;
                    }
                    invoices.add(inv);
                }
                firestore.collection("users").document(user.getUid())
                    .collection("tasks")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Task task = doc.toObject(Task.class);
                            if (task.getStatus() != null && task.getStatus().startsWith("Ukończone") && !paidTaskIds.contains(task.getId())) {
                                double hours = (task.getTogglTrackedSeconds() > 0) ? task.getTogglTrackedSeconds() / 3600.0 : task.getTotalTimeInSeconds() / 3600.0;
                                double rate = task.getRatePerHour();
                                double amount = hours * rate;
                                Invoice invoice = null;
                                for (Invoice inv : invoices) {
                                    if (inv.getTaskId() != null && inv.getTaskId().equals(task.getId())) {
                                        invoice = inv;
                                        break;
                                    }
                                }
                                boolean isNewInvoice = (invoice == null);
                                if (isNewInvoice) {
                                    invoice = new Invoice(task.getId(), task.getClient(), task.getName(), rate);
                                    invoice.setHours(hours);
                                    invoice.setTotalAmount(amount);
                                    invoice.setId("FV-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new java.util.Date()));
                                    invoice.setLocalOnly(false);
                                    invoices.add(invoice);
                                }
                                invoice.setTaskName(task.getName());
                                invoice.setClientName(task.getClient());
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
                                java.util.Date due = new java.util.Date(issue.getTime() + 7 * 24 * 60 * 60 * 1000);
                                invoice.setDueDate(due);
                                final Invoice invoiceToSave = invoice;
                                firestore.collection("users").document(user.getUid())
                                    .collection("invoices").document(invoiceToSave.getId())
                                    .set(invoiceToSave)
                                    .addOnSuccessListener(unused -> Log.d("InvoiceSyncHelper", "Faktura zapisana: " + invoiceToSave.getId()))
                                    .addOnFailureListener(e -> Log.e("InvoiceSyncHelper", "Błąd zapisu faktury: " + e.getMessage()));
                            }
                        }
                        if (onInvoicesReady != null) onInvoicesReady.accept(invoices);
                        if (onComplete != null) onComplete.run();

                        // Po zakończeniu synchronizacji faktur:
                        firestore.collection("users").document(user.getUid())
                            .collection("invoices")
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                                    String clientName = doc.getString("clientName");
                                    double hours = doc.contains("hours") ? doc.getDouble("hours") : -1;
                                    double ratePerHour = doc.contains("ratePerHour") ? doc.getDouble("ratePerHour") : -1;
                                    double totalAmount = doc.contains("totalAmount") ? doc.getDouble("totalAmount") : -1;
                                    if ("Brak klienta".equals(clientName)
                                        && hours == 0
                                        && ratePerHour == 0
                                        && totalAmount == 0) {
                                        doc.getReference().delete();
                                    }
                                }
                            });
                    })
                    .addOnFailureListener(e -> {
                        if (onInvoicesReady != null) onInvoicesReady.accept(new ArrayList<>());
                        if (onComplete != null) onComplete.run();
                    });
            })
            .addOnFailureListener(e -> {
                if (onInvoicesReady != null) onInvoicesReady.accept(new ArrayList<>());
                if (onComplete != null) onComplete.run();
            });
    }
} 