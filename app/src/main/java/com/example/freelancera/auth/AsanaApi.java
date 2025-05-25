package com.example.freelancera.auth;

import android.util.Log;
import okhttp3.*;
import java.io.IOException;

public class AsanaApi {
    private static final String TAG = "AsanaApi";
    private static final String BASE_URL = "https://app.asana.com/api/1.0";

    public static void getProjects(String accessToken, String workspaceId, okhttp3.Callback callback) {
        Log.d(TAG, "Pobieranie projektów z Asana dla workspace: " + workspaceId);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces/" + workspaceId + "/projects?limit=100&opt_fields=name,notes,due_date,completed,owner,workspace,team,created_at,modified_at,public,archived")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();

        Log.d(TAG, "Wysyłanie requestu do Asana API: " + request.url());
        client.newCall(request).enqueue(callback);
    }

    public static void getTasks(String accessToken, String projectId, okhttp3.Callback callback) {
        Log.d(TAG, "Pobieranie zadań z projektu: " + projectId);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "/tasks?project=" + projectId + "&opt_fields=name,notes,completed,due_on")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();

        Log.d(TAG, "Wysyłanie requestu do Asana API: " + request.url());
        client.newCall(request).enqueue(callback);
    }

    public static void getWorkspaces(String accessToken, okhttp3.Callback callback) {
        Log.d(TAG, "Pobieranie workspaces z Asana");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();

        Log.d(TAG, "Wysyłanie requestu do Asana API: " + request.url());
        client.newCall(request).enqueue(callback);
    }
}