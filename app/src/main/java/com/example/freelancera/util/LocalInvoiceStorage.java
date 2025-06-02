package com.example.freelancera.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.freelancera.models.Invoice;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalInvoiceStorage {
    private static final String PREFS_NAME = "local_invoices";
    private static final String KEY_INVOICES = "invoices";

    public static void saveInvoice(Context context, Invoice invoice) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_INVOICES, new HashSet<>()));
        set.removeIf(s -> Invoice.fromJson(s) != null && Invoice.fromJson(s).getId().equals(invoice.getId()));
        set.add(invoice.toJson());
        prefs.edit().putStringSet(KEY_INVOICES, set).apply();
    }

    public static void removeInvoice(Context context, String invoiceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_INVOICES, new HashSet<>()));
        set.removeIf(s -> Invoice.fromJson(s) != null && Invoice.fromJson(s).getId().equals(invoiceId));
        prefs.edit().putStringSet(KEY_INVOICES, set).apply();
    }

    public static List<Invoice> loadLocalInvoices(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_INVOICES, new HashSet<>());
        List<Invoice> result = new ArrayList<>();
        for (String s : set) {
            Invoice invoice = Invoice.fromJson(s);
            if (invoice != null && invoice.isLocalOnly()) result.add(invoice);
        }
        return result;
    }

    public static void updateInvoicesForTask(Context context, String taskId, double newRate) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_INVOICES, new HashSet<>()));
        Set<String> updated = new HashSet<>();
        for (String s : set) {
            Invoice invoice = Invoice.fromJson(s);
            if (invoice != null && invoice.getTaskId().equals(taskId)) {
                invoice.setRatePerHour(newRate);
                updated.add(invoice.toJson());
            } else if (invoice != null) {
                updated.add(s);
            }
        }
        prefs.edit().putStringSet(KEY_INVOICES, updated).apply();
    }

    public static void clearAllInvoices(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_INVOICES).apply();
    }
} 