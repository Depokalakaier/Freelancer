package com.example.freelancera.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.model.SyncHistory;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private List<SyncHistory> historyList;

    public HistoryAdapter(List<SyncHistory> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        SyncHistory h = historyList.get(position);
        holder.text.setText(h.getDescription());
        holder.date.setText(h.getDate());
        holder.status.setText(h.getStatus());
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView text, date, status;
        HistoryViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.history_text);
            date = v.findViewById(R.id.history_date);
            status = v.findViewById(R.id.history_status);
        }
    }
}