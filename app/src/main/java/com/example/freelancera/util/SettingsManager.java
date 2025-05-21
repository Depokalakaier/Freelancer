package com.example.freelancera.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.freelancera.model.UserSettings;

public class SettingsManager {
    private static final String PREFS_NAME = "user_settings";

    public static void saveSettings(Context context, UserSettings settings) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putFloat("hourlyRate", (float) settings.getHourlyRate());
        e.putInt("paymentDueDays", settings.getPaymentDueDays());
        e.putString("companyName", settings.getCompanyName());
        e.putString("address", settings.getAddress());
        e.putString("nip", settings.getNip());
        e.putString("bankAccount", settings.getBankAccount());
        e.putString("email", settings.getEmail());
        e.putString("phone", settings.getPhone());
        e.putString("preferredLanguage", settings.getPreferredLanguage());
        e.putString("projectTool", settings.getProjectTool());
        e.putString("timeTrackingTool", settings.getTimeTrackingTool());
        e.putString("invoiceTool", settings.getInvoiceTool());
        e.apply();
    }

    public static UserSettings loadSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        UserSettings settings = new UserSettings();
        settings.setHourlyRate(prefs.getFloat("hourlyRate", 120f));
        settings.setPaymentDueDays(prefs.getInt("paymentDueDays", 7));
        settings.setCompanyName(prefs.getString("companyName", ""));
        settings.setAddress(prefs.getString("address", ""));
        settings.setNip(prefs.getString("nip", ""));
        settings.setBankAccount(prefs.getString("bankAccount", ""));
        settings.setEmail(prefs.getString("email", ""));
        settings.setPhone(prefs.getString("phone", ""));
        settings.setPreferredLanguage(prefs.getString("preferredLanguage", "PL"));
        settings.setProjectTool(prefs.getString("projectTool", "Asana"));
        settings.setTimeTrackingTool(prefs.getString("timeTrackingTool", "Clockify"));
        settings.setInvoiceTool(prefs.getString("invoiceTool", "InvoiceNinja"));
        return settings;
    }
}