package com.example.freelancera.auth;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.freelancera.R;

public class AsanaLoginFragment extends DialogFragment {

    private static final String TAG = "AsanaLoginFragment";
    public interface AsanaAuthListener {
        void onTokenReceived(String token);
    }

    private AsanaAuthListener asanaAuthListener;

    public void setAsanaAuthListener(AsanaAuthListener listener) {
        this.asanaAuthListener = listener;
    }

    private static final String AUTH_URL = "https://app.asana.com/-/oauth_authorize" +
            "?client_id=1210368184403679" +
            "&redirect_uri=https://depokalakaier.github.io/Freelancer/" +
            "&response_type=token" +
            "&scope=default";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(
                R.layout.fragment_asana_login, null, false);

        WebView webView = view.findViewById(R.id.webViewAsana);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl(AUTH_URL);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "Received URL: " + url);
                
                if (url.startsWith("https://depokalakaier.github.io/Freelancer/")) {
                    try {
                        Uri uri = request.getUrl();
                        String fragment = uri.getFragment();
                        if (fragment != null) {
                            String[] params = fragment.split("&");
                            for (String param : params) {
                                if (param.startsWith("access_token=")) {
                                    String token = param.replace("access_token=", "");
                                    Log.d(TAG, "Token extracted successfully");
                                    if (asanaAuthListener != null) {
                                        asanaAuthListener.onTokenReceived(token);
                                    }
                                    dismiss();
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error extracting token: " + e.getMessage());
                    }
                    dismiss();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
            }
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("Połącz z Asana")
                .setView(view)
                .setNegativeButton("Anuluj", (DialogInterface dialog, int which) -> dismiss())
                .create();
    }
}