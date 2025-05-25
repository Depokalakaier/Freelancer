package com.example.freelancera.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class AsanaAuthManager {
    private static final String TAG = "AsanaAuthManager";
    public static final String CLIENT_ID = "1210368184403679";
    public static final String CLIENT_SECRET = "37984949b203b0a9ad86bc7b2d1d4d41";
    public static final String REDIRECT_URI = "https://depokalakaier.github.io/Freelancer/";
    public static final String AUTH_ENDPOINT = "https://app.asana.com/-/oauth_authorize";
    public static final String TOKEN_ENDPOINT = "https://app.asana.com/-/oauth_token";
    private static String state;
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public static class AuthResult {
        public final String accessToken;
        public final String idToken;
        public final String email;

        public AuthResult(String accessToken, String idToken, String email) {
            this.accessToken = accessToken;
            this.idToken = idToken;
            this.email = email;
        }
    }

    private static String generateRandomString() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifierBytes = new byte[32];
        secureRandom.nextBytes(codeVerifierBytes);
        return Base64.encodeToString(codeVerifierBytes, 
            Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String generateCodeChallenge(String codeVerifier) throws Exception {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    public static void startAuthorization(Activity activity, int requestCode) {
        try {
            Log.d(TAG, "Rozpoczynam proces autoryzacji...");
            
            // Generate state for CSRF protection
            state = generateRandomString();

            // Build authorization URL
            Uri authUri = Uri.parse(AUTH_ENDPOINT)
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("state", state)
                .appendQueryParameter("scope", "openid email profile tasks:read projects:read workspaces:read")
                .build();

            // Create CustomTabsIntent
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build();

            // Launch CustomTabs with authorization URL
            customTabsIntent.launchUrl(activity, authUri);
            Log.d(TAG, "Otwarto CustomTabs z URL autoryzacji: " + authUri.toString());

        } catch (Exception e) {
            Log.e(TAG, "Error starting authorization: " + e.getMessage(), e);
            if (activity != null) {
                android.widget.Toast.makeText(activity, 
                    "Błąd podczas inicjowania autoryzacji: " + e.getMessage(), 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void handleOAuthCallback(Uri uri, Context context, AuthCallback callback) {
        try {
            Log.d(TAG, "Otrzymano callback OAuth: " + uri.toString());

            String code = uri.getQueryParameter("code");
            String receivedState = uri.getQueryParameter("state");
            String error = uri.getQueryParameter("error");
            String errorDescription = uri.getQueryParameter("error_description");

            if (error != null) {
                Log.e(TAG, "Error in OAuth callback: " + error + " - " + errorDescription);
                callback.onError(errorDescription != null ? errorDescription : error);
                return;
            }

            if (code == null) {
                Log.e(TAG, "No authorization code received");
                callback.onError("Nie otrzymano kodu autoryzacyjnego");
                return;
            }

            // Verify state to prevent CSRF
            if (!receivedState.equals(state)) {
                Log.e(TAG, "State mismatch - possible CSRF attack");
                callback.onError("Błąd bezpieczeństwa - niezgodność state");
                return;
            }

            // Exchange code for token
            RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("redirect_uri", REDIRECT_URI)
                .add("code", code)
                .build();

            Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Token exchange failed: " + e.getMessage(), e);
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> callback.onError("Błąd wymiany kodu na token: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (!response.isSuccessful()) {
                            String error = json.optString("error", "Unknown error");
                            String errorDesc = json.optString("error_description", "No description");
                            Log.e(TAG, "Token exchange error: " + error + " - " + errorDesc);
                            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                            mainHandler.post(() -> callback.onError(errorDesc));
                            return;
                        }

                        String accessToken = json.getString("access_token");
                        String idToken = json.optString("id_token");
                        String email = json.has("data") ? json.getJSONObject("data").optString("email") : null;

                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> callback.onSuccess(new AuthResult(accessToken, idToken, email)));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing token response: " + e.getMessage(), e);
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> callback.onError("Błąd przetwarzania odpowiedzi: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling OAuth callback: " + e.getMessage(), e);
            callback.onError("Błąd przetwarzania odpowiedzi OAuth: " + e.getMessage());
        }
    }

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(String error);
    }

    public static void dispose() {
        // Clean up any resources if needed
        state = null;
    }
}