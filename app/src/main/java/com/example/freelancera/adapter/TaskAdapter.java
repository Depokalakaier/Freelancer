package com.example.freelancera.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    private final List<Task> taskList;
    private final OnTaskClickListener listener;

    public TaskAdapter(List<Task> taskList, OnTaskClickListener listener) {
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        holder.title.setText(task.getName());
        holder.desc.setText(task.getDescription());
        holder.status.setText(task.getStatus());

        // Obsługa kliknięcia w całe zadanie
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskClick(task);
            }
        });

        // Przycisk "Ukończ" tylko jeśli zadanie nie jest ukończone
        if (!"Ukończone".equals(task.getStatus())) {
            holder.completeBtn.setVisibility(View.VISIBLE);
            holder.completeBtn.setOnClickListener(v -> {
                task.setStatus("Ukończone");
                notifyItemChanged(position);
                // TODO: Zaktualizuj status w Firestore
                updateTaskStatusInFirestore(v.getContext(), task);
            });
        } else {
            holder.completeBtn.setVisibility(View.GONE);
        }
    }

    private void updateTaskStatusInFirestore(Context context, Task task) {
        // TODO: Implement Firestore update
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, status;
        Button completeBtn;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.task_title);
            desc = itemView.findViewById(R.id.task_desc);
            status = itemView.findViewById(R.id.task_status);
            completeBtn = itemView.findViewById(R.id.task_complete_btn);
        }
    }
}