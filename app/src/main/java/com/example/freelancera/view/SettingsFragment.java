package com.example.freelancera.view;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import com.example.freelancera.R;
import com.example.freelancera.util.ClockifyManager;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Inicjalizacja ClockifyManager
        ClockifyManager clockifyManager = ClockifyManager.getInstance(requireContext());

        // Nasłuchuj zmian w ustawieniach
        findPreference("clockify_api_key").setOnPreferenceChangeListener((preference, newValue) -> {
            clockifyManager.setApiKey((String) newValue);
            return true;
        });

        findPreference("clockify_workspace_id").setOnPreferenceChangeListener((preference, newValue) -> {
            clockifyManager.setWorkspaceId((String) newValue);
            return true;
        });
    }
} 