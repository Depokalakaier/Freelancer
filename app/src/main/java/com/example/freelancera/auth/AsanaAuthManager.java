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
    public static final String REDIRECT_URI = "https://depokalakaier.github.io/Freelancer";
    public static final String AUTH_ENDPOINT = "https://app.asana.com/-/oauth_authorize";
    public static final String TOKEN_ENDPOINT = "https://app.asana.com/-/oauth_token";
    private static AuthorizationService authService;
    private static String codeVerifier;
    private static String state;

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
            authService = new AuthorizationService(activity);
            AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(
                    Uri.parse(AUTH_ENDPOINT),
                    Uri.parse(TOKEN_ENDPOINT)
            );

            // Generate PKCE code verifier and challenge
            codeVerifier = generateRandomString();
            String codeChallenge = generateCodeChallenge(codeVerifier);

            // Generate state for CSRF protection
            state = generateRandomString();

            AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                    config,
                    CLIENT_ID,
                    ResponseTypeValues.CODE,
                    Uri.parse(REDIRECT_URI)
            );

            // Add required parameters as per Asana docs
            builder.setCodeVerifier(
                codeVerifier,
                codeChallenge,
                "S256"
            );

            // Add state for CSRF protection
            builder.setState(state);

            // Add scopes
            builder.setScopes(
                "default"
            );

            AuthorizationRequest request = builder.build();

            Log.d(TAG, "Konfiguracja autoryzacji:");
            Log.d(TAG, "Client ID: " + CLIENT_ID);
            Log.d(TAG, "Redirect URI: " + REDIRECT_URI);
            Log.d(TAG, "Code verifier: " + codeVerifier);
            Log.d(TAG, "Code challenge: " + codeChallenge);
            Log.d(TAG, "State: " + state);
            Log.d(TAG, "Auth endpoint: " + AUTH_ENDPOINT);
            Log.d(TAG, "Token endpoint: " + TOKEN_ENDPOINT);

            CustomTabsIntent.Builder customTabsBuilder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = customTabsBuilder.build();
            
            Log.d(TAG, "Otwieram Chrome Custom Tab z URL autoryzacji...");
            Intent authIntent = authService.getAuthorizationRequestIntent(request, customTabsIntent);
            activity.startActivityForResult(authIntent, requestCode);
            Log.d(TAG, "Wysłano intent do przeglądarki");

        } catch (Exception e) {
            Log.e(TAG, "Error starting authorization: " + e.getMessage(), e);
            if (activity != null) {
                android.widget.Toast.makeText(activity, 
                    "Błąd podczas inicjowania autoryzacji: " + e.getMessage(), 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void handleAuthResponse(int requestCode, int resultCode, @Nullable Intent data, 
                                        Context context, int expectedRequestCode, AuthCallback callback) {
        Log.d(TAG, "Otrzymano odpowiedź autoryzacji:");
        Log.d(TAG, "Request code: " + requestCode);
        Log.d(TAG, "Result code: " + resultCode);
        Log.d(TAG, "Data: " + (data != null ? data.getData() : "null"));
        
        if (requestCode == expectedRequestCode) {
            if (data == null) {
                Log.e(TAG, "Authentication data is null");
                callback.onError("Błąd autoryzacji: brak danych odpowiedzi");
                return;
            }

            AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
            AuthorizationException ex = AuthorizationException.fromIntent(data);

            if (response != null) {
                Log.d(TAG, "Otrzymano odpowiedź autoryzacji:");
                Log.d(TAG, "Authorization Code: " + response.authorizationCode);
                Log.d(TAG, "State: " + response.state);

                // Verify state parameter to prevent CSRF attacks
                if (!state.equals(response.state)) {
                    Log.e(TAG, "State mismatch. Expected: " + state + ", Got: " + response.state);
                    callback.onError("Błąd bezpieczeństwa: niezgodność parametru state");
                    return;
                }

                Log.d(TAG, "Rozpoczynam wymianę kodu na token...");
                
                // Exchange authorization code for tokens
                authService.performTokenRequest(
                    response.createTokenExchangeRequest(),
                    (tokenResponse, tokenException) -> {
                        if (tokenResponse != null) {
                            String accessToken = tokenResponse.accessToken;
                            if (accessToken != null && !accessToken.isEmpty()) {
                                Log.d(TAG, "Otrzymano token dostępu");
                                callback.onSuccess(accessToken);
                            } else {
                                Log.e(TAG, "Access token is null or empty");
                                callback.onError("Nie udało się uzyskać tokenu dostępu");
                            }
                        } else if (tokenException != null) {
                            Log.e(TAG, "Token exchange error: " + tokenException.getMessage(), tokenException);
                            callback.onError("Błąd wymiany tokenu: " + tokenException.getMessage());
                        }
                    }
                );
            } else if (ex != null) {
                Log.e(TAG, "Authorization error: " + ex.getMessage(), ex);
                String errorMessage = "Błąd autoryzacji: ";
                if (ex.getMessage() != null) {
                    errorMessage += ex.getMessage();
                } else {
                    errorMessage += "nieznany błąd";
                }
                callback.onError(errorMessage);
            } else {
                Log.e(TAG, "Unknown authorization error");
                callback.onError("Wystąpił nieoczekiwany błąd podczas autoryzacji");
            }
        }
    }

    public interface AuthCallback {
        void onSuccess(String accessToken);
        void onError(String error);
    }

    public static void dispose() {
        if (authService != null) {
            authService.dispose();
            authService = null;
        }
    }
}