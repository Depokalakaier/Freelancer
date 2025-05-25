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
            taskStatusTextView.setText(task.getStatus());
            
            if (task.getDueDate() != null) {
                taskDueDateTextView.setText(dateFormat.format(task.getDueDate()));
            } else {
                taskDueDateTextView.setText("Brak terminu");
            }
            
            taskClientTextView.setText(task.getClient() != null ? task.getClient() : "Brak klienta");
            taskRateTextView.setText(String.format(Locale.getDefault(), "%.2f zł/h", task.getRatePerHour()));

            // Set status color
            int statusColor;
            switch (task.getStatus()) {
                case "Nowe":
                    statusColor = itemView.getContext().getColor(R.color.status_new);
                    break;
                case "W toku":
                    statusColor = itemView.getContext().getColor(R.color.status_in_progress);
                    break;
                case "Ukończone":
                    statusColor = itemView.getContext().getColor(R.color.status_completed);
                    break;
                default:
                    statusColor = itemView.getContext().getColor(R.color.status_default);
                    break;
            }
            taskStatusTextView.setTextColor(statusColor);
        }
    }
}