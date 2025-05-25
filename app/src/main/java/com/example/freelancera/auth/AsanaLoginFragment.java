package com.example.freelancera.auth;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
    private ProgressBar progressBar;
    private ImageView statusIcon;
    private AlertDialog dialog;

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
    }

    private void updateStatus(String message, boolean isSuccess, boolean isError) {
        Log.d(TAG, message);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (statusTextView != null) {
                    statusTextView.setText(message);
                }
                
                if (progressBar != null && statusIcon != null) {
                    if (isSuccess || isError) {
                        progressBar.setVisibility(View.GONE);
                        statusIcon.setVisibility(View.VISIBLE);
                        statusIcon.setImageResource(isSuccess ? R.drawable.ic_check_circle : R.drawable.ic_error_circle);
                        
                        // Dodaj animację
                        Animation fadeIn = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
                        statusIcon.startAnimation(fadeIn);
                        
                        // Jeśli sukces, zamknij dialog po 1.5 sekundy
                        if (isSuccess) {
                            statusIcon.postDelayed(() -> {
                                if (isAdded() && dialog != null) {
                                    dialog.dismiss();
                                }
                            }, 1500);
                        }
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        statusIcon.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void startAuthorization() {
        updateStatus("Otwieranie przeglądarki z autoryzacją Asana...", false, false);
        AsanaAuthManager.startAuthorization(requireActivity(), 1001);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(
                R.layout.fragment_asana_login, null, false);

        statusTextView = view.findViewById(R.id.statusTextView);
        progressBar = view.findViewById(R.id.progressBar);
        statusIcon = view.findViewById(R.id.statusIcon);
        
        updateStatus("Inicjalizacja procesu logowania...", false, false);

        dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Łączenie z Asana")
                .setView(view)
                .setNegativeButton("Anuluj", (dialogInterface, i) -> {
                    updateStatus("Anulowano proces logowania", false, true);
                    AsanaAuthManager.dispose();
                    dismiss();
                })
                .create();

        try {
            // Set up callback for auth result
            authCallback = new AsanaAuthManager.AuthCallback() {
                @Override
                public void onSuccess(AsanaAuthManager.AuthResult result) {
                    updateStatus("Połączono!", true, false);
                    if (asanaAuthListener != null) {
                        asanaAuthListener.onTokenReceived(result);
                    }
                }

                @Override
                public void onError(String error) {
                    String errorMsg = "Błąd łączenia z kontem Asana";
                    Log.e(TAG, errorMsg + ": " + error);
                    updateStatus(errorMsg, false, true);
                    
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        new AlertDialog.Builder(getActivity())
                            .setTitle("Błąd logowania")
                            .setMessage(errorMsg)
                            .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                                try {
                                    updateStatus("Ponawiam próbę autoryzacji...", false, false);
                                    startAuthorization();
                                } catch (Exception e) {
                                    updateStatus("Błąd łączenia z kontem Asana", false, true);
                                    Log.e(TAG, "Error retrying authorization: " + e.getMessage());
                                    dismiss();
                                }
                            })
                            .setNegativeButton("Anuluj", (dialogInterface, which) -> {
                                updateStatus("Anulowano proces logowania", false, true);
                                dismiss();
                            })
                            .show();
                    }
                }
            };

            startAuthorization();
        } catch (Exception e) {
            String errorMsg = "Błąd łączenia z kontem Asana";
            Log.e(TAG, errorMsg + ": " + e.getMessage(), e);
            updateStatus(errorMsg, false, true);
            
            return new AlertDialog.Builder(requireContext())
                    .setTitle("Błąd połączenia")
                    .setMessage(errorMsg)
                    .setPositiveButton("Spróbuj ponownie", (dialogInterface, which) -> {
                        try {
                            updateStatus("Ponawiam próbę autoryzacji...", false, false);
                            startAuthorization();
                        } catch (Exception retryEx) {
                            updateStatus("Błąd łączenia z kontem Asana", false, true);
                            dismiss();
                        }
                    })
                    .setNegativeButton("Anuluj", (dialogInterface, which) -> {
                        updateStatus("Anulowano proces logowania", false, true);
                        dismiss();
                    })
                    .create();
        }

        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AsanaAuthManager.dispose();
    }
}