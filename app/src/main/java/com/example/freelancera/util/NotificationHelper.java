package com.example.freelancera.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.freelancera.R;

public class NotificationHelper {
    public static void showNotification(Context context, String title, String message, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "freelancera_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Powiadomienia", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
              //  .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(message)
                .setAutoCancel(true);
        nm.notify(id, builder.build());
    }
}