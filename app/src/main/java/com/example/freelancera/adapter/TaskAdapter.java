package com.example.freelancera.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.models.Task;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks;
    private final SimpleDateFormat dateFormat;

    public TaskAdapter(List<Task> tasks) {
        this.tasks = tasks;
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
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
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void updateTasks(List<Task> newTasks) {
        this.tasks = newTasks;
        notifyDataSetChanged();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView taskNameTextView;
        private final TextView taskStatusTextView;
        private final TextView taskDueDateTextView;
        private final TextView taskClientTextView;
        private final TextView taskRateTextView;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskNameTextView = itemView.findViewById(R.id.taskNameTextView);
            taskStatusTextView = itemView.findViewById(R.id.taskStatusTextView);
            taskDueDateTextView = itemView.findViewById(R.id.taskDueDateTextView);
            taskClientTextView = itemView.findViewById(R.id.taskClientTextView);
            taskRateTextView = itemView.findViewById(R.id.taskRateTextView);
        }

        void bind(Task task) {
            taskNameTextView.setText(task.getName());
            String statusText;
            int statusColor;
            if ("Ukończone".equals(task.getStatus())) {
                statusText = "Ukończone";
                statusColor = itemView.getContext().getColor(R.color.status_completed);
            } else {
                java.util.Calendar cal1 = java.util.Calendar.getInstance();
                java.util.Calendar cal2 = java.util.Calendar.getInstance();
                if (task.getCreatedAt() != null) {
                    cal2.setTime(task.getCreatedAt());
                    boolean isToday = cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
                            && cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR);
                    if (isToday) {
                        statusText = "Nowe";
                    } else {
                        statusText = dateFormat.format(task.getCreatedAt());
                    }
                } else {
                    statusText = "Nowe";
                }
                statusColor = itemView.getContext().getColor(R.color.status_new);
            }
            taskStatusTextView.setText(statusText);
            taskStatusTextView.setTextColor(statusColor);

            if (task.getDueDate() != null) {
                java.util.Calendar today = java.util.Calendar.getInstance();
                java.util.Calendar due = java.util.Calendar.getInstance();
                due.setTime(task.getDueDate());
                boolean isToday = today.get(java.util.Calendar.YEAR) == due.get(java.util.Calendar.YEAR)
                        && today.get(java.util.Calendar.DAY_OF_YEAR) == due.get(java.util.Calendar.DAY_OF_YEAR);
                if (isToday) {
                    taskDueDateTextView.setText("Do dzisiaj");
                    taskDueDateTextView.setTextColor(itemView.getContext().getColor(R.color.status_in_progress));
                } else if (due.after(today)) {
                    taskDueDateTextView.setText(dateFormat.format(task.getDueDate()));
                    taskDueDateTextView.setTextColor(itemView.getContext().getColor(R.color.status_in_progress));
                } else {
                    taskDueDateTextView.setText(dateFormat.format(task.getDueDate()) + "  Po terminie");
                    taskDueDateTextView.setTextColor(itemView.getContext().getColor(android.R.color.holo_red_dark));
                }
            } else {
                taskDueDateTextView.setText("Brak terminu");
                taskDueDateTextView.setTextColor(itemView.getContext().getColor(R.color.status_default));
            }

            taskClientTextView.setText(task.getClient() != null ? task.getClient() : "Brak klienta");
            taskRateTextView.setText(String.format(Locale.getDefault(), "%.2f zł/h", task.getRatePerHour()));
        }
    }
}