package com.example.freelancera.adapter;

import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    
    private List<Task> tasks;
    private final OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    public TaskAdapter(List<Task> tasks, OnTaskClickListener listener) {
        this.tasks = tasks;
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
        Task task = tasks.get(position);
        holder.bind(task, listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void updateTasks(List<Task> newTasks) {
        this.tasks = newTasks;
        notifyDataSetChanged();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        private static final String TAG = "TaskAdapter";
        private final MaterialCardView cardView;
        private final TextView titleText;
        private final TextView statusText;
        private final TextView clientText;
        private final TextView dueDateText;
        private final TextView timeText;
        private final TextView amountText;

        TaskViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            titleText = itemView.findViewById(R.id.text_task_title);
            statusText = itemView.findViewById(R.id.text_task_status);
            clientText = itemView.findViewById(R.id.text_task_client);
            dueDateText = itemView.findViewById(R.id.text_task_due_date);
            timeText = itemView.findViewById(R.id.text_task_time);
            amountText = itemView.findViewById(R.id.text_task_amount);
        }

        void bind(Task task, OnTaskClickListener listener) {
            titleText.setText(task.getName());
            String status = task.getStatus();
            
            statusText.setText(status);
            clientText.setText(task.getClient());
            
            if (task.getDueDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                dueDateText.setText(sdf.format(task.getDueDate()));
                dueDateText.setVisibility(View.VISIBLE);
            } else {
                dueDateText.setVisibility(View.GONE);
            }

            // Format time
            long hours = task.getTotalTimeInSeconds() / 3600;
            long minutes = (task.getTotalTimeInSeconds() % 3600) / 60;
            timeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hours, minutes));

            // Format amount
            double amount = (task.getTotalTimeInSeconds() / 3600.0) * task.getRatePerHour();
            amountText.setText(String.format(Locale.getDefault(), "%.2f PLN", amount));

            // Set card color based on status
            int colorRes;
            
            switch (status) {
                case "Nowe":
                    colorRes = R.color.task_new;
                    break;
                case "W toku":
                    colorRes = R.color.task_in_progress;
                    break;
                case "Ukończone":
                    colorRes = R.color.task_completed;
                    break;
                default:
                    colorRes = R.color.task_default;
                    break;
            }
            
            try {
                int color = ContextCompat.getColor(itemView.getContext(), colorRes);
                cardView.setCardBackgroundColor(color);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error setting color for task: " + task.getName(), e);
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.task_default));
            }

            // Set click listener
            itemView.setOnClickListener(v -> listener.onTaskClick(task));
        }
    }
}