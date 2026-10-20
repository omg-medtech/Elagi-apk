package app.elagi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

/** Plays the bell, then speaks the reminder aloud, even if the app is closed. */
public class AlarmService extends Service {
    private static final String CH = "elagi_alarm";
    private static final int NID = 4201;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private MediaPlayer mp;
    private PowerManager.WakeLock wl;
    private int gen = 0;
    private String lastTitle = "عِلاجي", lastBody = "";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { finish(); return START_NOT_STICKY; }
        final int my = ++gen;
        String title = intent != null ? intent.getStringExtra("title") : null;
        String body = intent != null ? intent.getStringExtra("body") : null;
        final String text = intent != null ? intent.getStringExtra("text") : null;
        if (title == null) title = "عِلاجي";
        if (body == null) body = "";
        lastTitle = title; lastBody = body;
        channel();
        Notification n = build(title, body, true);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NID, n);
        } catch (Exception e) {
            try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NID, n); } catch (Exception ignored) {}
        }
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elagi:alarm");
            }
            if (!wl.isHeld()) wl.acquire(3 * 60 * 1000L);
        } catch (Exception ignored) {}
        releasePlayers();
        chime(new Runnable() { @Override public void run() { if (my == gen) speak(text, my); } });
        h.postDelayed(new Runnable() { @Override public void run() { if (my == gen) finish(); } }, 90 * 1000L);
        return START_NOT_STICKY;
    }

    private void channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "تنبيهات الأدوية", NotificationManager.IMPORTANCE_HIGH);
            c.setSound(null, null);
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0, 400, 150, 400, 150, 600});
            c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification build(String title, String body, boolean ongoing) {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("alarm", true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 11, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, AlarmService.class).setAction("STOP");
        PendingIntent sp = PendingIntent.getService(this, 12, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .addAction(0, "إيقاف الصوت", sp)
                .setOngoing(ongoing).setAutoCancel(!ongoing);
        if (ongoing) b.setFullScreenIntent(pi, true);
        return b.build();
    }

    private void chime(final Runnable done) {
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(this, u);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { done.run(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int w, int e) { done.run(); return true; }
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            done.run();
        }
    }

    private void speak(final String text, final int my) {
        if (text == null || text.trim().isEmpty()) { finish(); return; }
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (my != gen) return;
                if (status != TextToSpeech.SUCCESS || tts == null) { finish(); return; }
                try { tts.setLanguage(new Locale("ar")); } catch (Exception ignored) {}
                try {
                    tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                } catch (Exception ignored) {}
                tts.setSpeechRate(0.9f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onError(String id) { if ("last".equals(id)) h.post(new Runnable() { @Override public void run() { finish(); } }); }
                    @Override public void onDone(String id) { if ("last".equals(id)) h.post(new Runnable() { @Override public void run() { finish(); } }); }
                });
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "a");
                tts.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, "s1");
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "b");
                tts.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, "s2");
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "last");
            }
        });
    }

    private void releasePlayers() {
        try { if (mp != null) { mp.release(); } } catch (Exception ignored) {}
        mp = null;
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) {}
        tts = null;
    }

    private void finish() {
        releasePlayers();
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_DETACH); else stopForeground(false);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NID, build(lastTitle, lastBody, false)); } catch (Exception ignored) {}
        stopSelf();
    }

    @Override public void onDestroy() { releasePlayers(); super.onDestroy(); }
}
