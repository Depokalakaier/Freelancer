package com.example.freelancera.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.freelancera.R;

public class AsanaLoginFragment extends Fragment {
    private static final int OAUTH_REQUEST_CODE = 41;
    private String accessToken = null;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_asana_login, container, false);
        Button btnLogin = v.findViewById(R.id.btnAsanaLogin);
        btnLogin.setOnClickListener(view -> {
            AsanaAuthManager.startAuthorization(getActivity(), OAUTH_REQUEST_CODE);
        });
        return v;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        AsanaAuthManager.handleAuthResponse(requestCode, resultCode, data, getContext(), OAUTH_REQUEST_CODE, new AsanaAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String token) {
                // ZAPISZ token do SharedPreferences lub innego bezpiecznego miejsca
                accessToken = token;
                Toast.makeText(getContext(), "Zalogowano do Asana!", Toast.LENGTH_SHORT).show();
                // Możesz pobierać dane z Asana
            }
            @Override
            public void onError(String error) {
                Toast.makeText(getContext(), "Błąd logowania: " + error, Toast.LENGTH_SHORT).show();
            }
        });
        super.onActivityResult(requestCode, resultCode, data);
    }
}