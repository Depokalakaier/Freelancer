package com.example.freelancera.auth;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private ActivityResultLauncher<android.content.Intent> authLauncher;

    public interface AsanaAuthListener {
        void onTokenReceived(AsanaAuthManager.AuthResult authResult);
    }

    private AsanaAuthListener asanaAuthListener;

    public void setAsanaAuthListener(AsanaAuthListener listener) {
        this.asanaAuthListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        authLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getData() == null) {
                    updateStatus("Otrzymano pusty wynik autoryzacji - prawdopodobnie zamknięto przeglądarkę");
                    // Użytkownik zamknął przeglądarkę lub anulował logowanie
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        new AlertDialog.Builder(getActivity())
                            .setTitle("Błąd logowania")
                            .setMessage("Anulowano logowanie do Asana lub zamknięto przeglądarkę.")
                            .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                                try {
                                    updateStatus("Ponawiam próbę autoryzacji...");
                                    startAuthorization();
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
                AsanaAuthManager.handleAuthResponse(1001, result.getResultCode(), result.getData(), 
                    requireContext(), 1001, authCallback);
            }
        );
    }

    private void updateStatus(String message) {
        Log.d(TAG, message);
        if (statusTextView != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                statusTextView.setText(message);
            });
        }
    }

    private void startAuthorization() {
        updateStatus("Rozpoczynam autoryzację...");
        AsanaAuthManager.startAuthorization(requireActivity(), 1001);
        updateStatus("Otwieranie przeglądarki z autoryzacją Asana...");
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
            // Set up callback for auth result
            authCallback = new AsanaAuthManager.AuthCallback() {
                @Override
                public void onSuccess(AsanaAuthManager.AuthResult result) {
                    updateStatus("Otrzymano token dostępu i dane użytkownika!");
                    if (asanaAuthListener != null) {
                        asanaAuthListener.onTokenReceived(result);
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
                                    startAuthorization();
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

            startAuthorization();
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
                            startAuthorization();
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

        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateStatus("Zamykanie okna logowania");
        AsanaAuthManager.dispose();
    }
}