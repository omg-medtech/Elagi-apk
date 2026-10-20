package app.elagi;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

final class AlarmScheduler {
    private static final String PREFS = "elagi_alarms", KEY = "items";
    private AlarmScheduler() {}

    private static PendingIntent pi(Context c, String id, JSONObject o, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setData(Uri.parse("elagi://alarm/" + id));
        if (o != null) {
            i.putExtra("id", id);
            i.putExtra("title", o.optString("title", "عِلاجي"));
            i.putExtra("body", o.optString("body", ""));
            i.putExtra("text", o.optString("text", ""));
        }
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static JSONArray load(Context c) {
        try {
            return new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void sync(Context c, String json) {
        JSONArray fresh;
        try { fresh = new JSONArray(json); } catch (Exception e) { return; }
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (fresh.toString().equals(sp.getString(KEY, ""))) return;
        cancelAll(c);
        sp.edit().putString(KEY, fresh.toString()).apply();
        scheduleAll(c);
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        JSONArray old = load(c);
        for (int k = 0; k < old.length(); k++) {
            JSONObject o = old.optJSONObject(k);
            if (o == null) continue;
            am.cancel(pi(c, o.optString("id"), null, PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        JSONArray arr = load(c);
        long now = System.currentTimeMillis();
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        for (int k = 0; k < arr.length(); k++) {
            JSONObject o = arr.optJSONObject(k);
            if (o == null) continue;
            long at = o.optLong("at");
            if (at <= now + 1000) continue;
            String id = o.optString("id");
            PendingIntent p = pi(c, id, o, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                if (exact) {
                    Intent show = new Intent(c, MainActivity.class);
                    PendingIntent sp = PendingIntent.getActivity(c, 7, show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, sp), p);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                }
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        }
    }
}
