package com.example.gameboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Game ke upar floating BOOST bubble dikhata hai.
 * Tap = background apps clear, Long press = bubble band.
 * Drag karke kahin bhi rakh sakte ho.
 */
public class BoostOverlayService extends Service {

    static final String EXTRA_GAME = "game";
    private static final String CHANNEL_ID = "boost_overlay";
    private static final int NOTIF_ID = 1001;
    private static final int LONG_PRESS_MS = 1000;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private TextView bubble;
    private String gamePkg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopRunnable = this::stopSelf;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            gamePkg = intent.getStringExtra(EXTRA_GAME);
        }
        startAsForeground();
        if (bubble == null) {
            addBubble();
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Boost", NotificationManager.IMPORTANCE_LOW));
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Game Booster active")
                    .setContentText("Floating boost button chal raha hai")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("Game Booster active")
                    .setContentText("Floating boost button chal raha hai")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addBubble() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        bubble = new TextView(this);
        bubble.setText("⚡\nBOOST");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(12);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xDDFF5722);
        bubble.setBackground(bg);

        int size = dp(68);
        lp = new WindowManager.LayoutParams(
                size,
                size,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = dp(200);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = lp.x;
                        initY = lp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        handler.postDelayed(stopRunnable, LONG_PRESS_MS);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - touchX);
                        int dy = (int) (e.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            moved = true;
                            handler.removeCallbacks(stopRunnable);
                        }
                        lp.x = initX + dx;
                        lp.y = initY + dy;
                        wm.updateViewLayout(bubble, lp);
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(stopRunnable);
                        if (!moved) onBubbleTap();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(stopRunnable);
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubble, lp);
    }

    private void onBubbleTap() {
        if (gamePkg == null) {
            Toast.makeText(this, "Game select nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        final long before = BoostUtil.availRam(this);
        final int killed = BoostUtil.killBackgroundApps(this, gamePkg);

        bubble.setText("✔\n" + killed);
        handler.postDelayed(() -> {
            long freed = Math.max(0, BoostUtil.availRam(this) - before) / BoostUtil.MB;
            Toast.makeText(this,
                    "Boost: " + killed + " apps band, ~" + freed + " MB free",
                    Toast.LENGTH_SHORT).show();
            if (bubble != null) bubble.setText("⚡\nBOOST");
        }, 1500);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (bubble != null && wm != null) {
            try {
                wm.removeView(bubble);
            } catch (IllegalArgumentException ignored) {
                // view pehle hi remove ho chuka tha
            }
        }
        bubble = null;
        super.onDestroy();
    }
}
