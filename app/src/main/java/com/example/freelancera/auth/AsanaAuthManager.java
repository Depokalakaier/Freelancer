package com.example.freelancera.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthState;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;

public class AsanaAuthManager {
    public static final String CLIENT_ID = "1210368184403679"; // <--- TWÓJ PRAWDZIWY CLIENT ID
    public static final String REDIRECT_URI = "https://depokalakaier.github.io/Freelancer/";
    public static final String AUTH_ENDPOINT = "https://app.asana.com/-/oauth_authorize";
    public static final String TOKEN_ENDPOINT = "https://app.asana.com/-/oauth_token";
    private static AuthorizationService authService;
    private static AuthState authState;

    public static void startAuthorization(Activity activity, int requestCode) {
        authService = new AuthorizationService(activity);
        AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(
                Uri.parse(AUTH_ENDPOINT),
                Uri.parse(TOKEN_ENDPOINT)
        );
        AuthorizationRequest request = new AuthorizationRequest.Builder(
                config,
                CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(REDIRECT_URI)
        )
                // Dodaj potrzebne zakresy – możesz dodać więcej niż "default"
                .setScope("openid profile email projects:read tasks:read")
                .build();
        Intent authIntent = authService.getAuthorizationRequestIntent(request);
        activity.startActivityForResult(authIntent, requestCode);
    }

    public static void handleAuthResponse(int requestCode, int resultCode, @Nullable Intent data, Context context, int expectedRequestCode, AuthCallback callback) {
        if (requestCode == expectedRequestCode) {
            AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
            AuthorizationException ex = AuthorizationException.fromIntent(data);
            if (response != null) {
                authState = new AuthState(response, ex);
                TokenRequest tokenRequest = response.createTokenExchangeRequest();
                authService = new AuthorizationService(context);
                authService.performTokenRequest(tokenRequest, (tokenResponse, tokenEx) -> {
                    if (tokenResponse != null) {
                        authState.update(tokenResponse, tokenEx);
                        callback.onSuccess(authState.getAccessToken());
                    } else {
                        callback.onError(tokenEx != null ? tokenEx.getMessage() : "Token error");
                    }
                });
            } else {
                callback.onError(ex != null ? ex.errorDescription : "Brak odpowiedzi OAuth");
            }
        }
    }

    public interface AuthCallback {
        void onSuccess(String accessToken);
        void onError(String error);
    }
}