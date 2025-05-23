package com.example.freelancera.auth;

import okhttp3.*;
import java.io.IOException;

public class AsanaApi {
    public static void getProjects(String accessToken, okhttp3.Callback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://app.asana.com/api/1.0/projects")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
        client.newCall(request).enqueue(callback);
    }
}