package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.freelancera.R;
import com.example.freelancera.model.Task;
import com.example.freelancera.model.WorkTime;
import com.example.freelancera.util.JsonLoader;

public class TaskDetailFragment extends Fragment {
    private static final String ARG_TASK_ID = "task_id";

    public static TaskDetailFragment newInstance(String taskId) {
        TaskDetailFragment fragment = new TaskDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    public TaskDetailFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_detail, container, false);

        String taskId = getArguments() != null ? getArguments().getString(ARG_TASK_ID) : null;
        Task task = JsonLoader.findTaskById(getContext(), taskId);
        WorkTime workTime = JsonLoader.findWorkTimeByTaskId(getContext(), taskId);

        TextView title = view.findViewById(R.id.detail_task_title);
        TextView desc = view.findViewById(R.id.detail_task_desc);
        TextView status = view.findViewById(R.id.detail_task_status);
        TextView client = view.findViewById(R.id.detail_task_client);
        TextView time = view.findViewById(R.id.detail_task_time);
        TextView completed = view.findViewById(R.id.detail_task_completed);

        if (task != null) {
            title.setText(task.getTitle());
            desc.setText(task.getDescription());
            status.setText(task.getStatus());
            client.setText(task.getClient());
            completed.setText(task.getCompletedDate() != null ? task.getCompletedDate() : "Nieukończone");
        }
        if (workTime != null) {
            time.setText(workTime.getHours() + "h " + workTime.getMinutes() + "min");
        }

        return view;
    }
}
