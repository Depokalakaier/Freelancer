package com.example.freelancera.auth;

import android.util.Log;
import okhttp3.*;
import java.io.IOException;
import org.json.JSONObject;

public class AsanaApi {
    private static final String TAG = "AsanaApi";
    private static final String BASE_URL = "https://app.asana.com/api/1.0";
    private static final OkHttpClient client = new OkHttpClient();

    public static void getProjects(String accessToken, String workspaceId, okhttp3.Callback callback) {
        Log.d(TAG, "Pobieranie projektów z Asana dla workspace: " + workspaceId);
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces/" + workspaceId + "/projects?limit=100&opt_fields=name,notes,due_date,completed,owner,workspace,team,created_at,modified_at,public,archived")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();
//
        Log.d(TAG, "Wysyłanie requestu do Asana API: " + request.url());
        client.newCall(request).enqueue(callback);
    }

    public static void getTasks(String accessToken, String projectId, okhttp3.Callback callback) {
        Log.d(TAG, "Pobieranie zadań z projektu: " + projectId);
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
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .build();

        Log.d(TAG, "Wysyłanie requestu do Asana API: " + request.url());
        client.newCall(request).enqueue(callback);
    }

    public static void createTask(String token, JSONObject taskData, Callback callback) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("data", taskData);
            String jsonBody = wrapper.toString();
            RequestBody body = RequestBody.create(jsonBody, JSON);

            Request request = new Request.Builder()
                .url(BASE_URL + "/tasks")
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
                .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            Log.e(TAG, "Błąd tworzenia zadania: " + e.getMessage());
        }
    }

    public static void updateTask(String token, String taskId, JSONObject taskData, Callback callback) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("data", taskData);
            String jsonBody = wrapper.toString();
            RequestBody body = RequestBody.create(jsonBody, JSON);

            Request request = new Request.Builder()
                .url(BASE_URL + "/tasks/" + taskId)
                .addHeader("Authorization", "Bearer " + token)
                .put(body)
                .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            Log.e(TAG, "Błąd aktualizacji zadania: " + e.getMessage());
        }
    }

    public interface DeletionCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void deleteTask(android.content.Context context, String taskId, DeletionCallback callback) {
        String token = com.example.freelancera.util.SettingsManager.getAsanaToken(context);
        if (token == null) {
            callback.onFailure("Brak tokena Asana");
            return;
        }
        Request request = new Request.Builder()
            .url(BASE_URL + "/tasks/" + taskId)
            .addHeader("Authorization", "Bearer " + token)
            .delete()
            .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onFailure("HTTP " + response.code());
                }
            }
        });
    }
}