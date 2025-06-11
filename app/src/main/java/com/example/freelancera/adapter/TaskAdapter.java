package com.example.freelancera.adapter;

import android.content.Context;
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
import java.util.concurrent.atomic.AtomicInteger;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    
    private List<Task> tasks;
    private final OnTaskClickListener listener;
    private final Context context;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    public TaskAdapter(Context context, List<Task> tasks, OnTaskClickListener listener) {
        this.context = context;
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
        tasks.clear();
        tasks.addAll(newTasks);
        for (Task task : tasks) {
            task.setContext(context);
        }
        notifyDataSetChanged();
        refreshRates();
    }

    private void refreshRates() {
        AtomicInteger completedUpdates = new AtomicInteger(0);
        int totalTasks = tasks.size();

        for (Task task : tasks) {
            if (task != null) {
                task.setContext(context);
            }
            task.updateRateFromFirebase(() -> {
                if (completedUpdates.incrementAndGet() == totalTasks) {
                    notifyDataSetChanged();
                }
            });
        }
    }

    private static double roundHours(double rawHours) {
        int wholeHours = (int) rawHours;
        double minutesPart = (rawHours - wholeHours) * 60;
        int minutes = (int) minutesPart;
        if (minutes >= 0 && minutes <= 15) {
            return wholeHours;
        } else if (minutes >= 16 && minutes <= 44) {
            return wholeHours + 0.5;
        } else {
            return wholeHours + 1;
        }
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        private static final String TAG = "TaskAdapter";
        private final MaterialCardView cardView;
        private final TextView titleText;
        private final TextView statusText;
        private final TextView clientText;
        private final TextView dueDateText;
        private final TextView timeText;
        private final TextView rateText;
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
            rateText = itemView.findViewById(R.id.text_task_rate1);
            togglProjectText = itemView.findViewById(R.id.text_task_toggl_project);
            togglClientText = itemView.findViewById(R.id.text_task_toggl_client);
        }

        void bind(Task task, OnTaskClickListener listener) {
            titleText.setText(task.getName());
            String status;
            if (task.isCompletedStatus()) {
                status = "Ukończone";
            } else if (task.getDueDate() != null) {
                java.util.Calendar calDue = java.util.Calendar.getInstance();
                calDue.setTime(task.getDueDate());
                java.util.Calendar calToday = java.util.Calendar.getInstance();
                boolean isDueToday = calDue.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR)
                        && calDue.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR);
                if (isDueToday) {
                    status = "Nowe";
                } else {
                    status = "W toku";
                }
            } else {
                status = task.getStatus(); // fallback
            }
            statusText.setText(status);
            if (task.getDueDate() != null) {
                java.util.Calendar calDue = java.util.Calendar.getInstance();
                calDue.setTime(task.getDueDate());
                java.util.Calendar calToday = java.util.Calendar.getInstance();
                boolean isDueToday = calDue.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR)
                        && calDue.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR);
                dueDateText.setVisibility(View.VISIBLE);
                if (task.isCompletedStatus()) {
                    long diffDays = (task.getCompletedAt() != null ? (task.getCompletedAt().getTime() - task.getDueDate().getTime()) : 0) / (1000 * 60 * 60 * 24);
                    if (diffDays <= 0) {
                        dueDateText.setText("Ukończono w terminie");
                    } else {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
                        dueDateText.setText("Termin: " + sdf.format(task.getDueDate()));
                    }
                } else if (isDueToday) {
                    SpannableString span = new SpannableString("Termin do dzisiaj");
                    span.setSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.date_orange)), 0, span.length(), 0);
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
            } else {
                dueDateText.setVisibility(View.GONE);
            }
            clientText.setVisibility(View.VISIBLE);
            clientText.setText(task.getClient());

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
            double roundedHours;
            if (togglSec > 0) {
                double rawHours = togglSec / 3600.0;
                roundedHours = roundHours(rawHours);
            } else {
                double rawHours = task.getTotalTimeInSeconds() / 3600.0;
                roundedHours = roundHours(rawHours);
            }
            String hoursText;
            if (Math.abs(roundedHours - Math.round(roundedHours)) < 0.01) {
                hoursText = String.format("%d h", (int) roundedHours);
            } else {
                hoursText = String.format("%.1f h", roundedHours).replace(".0", "").replace(".", ",");
            }
            timeText.setText(hoursText);

            // Format amount - Update to use the actual rate from task
            double rate = task.getRatePerHour();
          //  amountText.setText(String.format(Locale.getDefault(), "%.0f PLN/h", rate));
            rateText.setText(String.format(Locale.getDefault(), "Stawka %.0f PLN/h",  task.getRatePerHour()));

            // Set card color based on status
            int colorRes;
            if (task.isCompletedStatus()) {
                colorRes = R.color.task_completed;
            } else {
                switch (status) {
                    case "Nowe":
                        colorRes = R.color.task_new;
                        break;
                    case "W toku":
                        colorRes = R.color.task_in_progress;
                        break;
                    default:
                        colorRes = R.color.task_default;
                        break;
                }
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