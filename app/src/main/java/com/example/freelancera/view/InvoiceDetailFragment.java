package com.example.freelancera.view;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.freelancera.R;
import com.example.freelancera.models.Invoice;
import android.os.Build;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.freelancera.util.LocalInvoiceStorage;
import com.example.freelancera.auth.AsanaApi;
import android.util.Log;

public class InvoiceDetailFragment extends Fragment {

    private static final String ARG_INVOICE = "invoice";
    private Invoice invoice;

    private TextView headerText, numberText, issueDateText, clientText, clientAddressText, taskDescText, hoursText, rateText, amountText, totalText, dueDateText, footerText;

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

        headerText = view.findViewById(R.id.invoice_header);
        numberText = view.findViewById(R.id.invoice_number);
        issueDateText = view.findViewById(R.id.invoice_issue_date);
        clientText = view.findViewById(R.id.invoice_client);
        clientAddressText = view.findViewById(R.id.invoice_client_address);
        taskDescText = view.findViewById(R.id.invoice_task_desc);
        hoursText = view.findViewById(R.id.invoice_hours);
        rateText = view.findViewById(R.id.invoice_rate);
        amountText = view.findViewById(R.id.invoice_amount);
        totalText = view.findViewById(R.id.invoice_total);
        dueDateText = view.findViewById(R.id.invoice_due_date);
        footerText = view.findViewById(R.id.invoice_footer);

        if (getArguments() != null) {
            invoice = getArguments().getParcelable(ARG_INVOICE);
        }

        if (invoice != null) {
            headerText.setText("FAKTURA");
            numberText.setText("Nr: " + (invoice.getId() != null ? invoice.getId() : "-"));
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy");
            String issueDateStr = "-";
            String dueDateStr = "-";
            try {
                if (invoice.getIssueDate() != null) issueDateStr = sdf.format(invoice.getIssueDate());
            } catch (Exception e) {
                Log.w("InvoiceDetail", "Błąd formatowania issueDate", e);
            }
            try {
                if (invoice.getDueDate() != null) dueDateStr = sdf.format(invoice.getDueDate());
            } catch (Exception e) {
                Log.w("InvoiceDetail", "Błąd formatowania dueDate", e);
            }
            issueDateText.setText("Data wystawienia: " + issueDateStr);
            clientText.setText("Klient: " + (invoice.getClientName() != null ? invoice.getClientName() : "-"));
            clientAddressText.setText("Adres: (adres przykładowy)");
            taskDescText.setText(invoice.getTaskName() != null ? invoice.getTaskName() : "-");
            hoursText.setText(String.valueOf(invoice.getHours()));
            rateText.setText(String.format("%.2f zł", invoice.getRatePerHour()));
            amountText.setText(String.format("%.2f zł", invoice.getTotalAmount()));
            totalText.setText("Do zapłaty: " + String.format("%.2f zł", invoice.getTotalAmount()));
            dueDateText.setText("Termin płatności: " + dueDateStr);
            footerText.setText("(To jest faktura robocza, wygenerowana automatycznie)");
        } else {
            Log.e("InvoiceDetail", "Invoice przekazany do fragmentu jest nullem!");
            headerText.setText("FAKTURA");
            numberText.setText("Nr: -");
            issueDateText.setText("Data wystawienia: -");
            clientText.setText("Klient: -");
            clientAddressText.setText("Adres: -");
            taskDescText.setText("-");
            hoursText.setText("-");
            rateText.setText("-");
            amountText.setText("-");
            totalText.setText("Do zapłaty: -");
            dueDateText.setText("Termin płatności: -");
            footerText.setText("(To jest faktura robocza, wygenerowana automatycznie)");
                    }

        return view;
    }
}