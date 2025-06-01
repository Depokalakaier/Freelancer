package com.example.freelancera.adapter;

import android.content.res.Configuration;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
        private final TextView togglProjectText;
        private final TextView togglClientText;

        TaskViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            titleText = itemView.findViewById(R.id.text_task_title);
            statusText = itemView.findViewById(R.id.text_task_status);
            clientText = itemView.findViewById(R.id.text_task_client);
            dueDateText = itemView.findViewById(R.id.text_task_due_date);
            timeText = itemView.findViewById(R.id.text_task_time);
            amountText = itemView.findViewById(R.id.text_task_amount);
            togglProjectText = itemView.findViewById(R.id.text_task_toggl_project);
            togglClientText = itemView.findViewById(R.id.text_task_toggl_client);
        }

        void bind(Task task, OnTaskClickListener listener) {
            titleText.setText(task.getName());
            String status = task.getStatus();
            
            if ("Ukończone".equals(status)) {
                if (task.getCompletedAt() != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
                    statusText.setText("Ukończono " + sdf.format(task.getCompletedAt()));
                } else {
                    statusText.setText("Ukończono");
                }
                if (task.getDueDate() != null) {
                    java.util.Calendar calDue = java.util.Calendar.getInstance();
                    calDue.setTime(task.getDueDate());
                    java.util.Calendar calCompleted = java.util.Calendar.getInstance();
                    if (task.getCompletedAt() != null) {
                        calCompleted.setTime(task.getCompletedAt());
                    } else {
                        calCompleted = java.util.Calendar.getInstance();
                    }
                    boolean isDueToday = calDue.get(java.util.Calendar.YEAR) == calCompleted.get(java.util.Calendar.YEAR)
                            && calDue.get(java.util.Calendar.DAY_OF_YEAR) == calCompleted.get(java.util.Calendar.DAY_OF_YEAR);
                    dueDateText.setVisibility(View.VISIBLE);
                    dueDateText.setText("");
                    if (isDueToday) {
                        SpannableString span = new SpannableString("Termin do dzisiaj");
                        span.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.gray)), 0, span.length(), 0);
                        span.setSpan(new RelativeSizeSpan(0.95f), 0, span.length(), 0);
                        dueDateText.setText(span);
                    } else {
                        long diffDays = (calCompleted.getTimeInMillis() - calDue.getTimeInMillis()) / (1000 * 60 * 60 * 24);
                        if (diffDays > 0) {
                            String info = diffDays == 1 ? "1 dzień po terminie" : diffDays + " dni po terminie";
                            SpannableString span = new SpannableString(info);
                            span.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.gray)), 0, info.length(), 0);
                            span.setSpan(new RelativeSizeSpan(0.95f), 0, info.length(), 0);
                            dueDateText.setText(span);
                        } else {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
                            dueDateText.setText("Termin: " + sdf.format(task.getDueDate()));
                        }
                    }
                } else {
                    dueDateText.setVisibility(View.GONE);
                }
                clientText.setVisibility(View.VISIBLE);
                clientText.setText(task.getClient());
            } else {
                statusText.setText(task.getStatus());
                if (task.getDueDate() != null) {
                    java.util.Calendar calDue = java.util.Calendar.getInstance();
                    calDue.setTime(task.getDueDate());
                    java.util.Calendar calToday = java.util.Calendar.getInstance();
                    boolean isDueToday = calDue.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR)
                            && calDue.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR);
                    if (isDueToday) {
                        SpannableString span = new SpannableString("Termin do dzisiaj");
                        span.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.red)), 0, span.length(), 0);
                        span.setSpan(new RelativeSizeSpan(0.95f), 0, span.length(), 0);
                        dueDateText.setText(span);
                    } else {
                        long diffDays = (calToday.getTimeInMillis() - calDue.getTimeInMillis()) / (1000 * 60 * 60 * 24);
                        if (diffDays > 0) {
                            String info = diffDays == 1 ? "1 dzień po terminie" : diffDays + " dni po terminie";
                            SpannableString span = new SpannableString(info);
                            span.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.red)), 0, info.length(), 0);
                            span.setSpan(new RelativeSizeSpan(0.95f), 0, info.length(), 0);
                            dueDateText.setText(span);
                        } else {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
                            dueDateText.setText("Termin: " + sdf.format(task.getDueDate()));
                        }
                    }
                }
                clientText.setVisibility(View.VISIBLE);
                clientText.setText(task.getClient());
            }

            // Projekt z Toggl pod statusem
            if (task.getTogglProjectName() != null && !task.getTogglProjectName().isEmpty()) {
                togglProjectText.setText("Projekt: " + task.getTogglProjectName());
                togglProjectText.setVisibility(View.VISIBLE);
            } else {
                togglProjectText.setVisibility(View.GONE);
            }
            // Klient z Toggl nad klientem
            if (task.getTogglClientName() != null && !task.getTogglClientName().isEmpty() && (task.getClient() == null || !task.getClient().equals(task.getTogglClientName()))) {
                togglClientText.setText("Klient: " + task.getTogglClientName());
                togglClientText.setVisibility(View.VISIBLE);
            } else {
                togglClientText.setVisibility(View.GONE);
            }
            // Czas z Toggl jeśli jest
            long togglSec = task.getTogglTrackedSeconds();
            if (togglSec > 0) {
                long hours = togglSec / 3600;
                long minutes = (togglSec % 3600) / 60;
                timeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hours, minutes));
            } else {
                long hours = task.getTotalTimeInSeconds() / 3600;
                long minutes = (task.getTotalTimeInSeconds() % 3600) / 60;
                timeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hours, minutes));
            }

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
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });
        }
    }
}