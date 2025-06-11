package com.example.freelancera.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freelancera.R;
import com.example.freelancera.models.Invoice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder> {
    private List<Invoice> invoiceList;
    private OnInvoiceClickListener listener;
    private OnPaidListener onPaidListener;

    public interface OnInvoiceClickListener {
        void onInvoiceClick(int position);
    }

    public interface OnPaidListener {
        void onInvoicePaid(Invoice invoice);
    }

    public void setOnPaidListener(OnPaidListener listener) {
        this.onPaidListener = listener;
    }

    public InvoiceAdapter(List<Invoice> invoiceList, OnInvoiceClickListener listener) {
        this.invoiceList = invoiceList;
        this.listener = listener;
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
        holder.client.setText(invoice.getClientName());
        holder.amount.setText(invoice.getTotalAmount() + " zł");
        holder.hours.setText(invoice.getHours() + "h");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy");
        holder.dueDate.setText("Termin: " + (invoice.getDueDate() != null ? sdf.format(invoice.getDueDate()) : "brak"));
        holder.status.setText(invoice.getStatus());

        if (invoice.isPaid()) {
            holder.paidStatus.setText("Opłacona");
            holder.paidStatus.setTextColor(Color.parseColor("#A8E6A3"));
            holder.status.setText("Zarchiwizowana");
        } else {
            holder.paidStatus.setText("Nieopłacona");
            holder.paidStatus.setTextColor(Color.parseColor("#cc0000"));
            holder.status.setText("");
        }

        holder.paidButton.setVisibility(invoice.isPaid() ? View.GONE : View.VISIBLE);
        holder.paidButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            Invoice inv = invoiceList.get(pos);
            inv.markAsPaid();
            // Zaktualizuj w Firebase
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            if (uid != null && inv.getId() != null) {
                firestore.collection("users").document(uid)
                    .collection("invoices").document(inv.getId())
                    .set(inv)
                    .addOnSuccessListener(aVoid -> android.util.Log.i("InvoiceAdapter", "Faktura oznaczona jako opłacona i zarchiwizowana: " + inv.getId()))
                    .addOnFailureListener(e -> android.util.Log.e("InvoiceAdapter", "Błąd archiwizacji faktury", e));
            }
            invoiceList.remove(pos);
            notifyItemRemoved(pos);
            if (onPaidListener != null) onPaidListener.onInvoicePaid(inv);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onInvoiceClick(holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return invoiceList.size();
    }

    public Invoice getInvoiceAt(int position) {
        return invoiceList.get(position);
    }

    static class InvoiceViewHolder extends RecyclerView.ViewHolder {
        TextView client, amount, hours, dueDate, status, paidStatus;
        Button paidButton;
        InvoiceViewHolder(View v) {
            super(v);
            client = v.findViewById(R.id.invoice_client);
            amount = v.findViewById(R.id.invoice_amount);
            hours = v.findViewById(R.id.invoice_hours);
            dueDate = v.findViewById(R.id.invoice_due_date);
            status = v.findViewById(R.id.invoice_status);
            paidStatus = v.findViewById(R.id.invoice_paid_status);
            paidButton = v.findViewById(R.id.invoice_paid_button);
        }
    }
}