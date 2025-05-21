package com.example.freelancera.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.model.Invoice;
import java.util.List;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder> {
    private List<Invoice> invoiceList;

    public InvoiceAdapter(List<Invoice> invoiceList) {
        this.invoiceList = invoiceList;
    }

    @NonNull
    @Override
    public InvoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invoice, parent, false);
        return new InvoiceViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull InvoiceViewHolder holder, int position) {
        Invoice invoice = invoiceList.get(position);
        holder.client.setText(invoice.getClient());
        holder.amount.setText(invoice.getTotalAmount() + " zł");
        holder.hours.setText(invoice.getHoursWorked() + "h");
        holder.dueDate.setText("Termin: " + invoice.getDueDate());
        holder.status.setText(invoice.isSent() ? "Wysłana" : "Robocza");
    }

    @Override
    public int getItemCount() {
        return invoiceList.size();
    }

    static class InvoiceViewHolder extends RecyclerView.ViewHolder {
        TextView client, amount, hours, dueDate, status;
        InvoiceViewHolder(View v) {
            super(v);
            client = v.findViewById(R.id.invoice_client);
            amount = v.findViewById(R.id.invoice_amount);
            hours = v.findViewById(R.id.invoice_hours);
            dueDate = v.findViewById(R.id.invoice_due_date);
            status = v.findViewById(R.id.invoice_status);
        }
    }
}