package com.example.freelancera.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.adapter.InvoiceAdapter;
import com.example.freelancera.models.Invoice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.freelancera.util.LocalInvoiceStorage;
import java.util.ArrayList;
import java.util.List;

public class InvoiceListFragment extends Fragment {
    private RecyclerView recyclerView;
    private InvoiceAdapter adapter;
    private List<Invoice> invoices = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invoice_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_invoices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InvoiceAdapter(invoices, position -> {
            Invoice invoice = adapter.getInvoiceAt(position);
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, InvoiceDetailFragment.newInstance(invoice))
                .addToBackStack(null)
                .commit();
        });
        recyclerView.setAdapter(adapter);
        // Ładuj faktury tylko z lokalnej pamięci
        invoices.clear();
        invoices.addAll(com.example.freelancera.util.LocalInvoiceStorage.loadLocalInvoices(getContext()));
        adapter.notifyDataSetChanged();
    }

    private void loadInvoices() {
        invoices.clear();
        if (user == null) return;
        firestore.collection("users").document(user.getUid())
            .collection("tasks")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    com.example.freelancera.models.Task task = doc.toObject(com.example.freelancera.models.Task.class);
                    if ("Ukończone".equals(task.getStatus())) {
                        double hours = (task.getTogglTrackedSeconds() > 0 ? task.getTogglTrackedSeconds() : task.getTotalTimeInSeconds()) / 3600.0;
                        double rate = task.getRatePerHour();
                        double amount = hours * rate;
                        com.example.freelancera.models.Invoice invoice = new com.example.freelancera.models.Invoice();
                        invoice.setTaskId(task.getId());
                        invoice.setTaskName(task.getName());
                        invoice.setClientName(task.getClient());
                        // Zaokrąglanie godzin jak w zadaniach
                        int intHours = (int) hours;
                        int minutes = (int) ((hours - intHours) * 60);
                        double rounded = intHours;
                        if (minutes >= 16 && minutes <= 44) rounded += 0.5;
                        else if (minutes >= 45) rounded += 1.0;
                        else if (minutes > 0) rounded += 1.0;
                        invoice.setHours(rounded);
                        invoice.setRatePerHour(rate);
                        invoice.setTotalAmount(rounded * rate);
                        invoice.setIssueDate(task.getCompletedAt() != null ? task.getCompletedAt() : new java.util.Date());
                        invoice.setDueDate(new java.util.Date(invoice.getIssueDate().getTime() + 7 * 24 * 60 * 60 * 1000));
                        invoice.setId("FV-" + new java.text.SimpleDateFormat("yyyyMMdd").format(invoice.getIssueDate()) + "-" + task.getId());
                        invoice.setStatus("DRAFT");
                        invoice.setPaid(false);
                        invoices.add(invoice);
                    }
                }
                adapter.notifyDataSetChanged();
            });
    }
}