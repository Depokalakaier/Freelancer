package com.example.freelancera.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.freelancera.R;
import com.example.freelancera.model.UserSettings;
import com.example.freelancera.util.SettingsManager;

public class SettingsFragment extends Fragment {
    private EditText etCompanyName, etAddress, etNip, etBankAccount, etEmail, etPhone, etHourlyRate, etPaymentDueDays;
    private Spinner spinnerProjectTool, spinnerTimeTrackingTool, spinnerInvoiceTool, spinnerLanguage;
    private Button btnSaveSettings;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        etCompanyName = v.findViewById(R.id.etCompanyName);
        etAddress = v.findViewById(R.id.etAddress);
        etNip = v.findViewById(R.id.etNip);
        etBankAccount = v.findViewById(R.id.etBankAccount);
        etEmail = v.findViewById(R.id.etEmail);
        etPhone = v.findViewById(R.id.etPhone);
        etHourlyRate = v.findViewById(R.id.etHourlyRate);
        etPaymentDueDays = v.findViewById(R.id.etPaymentDueDays);
        spinnerProjectTool = v.findViewById(R.id.spinnerProjectTool);
        spinnerTimeTrackingTool = v.findViewById(R.id.spinnerTimeTrackingTool);
        spinnerInvoiceTool = v.findViewById(R.id.spinnerInvoiceTool);
        spinnerLanguage = v.findViewById(R.id.spinnerLanguage);
        btnSaveSettings = v.findViewById(R.id.btnSaveSettings);

        // Ustaw adaptery spinnerów i pobierz dane z SettingsManager
        // (Dodaj do strings.xml odpowiednie tablice wyboru)

        btnSaveSettings.setOnClickListener(view -> {
            UserSettings settings = new UserSettings();
            settings.setCompanyName(etCompanyName.getText().toString());
            settings.setAddress(etAddress.getText().toString());
            settings.setNip(etNip.getText().toString());
            settings.setBankAccount(etBankAccount.getText().toString());
            settings.setEmail(etEmail.getText().toString());
            settings.setPhone(etPhone.getText().toString());
            settings.setHourlyRate(Double.parseDouble(etHourlyRate.getText().toString()));
            settings.setPaymentDueDays(Integer.parseInt(etPaymentDueDays.getText().toString()));
            settings.setProjectTool(spinnerProjectTool.getSelectedItem().toString());
            settings.setTimeTrackingTool(spinnerTimeTrackingTool.getSelectedItem().toString());
            settings.setInvoiceTool(spinnerInvoiceTool.getSelectedItem().toString());
            settings.setPreferredLanguage(spinnerLanguage.getSelectedItem().toString());
            SettingsManager.saveSettings(getContext(), settings);
            Toast.makeText(getContext(), "Zapisano ustawienia", Toast.LENGTH_SHORT).show();
        });

        return v;
    }
}