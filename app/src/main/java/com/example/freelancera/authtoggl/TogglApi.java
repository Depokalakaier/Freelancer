package com.example.freelancera.authtoggl;

import android.util.Log;
import androidx.annotation.NonNull;
import com.example.freelancera.models.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TogglApi {
    private static final String TAG = "TogglApi";
    private final OkHttpClient client = new OkHttpClient();
    private final String apiToken;
    private final FirebaseUser user;
    private final FirebaseFirestore firestore;

    public TogglApi(@NonNull String apiToken, @NonNull FirebaseUser user) {
        this.apiToken = apiToken;
        this.user = user;
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void fetchWorkspaces(Callback callback) {
        Request request = new Request.Builder()
                .url("https://api.track.toggl.com/api/v9/me")
                .addHeader("Authorization", Credentials.basic(apiToken, "api_token"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void fetchProjects(long workspaceId, Callback callback) {
        Request request = new Request.Builder()
                .url("https://api.track.toggl.com/api/v9/workspaces/" + workspaceId + "/projects")
                .addHeader("Authorization", Credentials.basic(apiToken, "api_token"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void fetchTasks(long workspaceId, long projectId, Callback callback) {
        Request request = new Request.Builder()
                .url("https://api.track.toggl.com/api/v9/workspaces/" + workspaceId + "/projects/" + projectId + "/tasks")
                .addHeader("Authorization", Credentials.basic(apiToken, "api_token"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void fetchClients(long workspaceId, Callback callback) {
        Request request = new Request.Builder()
                .url("https://api.track.toggl.com/api/v9/workspaces/" + workspaceId + "/clients")
                .addHeader("Authorization", Credentials.basic(apiToken, "api_token"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Zapisz projekt do Firestore
    public void saveProjectToFirestore(JSONObject project) throws JSONException {
        if (user == null) return;
        String projectId = project.getString("id");
        Map<String, Object> data = new HashMap<>();
        data.put("id", projectId);
        data.put("name", project.optString("name"));
        data.put("client_id", project.optString("client_id"));
        data.put("client_name", project.optString("client_name"));
        data.put("color", project.optString("color"));
        data.put("active", project.optBoolean("active", true));
        firestore.collection("users").document(user.getUid())
                .collection("toggl_projects").document(projectId)
                .set(data, SetOptions.merge());
    }

    // Zapisz klienta do Firestore
    public void saveClientToFirestore(JSONObject clientObj) throws JSONException {
        if (user == null) return;
        String clientId = clientObj.getString("id");
        Map<String, Object> data = new HashMap<>();
        data.put("id", clientId);
        data.put("name", clientObj.optString("name"));
        firestore.collection("users").document(user.getUid())
                .collection("toggl_clients").document(clientId)
                .set(data, SetOptions.merge());
    }

    // Zapisz zadanie do Firestore (łącznie z danymi projektu i klienta)
    public void saveTaskToFirestore(JSONObject task, JSONObject project, JSONObject clientObj) throws JSONException {
        if (user == null) return;
        String taskId = task.getString("id");
        Task t = new Task();
        t.setId(taskId);
        t.setName(task.optString("name"));
        t.setStatus(task.optBoolean("active", true) ? "Nowe" : "Ukończone");
        t.setTogglProjectId(project != null ? project.optString("id") : null);
        t.setTogglProjectName(project != null ? project.optString("name") : null);
        t.setTogglClientId(clientObj != null ? clientObj.optString("id") : null);
        t.setTogglClientName(clientObj != null ? clientObj.optString("name") : null);
        t.setTogglTrackedSeconds(task.optLong("tracked_seconds", 0));
        firestore.collection("users").document(user.getUid())
                .collection("tasks").document(taskId)
                .set(t, SetOptions.merge());
    }

    // Łączenie zadań po nazwie z Asany i Toggl
    public void mergeWithAsanaTasks(List<Task> asanaTasks, List<Task> togglTasks) {
        for (Task togglTask : togglTasks) {
            for (Task asanaTask : asanaTasks) {
                if (togglTask.getName().equalsIgnoreCase(asanaTask.getName())) {
                    // Uzupełnij dane w zadaniu Asany
                    asanaTask.setTogglProjectId(togglTask.getTogglProjectId());
                    asanaTask.setTogglProjectName(togglTask.getTogglProjectName());
                    asanaTask.setTogglClientId(togglTask.getTogglClientId());
                    asanaTask.setTogglClientName(togglTask.getTogglClientName());
                    asanaTask.setTogglTrackedSeconds(togglTask.getTogglTrackedSeconds());
                    // NIE nadpisuj stawki jeśli już jest ustawiona
                    if (asanaTask.getRatePerHour() <= 0 && togglTask.getRatePerHour() > 0) {
                        asanaTask.setRatePerHour(togglTask.getRatePerHour());
                    }
                    // Zapisz do Firestore
                    firestore.collection("users").document(user.getUid())
                            .collection("tasks").document(asanaTask.getId())
                            .set(asanaTask, SetOptions.merge());
                }
            }
        }
    }
} 