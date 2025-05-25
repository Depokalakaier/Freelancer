package com.example.freelancera.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.models.SyncHistory;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SyncHistoryAdapter extends RecyclerView.Adapter<SyncHistoryAdapter.ViewHolder> {
    private List<SyncHistory> historyList;
    private final SimpleDateFormat dateFormat;

    public SyncHistoryAdapter(List<SyncHistory> historyList) {
        this.historyList = historyList;
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sync_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SyncHistory history = historyList.get(position);
        holder.messageText.setText(history.getFormattedMessage());
        holder.timestampText.setText(dateFormat.format(history.getTimestamp()));
        
        // Ustaw kolor tekstu w zależności od sukcesu/błędu
        int textColor = history.isSuccess() ? 
            holder.itemView.getContext().getColor(android.R.color.black) :
            holder.itemView.getContext().getColor(android.R.color.holo_red_dark);
        holder.messageText.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public void updateData(List<SyncHistory> newHistoryList) {
        this.historyList = newHistoryList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestampText;

        ViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_sync_message);
            timestampText = itemView.findViewById(R.id.text_sync_timestamp);
        }
    }
} 