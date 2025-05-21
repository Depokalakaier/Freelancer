package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.example.freelancera.R;
import com.example.freelancera.adapter.TaskAdapter;
import com.example.freelancera.model.Task;
import com.example.freelancera.util.JsonLoader;
import java.util.List;

public class TaskListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;

    public TaskListFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerViewTasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Task> tasks = JsonLoader.loadTasksFromAssets(getContext());
        adapter = new TaskAdapter(tasks, task -> {
            // Otwieranie szczegółów zadania (możesz zastąpić to nawigacją do TaskDetailFragment)
            TaskDetailFragment fragment = TaskDetailFragment.newInstance(task.getId());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);

        return view;
    }
}