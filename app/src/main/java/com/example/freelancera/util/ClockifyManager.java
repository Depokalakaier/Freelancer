package com.example.freelancera.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.example.freelancera.models.clockify.ClockifyTimeEntry;
import com.example.freelancera.api.ClockifyApi;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ClockifyManager {
    private static final String TAG = "ClockifyManager";
    private static final String BASE_URL = "https://api.clockify.me/api/v1/";
    private static final String PREF_API_KEY = "clockify_api_key";
    private static final String PREF_WORKSPACE_ID = "clockify_workspace_id";

    private static ClockifyManager instance;
    private final ClockifyApi api;
    private final SharedPreferences prefs;
    private String apiKey;
    private String workspaceId;

    private ClockifyManager(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        apiKey = prefs.getString(PREF_API_KEY, null);
        workspaceId = prefs.getString(PREF_WORKSPACE_ID, null);

        // Konfiguracja HTTP client z interceptorem dla logów
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("X-Api-Key", apiKey)
                            .method(original.method(), original.body());
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        // Konfiguracja Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(ClockifyApi.class);
    }

    public static synchronized ClockifyManager getInstance(Context context) {
        if (instance == null) {
            instance = new ClockifyManager(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && workspaceId != null && !workspaceId.isEmpty();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        prefs.edit().putString(PREF_API_KEY, apiKey).apply();
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
        prefs.edit().putString(PREF_WORKSPACE_ID, workspaceId).apply();
    }

    public ClockifyApi getApi() {
        return api;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }
} 