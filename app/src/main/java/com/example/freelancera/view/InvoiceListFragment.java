package com.example.freelancera.view;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

public class InvoiceListFragment extends Fragment implements InvoiceAdapter.OnInvoiceClickListener {

    private RecyclerView recyclerView;
    private InvoiceAdapter adapter;

    public InvoiceListFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JsonLoader.ensureInvoicesFileExists(getContext());
        View view = inflater.inflate(R.layout.fragment_invoice_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_invoices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Invoice> invoices = JsonLoader.loadInvoicesFromAppFiles(getContext());
        adapter = new InvoiceAdapter(invoices, this);
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onInvoiceClick(int position) {
        Invoice invoice = adapter.getInvoiceAt(position);
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, InvoiceDetailFragment.newInstance(invoice))
                .addToBackStack(null)
                .commit();
    }
}