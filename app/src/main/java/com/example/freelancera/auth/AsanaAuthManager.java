package com.example.freelancera.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AsanaAuthManager {
    private static final String TAG = "AsanaAuthManager";
    public static final String CLIENT_ID = "1210368184403679";
    public static final String REDIRECT_URI = "https://depokalakaier.github.io/Freelancer/";
    public static final String AUTH_ENDPOINT = "https://app.asana.com/-/oauth_authorize";
    public static final String TOKEN_ENDPOINT = "https://app.asana.com/-/oauth_token";
    private static AuthorizationService authService;
    private static String codeVerifier;
    private static String state;

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

            // Open browser with authorization URL
            Intent intent = new Intent(Intent.ACTION_VIEW, authUri);
            activity.startActivity(intent);
            Log.d(TAG, "Otwarto przeglądarkę z URL autoryzacji: " + authUri.toString());

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

            // Extract tokens from URI parameters
            String accessToken = uri.getQueryParameter("access_token");
            String idToken = uri.getQueryParameter("id_token");
            String email = uri.getQueryParameter("email");

            if (accessToken != null && !accessToken.isEmpty()) {
                Log.d(TAG, "Otrzymano token dostępu i dane użytkownika");
                callback.onSuccess(new AuthResult(accessToken, idToken, email));
            } else {
                String error = uri.getQueryParameter("error");
                String errorDescription = uri.getQueryParameter("error_description");
                Log.e(TAG, "Error in OAuth callback: " + error + " - " + errorDescription);
                callback.onError(errorDescription != null ? errorDescription : error);
            }
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
        if (authService != null) {
            authService.dispose();
            authService = null;
        }
    }
}