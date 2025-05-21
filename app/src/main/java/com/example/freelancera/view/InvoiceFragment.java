package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.example.freelancera.R;
import com.example.freelancera.adapter.InvoiceAdapter;
import com.example.freelancera.model.Invoice;
import com.example.freelancera.util.JsonLoader;
import java.util.List;

public class InvoiceFragment extends Fragment {

    public InvoiceFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_invoice, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewInvoices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Invoice> invoices = JsonLoader.loadInvoicesFromAssets(getContext());
        InvoiceAdapter adapter = new InvoiceAdapter(invoices);
        recyclerView.setAdapter(adapter);
        return view;
    }
}