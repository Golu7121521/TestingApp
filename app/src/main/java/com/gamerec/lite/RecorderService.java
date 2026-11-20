package com.gamerec.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class RecorderService extends Service {

    public static final String ACTION_START = "com.gamerec.lite.START";
    public static final String ACTION_PAUSE = "com.gamerec.lite.PAUSE";
    public static final String ACTION_RESUME = "com.gamerec.lite.RESUME";
    public static final String ACTION_STOP = "com.gamerec.lite.STOP";

    public static final int STATE_IDLE = 0;
    public static final int STATE_COUNTDOWN = 1;
    public static final int STATE_RECORDING = 2;
    public static final int STATE_SAVING = 3;

    // Shared with MainActivity so the button can show Start / Stop
    public static volatile int state = STATE_IDLE;
    public static volatile Runnable listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void setState(int s) {
        state = s;
        Runnable l = listener;
        if (l != null) MAIN.post(l);
    }

    private static final String CHANNEL_ID = "rec_channel";
    private static final int NOTIF_ID = 1;
    private static final int SAVED_ID = 2;
    private static final int COUNTDOWN_SEC = 4;

    private Recorder rec;
    private boolean paused = false;
    private boolean starting = false;
    private int countdown = 0;
    private Runnable tick;
    private long runStart = 0, accum = 0;
    private String summary = "";

    private WindowManager wm;
    private TextView overlay;

    private final Runnable timeTick = new Runnable() {
        @Override
        public void run() {
            if (rec != null && !paused) {
                updateNotification();
                MAIN.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        switch (action) {
            case ACTION_START:
                if (rec == null && !starting && state != STATE_SAVING) startRecording(intent);
                break;
            case ACTION_PAUSE:
                if (rec != null && !paused) {
                    rec.pause();
                    accum += System.currentTimeMillis() - runStart;
                    paused = true;
                    MAIN.removeCallbacks(timeTick);
                    updateNotification();
                }
                break;
            case ACTION_RESUME:
                if (rec != null && paused) {
                    rec.resume();
                    runStart = System.currentTimeMillis();
                    paused = false;
                    updateNotification();
                    MAIN.removeCallbacks(timeTick);
                    MAIN.postDelayed(timeTick, 1000);
                }
                break;
            case ACTION_STOP:
                stopAndSave();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void startRecording(final Intent intent) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW));
        }

        final int audio = intent.getIntExtra("audio", 0);
        final int ori = intent.getIntExtra("ori", 0);
        boolean mic = audio == 2 || audio == 3;
        summary = intent.getIntExtra("res", 720) + "p | " + intent.getIntExtra("fps", 30) + " fps | "
                + intent.getIntExtra("mbps", 8) + " Mbps" + (mic ? " | Mic" : "");
        paused = false;
        accum = 0;
        starting = true;
        countdown = COUNTDOWN_SEC;

        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        | (mic ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0);
                startForeground(NOTIF_ID, n, type);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            starting = false;
            setState(STATE_IDLE);
            Toast.makeText(this, "Could not start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        setState(STATE_COUNTDOWN);
        showOverlay(countdown);
        tick = new Runnable() {
            @Override
            public void run() {
                countdown--;
                if (countdown > 0) {
                    updateNotification();
                    setOverlayNumber(countdown);
                    MAIN.postDelayed(this, 1000);
                } else {
                    tick = null;
                    hideOverlay();
                    // small gap so the overlay is gone before the first recorded frame
                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (starting) beginRecording(intent, audio, ori);
                        }
                    }, 250);
                }
            }
        };
        MAIN.postDelayed(tick, 1000);
    }

    private void beginRecording(Intent intent, int audio, int ori) {
        starting = false;
        Recorder r = null;
        MediaProjection mp = null;
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent data = (Intent) intent.getParcelableExtra("data");
            mp = mpm.getMediaProjection(intent.getIntExtra("code", 0), data);
            if (mp == null) throw new Exception("Screen capture permission is invalid");
            r = new Recorder(this, mp,
                    intent.getIntExtra("res", 720),
                    intent.getIntExtra("fps", 30),
                    intent.getIntExtra("mbps", 8) * 1000000,
                    audio,
                    ori,
                    this::stopAndSave);
            r.start();
            rec = r;
            accum = 0;
            runStart = System.currentTimeMillis();
            setState(STATE_RECORDING);
            updateNotification();
            MAIN.removeCallbacks(timeTick);
            MAIN.postDelayed(timeTick, 1000);
        } catch (Exception e) {
            if (r != null) {
                try { r.stop(); } catch (Exception ignored) { }
            } else if (mp != null) {
                try { mp.stop(); } catch (Exception ignored) { }
            }
            setState(STATE_IDLE);
            Toast.makeText(this, "Could not start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopForeground(true);
            stopSelf();
        }
    }

    // ---------- on-screen countdown (needs "Display over other apps"; optional) ----------
    private void showOverlay(int number) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xCC000000);
            bg.setStroke((int) (3 * getResources().getDisplayMetrics().density), 0xFFFF1744);
            tv.setBackground(bg);
            tv.setText(String.valueOf(number));
            int size = (int) (150 * getResources().getDisplayMetrics().density);
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(tv, lp);
            overlay = tv;
        } catch (Throwable t) {
            overlay = null;
        }
    }

    private void setOverlayNumber(int number) {
        if (overlay != null) overlay.setText(String.valueOf(number));
    }

    private void hideOverlay() {
        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Throwable ignored) { }
        }
        overlay = null;
    }

    // ---------- notification ----------
    private PendingIntent action(String action, int code) {
        Intent i = new Intent(this, RecorderService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, code, i, flags);
    }

    private Notification.Builder newBuilder() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID);
        }
        return new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
    }

    private long elapsedMs() {
        return accum + (paused ? 0 : System.currentTimeMillis() - runStart);
    }

    private static String fmt(long ms) {
        long t = ms / 1000;
        long h = t / 3600;
        long m = (t % 3600) / 60;
        long s = t % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private Notification buildNotification() {
        boolean ph = paused;
        Notification.Action stop = new Notification.Action.Builder(
                android.R.drawable.ic_menu_save,
                starting ? "Cancel" : "Stop & Save",
                action(ACTION_STOP, 11)).build();

        String title;
        String text;
        int color;
        if (starting) {
            title = "Starting in " + countdown + " sec";
            text = "Open your game now";
            color = 0xFFFFD600;
        } else if (ph) {
            title = "Paused  " + fmt(elapsedMs());
            text = summary;
            color = 0xFFFFAB00;
        } else {
            title = "Recording  " + fmt(elapsedMs());
            text = summary;
            color = 0xFFFF1744;
        }

        Notification.Builder nb = newBuilder()
                .setSmallIcon(R.drawable.ic_stat_rec)
                .setColor(color)
                .setSubText("Game Recorder")
                .setContentTitle(title)
                .setContentText(text)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (!starting) {
            Notification.Action toggle = new Notification.Action.Builder(
                    ph ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                    ph ? "Resume" : "Pause",
                    action(ph ? ACTION_RESUME : ACTION_PAUSE, 10)).build();
            nb.addAction(toggle);
        }
        nb.addAction(stop);
        return nb.build();
    }

    private void updateNotification() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, buildNotification());
    }

    private void stopAndSave() {
        final Recorder r = rec;
        rec = null;
        MAIN.removeCallbacks(timeTick);
        if (r == null) {
            if (starting) {
                if (tick != null) MAIN.removeCallbacks(tick);
                tick = null;
                starting = false;
                hideOverlay();
                stopForeground(true);
            }
            setState(STATE_IDLE);
            stopSelf();
            return;
        }
        setState(STATE_SAVING);
        new Thread(() -> {
            Uri u = null;
            try {
                u = r.stop();
            } catch (Exception ignored) { }
            stopForeground(true);
            notifySaved(u, r.saved);
            setState(STATE_IDLE);
            stopSelf();
        }).start();
    }

    private void notifySaved(Uri uri, boolean ok) {
        Notification.Builder b = newBuilder()
                .setSmallIcon(R.drawable.ic_stat_rec)
                .setColor(0xFF00E676)
                .setSubText("Game Recorder")
                .setAutoCancel(true);
        if (ok) {
            b.setContentTitle("Recording saved").setContentText("Movies/GameRec");
            if (uri != null) {
                Intent view = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
                b.setContentIntent(PendingIntent.getActivity(this, 20, view, flags));
            }
        } else {
            b.setContentTitle("Recording could not be saved");
        }
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(SAVED_ID, b.build());
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacks(timeTick);
        if (tick != null) MAIN.removeCallbacks(tick);
        tick = null;
        starting = false;
        hideOverlay();
        Recorder r = rec;
        rec = null;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) { }
        }
        setState(STATE_IDLE);
        super.onDestroy();
    }
}
