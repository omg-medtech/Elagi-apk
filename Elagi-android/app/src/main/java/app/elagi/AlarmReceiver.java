package app.elagi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (MainActivity.visible) return; // the open app rings by itself
        Intent s = new Intent(c, AlarmService.class);
        s.putExtra("id", intent.getStringExtra("id"));
        s.putExtra("title", intent.getStringExtra("title"));
        s.putExtra("body", intent.getStringExtra("body"));
        s.putExtra("text", intent.getStringExtra("text"));
        ContextCompat.startForegroundService(c, s);
    }
}
