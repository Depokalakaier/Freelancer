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
import com.example.freelancera.model.Invoice;
import com.example.freelancera.util.JsonLoader;

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
            titleText.setText(invoice.getTitle());
            clientText.setText(invoice.getClient());
            amountText.setText(String.format("%.2f PLN", invoice.getTotalAmount()));
            dateText.setText(invoice.getIssueDate());
            paidStatusText.setText(invoice.isPaid() ? "Opłacona" : "Nieopłacona");
            paidSwitch.setChecked(invoice.isPaid());
        }

        paidSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (invoice != null) {
                    invoice.setPaid(isChecked);
                    paidStatusText.setText(isChecked ? "Opłacona" : "Nieopłacona");
                    JsonLoader.saveInvoice(getContext(), invoice);
                    Toast.makeText(getContext(), isChecked ? "Oznaczono jako opłacona" : "Oznaczono jako nieopłacona", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }
}