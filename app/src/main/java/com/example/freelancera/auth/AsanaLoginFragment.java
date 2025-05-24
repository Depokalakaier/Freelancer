package com.example.freelancera.auth;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.freelancera.R;

public class AsanaLoginFragment extends DialogFragment {

    public interface AsanaAuthListener {
        void onTokenReceived(String token);
    }

    private AsanaAuthListener asanaAuthListener;

    public void setAsanaAuthListener(AsanaAuthListener listener) {
        this.asanaAuthListener = listener;
    }

    // Zamień poniższe na prawdziwy adres URL autoryzacji Asany (klucz klienta, redirect_uri, itd.)
    private static final String AUTH_URL = "https://app.asana.com/-/oauth_authorize?client_id=TWOJE_CLIENT_ID&redirect_uri=freelancerauth://callback&response_type=token";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(
                R.layout.fragment_asana_login, null, false);

        WebView webView = view.findViewById(R.id.webViewAsana);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(AUTH_URL);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Sprawdź redirect_uri i wyciągnij token
                if (url.startsWith("freelancerauth://callback")) {
                    // Przykład: freelancerauth://callback#access_token=TOKEN_HERE&token_type=Bearer&expires_in=3600
                    String[] parts = url.split("#");
                    if (parts.length > 1) {
                        String[] params = parts[1].split("&");
                        for (String param : params) {
                            if (param.startsWith("access_token=")) {
                                String token = param.replace("access_token=", "");
                                if (asanaAuthListener != null) {
                                    asanaAuthListener.onTokenReceived(token);
                                }
                                dismiss();
                                return true;
                            }
                        }
                    }
                    dismiss();
                    return true;
                }
                return false;
            }
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("Połącz z Asana")
                .setView(view)
                .setNegativeButton("Anuluj", (DialogInterface dialog, int which) -> dismiss())
                .create();
    }
}