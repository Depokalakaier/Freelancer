package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.example.freelancera.R;
import com.example.freelancera.models.Invoice;
import com.example.freelancera.util.JsonLoader;
import android.os.Build;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.freelancera.util.LocalInvoiceStorage;
import com.example.freelancera.auth.AsanaApi;

public class InvoiceDetailFragment extends Fragment {

    private static final String ARG_INVOICE = "invoice";
    private Invoice invoice;

    private TextView titleText, clientText, amountText, dateText, paidStatusText;
    private Switch paidSwitch;

    public static InvoiceDetailFragment newInstance(Invoice invoice) {
        InvoiceDetailFragment fragment = new InvoiceDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_INVOICE, invoice);
        fragment.setArguments(args);
        return fragment;
    }

    public InvoiceDetailFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_invoice_detail, container, false);

        titleText = view.findViewById(R.id.text_invoice_title);
        clientText = view.findViewById(R.id.text_invoice_client);
        amountText = view.findViewById(R.id.text_invoice_amount);
        dateText = view.findViewById(R.id.text_invoice_date);
        paidStatusText = view.findViewById(R.id.text_paid_status);
        paidSwitch = view.findViewById(R.id.switch_paid);

        if (getArguments() != null) {
            invoice = getArguments().getParcelable(ARG_INVOICE);
        }

        if (invoice != null) {
            titleText.setText(invoice.getTaskName());
            clientText.setText(invoice.getClientName());
            amountText.setText(String.format("%.2f PLN", invoice.getTotalAmount()));
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy");
            dateText.setText(sdf.format(invoice.getIssueDate()));
            paidStatusText.setText(invoice.isPaid() ? "Opłacona" : "Nieopłacona");
            paidSwitch.setChecked(invoice.isPaid());
        }

        paidSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (invoice != null) {
                    invoice.setPaid(isChecked);
                    paidStatusText.setText(isChecked ? "Opłacona" : "Nieopłacona");
                    if (isChecked) {
                        invoice.setLocalOnly(false);
                        // Zapisz do Firebase
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null && invoice.getId() != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users").document(user.getUid())
                                .collection("invoices").document(invoice.getId())
                                .set(invoice);
                        }
                        // Usuń z lokalnych faktur
                        LocalInvoiceStorage.removeInvoice(getContext(), invoice.getId());
                        // Usuń zadanie z Firestore
                        if (user != null && invoice.getTaskId() != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users").document(user.getUid())
                                .collection("tasks").document(invoice.getTaskId())
                                .delete();
                        }
                        // Usuń zadanie z Asany (jeśli jest taskId i token)
                        if (invoice.getTaskId() != null) {
                            AsanaApi.deleteTask(requireContext(), invoice.getTaskId(), new AsanaApi.DeletionCallback() {
                                @Override
                                public void onSuccess() {
                                    if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
                                }
                                @Override
                                public void onFailure(String error) {
                                    Toast.makeText(getContext(), "Błąd usuwania zadania z Asany: " + error, Toast.LENGTH_SHORT).show();
                                    if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
                                }
                            });
                        } else {
                            Toast.makeText(getContext(), "Zadanie usunięte tylko z Firebase (brak powiązania z Asaną)", Toast.LENGTH_SHORT).show();
                            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
                        }
                    } else {
                        // Jeśli cofnięto opłacenie, przywróć do lokalnych faktur
                        invoice.setLocalOnly(true);
                        LocalInvoiceStorage.saveInvoice(getContext(), invoice);
                    }
                }
            }
        });

        return view;
    }
}