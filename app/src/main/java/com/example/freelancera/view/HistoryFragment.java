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
import com.example.freelancera.adapter.InvoiceAdapter;
import com.example.freelancera.model.SyncHistory;
import com.example.freelancera.models.Invoice;
import com.example.freelancera.util.JsonLoader;
import java.util.List;
import java.util.ArrayList;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HistoryFragment extends Fragment {
    private RecyclerView recyclerViewInvoices;
    private InvoiceAdapter invoiceAdapter;
    private List<Invoice> paidInvoices = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    public HistoryFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<SyncHistory> history = JsonLoader.loadHistoryFromAssets(getContext());
        HistoryAdapter adapter = new HistoryAdapter(history);
        recyclerView.setAdapter(adapter);

        // Użyj RecyclerView z XML do faktur
        recyclerViewInvoices = view.findViewById(R.id.recyclerViewPaidInvoices);
        recyclerViewInvoices.setLayoutManager(new LinearLayoutManager(getContext()));
        invoiceAdapter = new InvoiceAdapter(paidInvoices, position -> {
            Invoice invoice = invoiceAdapter.getInvoiceAt(position);
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, com.example.freelancera.view.InvoiceDetailFragment.newInstance(invoice))
                .addToBackStack(null)
                .commit();
        });
        recyclerViewInvoices.setAdapter(invoiceAdapter);
        loadPaidInvoices();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPaidInvoices();
    }

    public void loadPaidInvoices() {
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        if (user == null) return;
        firestore.collection("users").document(user.getUid())
            .collection("invoices")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                paidInvoices.clear();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    Invoice inv = doc.toObject(Invoice.class);
                    if (inv.isPaid() || "PAID".equalsIgnoreCase(inv.getStatus())) {
                        paidInvoices.add(inv);
                        android.util.Log.d("HistoryFragment", "Załadowano fakturę: " + inv.getId() + ", klient: " + inv.getClientName());
                    }
                }
                invoiceAdapter.notifyDataSetChanged();
                android.util.Log.d("HistoryFragment", "Liczba opłaconych faktur: " + paidInvoices.size());
            });
    }
}