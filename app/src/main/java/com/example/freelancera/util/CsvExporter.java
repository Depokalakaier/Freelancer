package com.example.freelancera.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.example.freelancera.model.Invoice;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class CsvExporter {
    public static void exportInvoices(Context context, List<Invoice> invoices) {
        StringBuilder sb = new StringBuilder();
        sb.append("Klient,Kwota,Termin\n");
        for (Invoice i : invoices) {
            sb.append(i.getClient()).append(",").append(i.getTotalAmount()).append(",").append(i.getDueDate()).append("\n");
        }
        try {
            File file = new File(context.getExternalFilesDir(null), "invoices.csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes());
            fos.close();
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(Intent.createChooser(intent, "Udostępnij CSV"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}