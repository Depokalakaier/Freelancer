package com.example.freelancera.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import java.util.Calendar;

public class CalendarUtils {

    public static void addEventToCalendar(Context context, String title, String description, long timeInMillis) {
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, description)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, timeInMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, timeInMillis + 60 * 60 * 1000);
        context.startActivity(intent);
    }

    // Alternatywnie, z ContentResolver (wymaga uprawnień)
    public static void addEventDirectly(Context context, String title, String description, long timeInMillis) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, timeInMillis);
        values.put(CalendarContract.Events.DTEND, timeInMillis + 60 * 60 * 1000);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.CALENDAR_ID, 1);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Warsaw");
        context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
    }
}