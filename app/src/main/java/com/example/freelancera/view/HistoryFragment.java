package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.example.freelancera.R;
import com.example.freelancera.adapter.HistoryAdapter;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.util.JsonLoader;
import java.util.List;

public class HistoryFragment extends Fragment {

    public HistoryFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<SyncHistory> history = JsonLoader.loadHistoryFromAssets(getContext());
        HistoryAdapter adapter = new HistoryAdapter(history);
        recyclerView.setAdapter(adapter);
        return view;
    }
}