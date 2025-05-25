package com.example.freelancera.auth;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import com.example.freelancera.R;

public class AsanaLoginFragment extends DialogFragment {
    private static final String TAG = "AsanaLoginFragment";
    private AsanaAuthManager.AuthCallback authCallback;
    private TextView statusTextView;

    public interface AsanaAuthListener {
        void onTokenReceived(String token);
    }

    private AsanaAuthListener asanaAuthListener;

    public void setAsanaAuthListener(AsanaAuthListener listener) {
        this.asanaAuthListener = listener;
    }

    private void updateStatus(String message) {
        Log.d(TAG, message);
        if (statusTextView != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                statusTextView.setText(message);
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(
                R.layout.fragment_asana_login, null, false);

        statusTextView = view.findViewById(R.id.statusTextView);
        updateStatus("Inicjalizacja procesu logowania...");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Łączenie z Asana")
                .setView(view)
                .setNegativeButton("Anuluj", (dialogInterface, i) -> {
                    updateStatus("Anulowano proces logowania");
                    AsanaAuthManager.dispose();
                    dismiss();
                })
                .create();

        try {
            updateStatus("Rozpoczynam autoryzację...");
            // Start OAuth using AppAuth
            AsanaAuthManager.startAuthorization(requireActivity(), 1001);
            updateStatus("Otwieranie przeglądarki z autoryzacją Asana...");
        } catch (Exception e) {
            String errorMsg = "Błąd rozpoczęcia autoryzacji: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            updateStatus(errorMsg);
            
            return new AlertDialog.Builder(requireContext())
                    .setTitle("Błąd połączenia")
                    .setMessage(errorMsg)
                    .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                        try {
                            updateStatus("Ponawiam próbę autoryzacji...");
                            AsanaAuthManager.startAuthorization(requireActivity(), 1001);
                        } catch (Exception retryEx) {
                            updateStatus("Ponowna próba nie powiodła się: " + retryEx.getMessage());
                            dismiss();
                        }
                    })
                    .setNegativeButton("Anuluj", (dialogInterface, which) -> {
                        updateStatus("Anulowano proces logowania");
                        dismiss();
                    })
                    .create();
        }

        // Set up callback for auth result
        authCallback = new AsanaAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String accessToken) {
                updateStatus("Otrzymano token dostępu!");
                if (asanaAuthListener != null) {
                    asanaAuthListener.onTokenReceived(accessToken);
                }
                dismiss();
            }

            @Override
            public void onError(String error) {
                String errorMsg = "Błąd autoryzacji: " + error;
                Log.e(TAG, errorMsg);
                updateStatus(errorMsg);
                
                if (getActivity() != null && !getActivity().isFinishing()) {
                    new AlertDialog.Builder(getActivity())
                        .setTitle("Błąd logowania")
                        .setMessage(errorMsg)
                        .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                            try {
                                updateStatus("Ponawiam próbę autoryzacji...");
                                AsanaAuthManager.startAuthorization(requireActivity(), 1001);
                            } catch (Exception e) {
                                updateStatus("Ponowna próba nie powiodła się: " + e.getMessage());
                                Log.e(TAG, "Error retrying authorization: " + e.getMessage());
                                dismiss();
                            }
                        })
                        .setNegativeButton("Anuluj", (dialogInterface, which) -> {
                            updateStatus("Anulowano proces logowania");
                            dismiss();
                        })
                        .show();
                }
            }
        };

        return dialog;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (data == null) {
                updateStatus("Otrzymano pusty wynik autoryzacji - prawdopodobnie zamknięto przeglądarkę");
                // Użytkownik zamknął przeglądarkę lub anulował logowanie
                if (getActivity() != null && !getActivity().isFinishing()) {
                    new AlertDialog.Builder(getActivity())
                        .setTitle("Błąd logowania")
                        .setMessage("Anulowano logowanie do Asana lub zamknięto przeglądarkę.")
                        .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                            try {
                                updateStatus("Ponawiam próbę autoryzacji...");
                                AsanaAuthManager.startAuthorization(requireActivity(), 1001);
                            } catch (Exception e) {
                                updateStatus("Ponowna próba nie powiodła się: " + e.getMessage());
                                Log.e(TAG, "Error retrying authorization: " + e.getMessage());
                                dismiss();
                            }
                        })
                        .setNegativeButton("Anuluj", (dialogInterface, which) -> {
                            updateStatus("Anulowano proces logowania");
                            dismiss();
                        })
                        .show();
                }
                return;
            }
            updateStatus("Otrzymano odpowiedź z przeglądarki, przetwarzam...");
            AsanaAuthManager.handleAuthResponse(requestCode, resultCode, data, requireContext(), 1001, authCallback);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateStatus("Zamykanie okna logowania");
        AsanaAuthManager.dispose();
    }
}