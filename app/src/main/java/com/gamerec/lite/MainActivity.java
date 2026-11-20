package com.gamerec.lite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 1;
    private static final int REQ_CAPTURE = 2;

    private static final int C_BG1 = 0xFF0B0E14;
    private static final int C_BG2 = 0xFF121A2B;
    private static final int C_CARD = 0xFF161B26;
    private static final int C_LINE = 0xFF222C3D;
    private static final int C_FIELD = 0xFF0F131C;
    private static final int C_CYAN = 0xFF00E5FF;
    private static final int C_MUTED = 0xFF7A889B;
    private static final int C_WHITE = 0xFFF0F4F8;

    // everything the app can offer; the list shown is trimmed to what this phone supports
    private static final int[] RES_ALL = {240, 360, 480, 720, 1080, 1440, 2160};
    private static final int[] FPS_ALL = {10, 15, 20, 24, 25, 30, 45, 50, 60, 72, 90, 120, 144};
    private static final int[] BIT_ALL = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 80, 100};
    private final String[] audioLabels = {"Device audio", "Device + Mic"};
    private final String[] oriLabels = {"Auto", "Landscape", "Portrait"};

    private DD ddRes, ddFps, ddBit;
    private LinearLayout card;
    private Button startBtn;
    private DeviceCaps caps;
    private Seg segAudio, segOri;
    private SharedPreferences prefs;
    private float density;
    private boolean audioDenied = false;

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private boolean has(String perm) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    // 1 = device audio, 2 = device + mic, 3 = mic only (Android 9 and below)
    private int audioMode() {
        if (Build.VERSION.SDK_INT >= 29) {
            return segAudio.sel == 1 ? 2 : 1;
        }
        return 3;
    }

    private TextView label(LinearLayout parent, String text) {
        TextView lb = new TextView(this);
        lb.setText(text);
        lb.setTextColor(C_MUTED);
        lb.setTextSize(11);
        lb.setLetterSpacing(0.1f);
        lb.setPadding(dp(2), dp(16), 0, dp(6));
        parent.addView(lb);
        return lb;
    }

    private String resLabel(int res) {
        switch (res) {
            case 720:
                return "720p (HD)";
            case 1080:
                return "1080p (Full HD)";
            case 1440:
                return "1440p (2K)";
            case 2160:
                return "2160p (4K)";
            default:
                return res + "p";
        }
    }

    private String[] resLabels(int[] vals) {
        String[] out = new String[vals.length];
        for (int i = 0; i < vals.length; i++) out[i] = resLabel(vals[i]);
        return out;
    }

    private String[] numbered(int[] vals, String suffix) {
        String[] out = new String[vals.length];
        for (int i = 0; i < vals.length; i++) out[i] = vals[i] + suffix;
        return out;
    }

    // Dropdown selector
    private class DD {
        final Spinner sp;
        int[] vals = new int[0];
        Runnable onChange;

        DD(LinearLayout parent, String title, String[] labels, int[] vals, int selValue) {
            label(parent, title);

            FrameLayout frame = new FrameLayout(MainActivity.this);
            sp = new Spinner(MainActivity.this);
            sp.setBackground(rounded(C_FIELD, 14, C_LINE));
            sp.setPopupBackgroundDrawable(rounded(C_CARD, 12, C_LINE));
            frame.addView(sp, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

            ImageView arrow = new ImageView(MainActivity.this);
            arrow.setImageResource(R.drawable.ic_arrow);
            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                    dp(24), dp(24), Gravity.END | Gravity.CENTER_VERTICAL);
            alp.rightMargin = dp(12);
            frame.addView(arrow, alp);

            parent.addView(frame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            setOptions(labels, vals, selValue);
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View v, int position, long id) {
                    if (onChange != null) onChange.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) { }
            });
        }

        void setOptions(String[] labels, int[] newVals, int prefer) {
            vals = newVals;
            ArrayAdapter<String> ad = new ArrayAdapter<String>(MainActivity.this,
                    android.R.layout.simple_spinner_item, labels) {
                @Override
                public View getView(int pos, View cv, ViewGroup p) {
                    TextView t = (TextView) super.getView(pos, cv, p);
                    t.setTextColor(C_WHITE);
                    t.setTextSize(16);
                    t.setGravity(Gravity.CENTER_VERTICAL);
                    t.setMinHeight(dp(50));
                    t.setPadding(dp(14), 0, dp(44), 0);
                    return t;
                }

                @Override
                public View getDropDownView(int pos, View cv, ViewGroup p) {
                    TextView t = (TextView) super.getDropDownView(pos, cv, p);
                    t.setTextColor(C_WHITE);
                    t.setTextSize(16);
                    t.setBackgroundColor(C_CARD);
                    t.setPadding(dp(16), dp(12), dp(16), dp(12));
                    return t;
                }
            };
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sp.setAdapter(ad);
            // keep the closest option that does not exceed the wanted value
            int idx = 0;
            for (int i = 0; i < newVals.length; i++) {
                if (newVals[i] <= prefer) idx = i;
            }
            sp.setSelection(idx);
        }

        int value() {
            int pos = sp.getSelectedItemPosition();
            if (pos < 0 || pos >= vals.length) pos = 0;
            return vals[pos];
        }
    }

    // Tap selector for short option lists
    private class Seg {
        int sel;
        final TextView[] tabs;

        Seg(LinearLayout parent, String title, String[] items, int initial) {
            label(parent, title);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(4), dp(4), dp(4), dp(4));
            row.setBackground(rounded(C_FIELD, 14, C_LINE));
            tabs = new TextView[items.length];
            sel = Math.max(0, Math.min(initial, items.length - 1));
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                TextView t = new TextView(MainActivity.this);
                t.setText(items[i]);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(14);
                t.setPadding(dp(4), dp(12), dp(4), dp(12));
                t.setOnClickListener(v -> {
                    sel = idx;
                    refresh();
                });
                tabs[i] = t;
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            parent.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            refresh();
        }

        void refresh() {
            for (int i = 0; i < tabs.length; i++) {
                if (i == sel) {
                    tabs[i].setBackground(rounded(C_CYAN, 10, 0));
                    tabs[i].setTextColor(C_BG1);
                    tabs[i].setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    tabs[i].setBackground(null);
                    tabs[i].setTextColor(C_MUTED);
                    tabs[i].setTypeface(Typeface.DEFAULT);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;

        // Edge-to-edge with a transparent status bar and navigation bar
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            win.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams wlp = win.getAttributes();
            wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(wlp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            win.setDecorFitsSystemWindows(false);
        } else {
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("GAME RECORDER");
        title.setTextColor(C_CYAN);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.14f);
        title.setPadding(0, 0, 0, dp(22));
        root.addView(title);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackground(rounded(C_CARD, 18, C_LINE));
        root.addView(card);

        TextView head = new TextView(this);
        head.setText("RECORDING SETTINGS");
        head.setTextColor(C_CYAN);
        head.setTextSize(11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.12f);
        card.addView(head);

        caps = new DeviceCaps(this);
        int[] resVals = caps.resolutions(RES_ALL);
        ddRes = new DD(card, "RESOLUTION", resLabels(resVals), resVals, prefs.getInt("res_v", 720));
        int[] fpsVals = caps.fps(FPS_ALL, ddRes.value());
        ddFps = new DD(card, "FPS", numbered(fpsVals, " fps"), fpsVals, prefs.getInt("fps_v", 30));
        int[] bitVals = caps.bitrates(BIT_ALL);
        ddBit = new DD(card, "BITRATE", numbered(bitVals, " Mbps"), bitVals, prefs.getInt("bit_v", 8));
        ddRes.onChange = () -> {
            int[] f = caps.fps(FPS_ALL, ddRes.value());
            ddFps.setOptions(numbered(f, " fps"), f, ddFps.value());
        };
        if (Build.VERSION.SDK_INT >= 29) {
            segAudio = new Seg(card, "AUDIO", audioLabels, prefs.getInt("audio", 0));
        }
        segOri = new Seg(card, "ORIENTATION", oriLabels, prefs.getInt("ori", 0));

        Button start = new Button(this);
        start.setText("START RECORDING");
        start.setAllCaps(false);
        start.setTextColor(Color.WHITE);
        start.setTextSize(16);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setLetterSpacing(0.08f);
        start.setStateListAnimator(null);
        startBtn = start;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = dp(26);
        root.addView(start, lp);
        start.setOnClickListener(v -> onStartClicked());

        final ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{C_BG1, C_BG2}));
        sv.addView(root);
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets in = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = in.left;
                t = in.top;
                r = in.right;
                b = in.bottom;
            } else {
                l = ins.getSystemWindowInsetLeft();
                t = ins.getSystemWindowInsetTop();
                r = ins.getSystemWindowInsetRight();
                b = ins.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && ins.getDisplayCutout() != null) {
                    t = Math.max(t, ins.getDisplayCutout().getSafeInsetTop());
                }
            }
            v.setPadding(l, t, r, b);
            return ins;
        });
        setContentView(sv);
        showIntroIfNeeded();
    }

    private void showIntroIfNeeded() {
        if (prefs.getBoolean("intro_done", false)) return;
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Before you start")
                .setMessage("These steps help on most phones:\n\n"
                        + "1. Open Settings > Apps > Game Recorder > Battery and choose Unrestricted "
                        + "(or Allow background activity). If your phone has an Autostart option, turn it ON. "
                        + "Otherwise the system may stop the recording in the background.\n\n"
                        + "2. Some games block device audio capture. If the game sound is missing "
                        + "in your video, choose Device + Mic.")
                .setPositiveButton("Open app settings", (dlg, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) { }
                })
                .setNegativeButton("Got it", null)
                .create();
        dialog.setOnDismissListener(d -> prefs.edit().putBoolean("intro_done", true).apply());
        dialog.show();
    }

    private void saveSettings() {
        SharedPreferences.Editor e = prefs.edit()
                .putInt("res_v", ddRes.value())
                .putInt("fps_v", ddFps.value())
                .putInt("bit_v", ddBit.value())
                .putInt("ori", segOri.sel);
        if (segAudio != null) e.putInt("audio", segAudio.sel);
        e.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RecorderService.listener = this::updateButton;
        updateButton();
    }

    @Override
    protected void onPause() {
        RecorderService.listener = null;
        saveSettings();
        super.onPause();
    }

    private void setEnabledRecursive(View v, boolean en) {
        v.setEnabled(en);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledRecursive(g.getChildAt(i), en);
        }
    }

    // Start button turns into a Stop button while a recording (or countdown) is running
    private void updateButton() {
        int st = RecorderService.state;
        boolean active = st == RecorderService.STATE_COUNTDOWN || st == RecorderService.STATE_RECORDING;
        boolean saving = st == RecorderService.STATE_SAVING;
        if (saving) {
            startBtn.setText("SAVING...");
            startBtn.setEnabled(false);
            startBtn.setAlpha(0.6f);
        } else {
            startBtn.setEnabled(true);
            startBtn.setAlpha(1f);
            startBtn.setText(active ? "STOP RECORDING" : "START RECORDING");
        }
        if (active || saving) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF2B1218);
            g.setCornerRadius(dp(30));
            g.setStroke(dp(2), 0xFFFF1744);
            startBtn.setBackground(g);
        } else {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFFFF1744, 0xFFFF6D00});
            g.setCornerRadius(dp(30));
            startBtn.setBackground(g);
        }
        boolean editable = !(active || saving);
        card.setAlpha(editable ? 1f : 0.45f);
        setEnabledRecursive(card, editable);
    }

    private void onStartClicked() {
        int st = RecorderService.state;
        if (st == RecorderService.STATE_COUNTDOWN || st == RecorderService.STATE_RECORDING) {
            startService(new Intent(this, RecorderService.class).setAction(RecorderService.ACTION_STOP));
            return;
        }
        if (st == RecorderService.STATE_SAVING) return;

        // Ask once for the optional on-screen countdown permission
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)
                && !prefs.getBoolean("overlay_asked", false)) {
            prefs.edit().putBoolean("overlay_asked", true).apply();
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Show countdown on screen?")
                    .setMessage("Allow \"Display over other apps\" to see the 4-3-2-1 countdown on your screen "
                            + "before recording starts. Without it, the countdown is shown only in the notification.")
                    .setCancelable(false)
                    .setPositiveButton("Allow", (dlg, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                    })
                    .setNegativeButton("Skip", (dlg, which) -> beginStartFlow())
                    .show();
            return;
        }
        beginStartFlow();
    }

    private void beginStartFlow() {
        saveSettings();
        audioDenied = false;
        List<String> need = new ArrayList<>();
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (Build.VERSION.SDK_INT < 29 && !has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (need.isEmpty()) {
            startCapture();
        } else {
            requestPermissions(need.toArray(new String[0]), REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERM) return;
        if (Build.VERSION.SDK_INT < 29 && !has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, "Storage permission is required to save videos", Toast.LENGTH_LONG).show();
            return;
        }
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            audioDenied = true;
            Toast.makeText(this, "Audio permission denied. Recording without audio.", Toast.LENGTH_LONG).show();
        }
        startCapture();
    }

    private void startCapture() {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Screen capture is not available on this device", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent i = new Intent(this, RecorderService.class);
            i.setAction(RecorderService.ACTION_START);
            i.putExtra("code", resultCode);
            i.putExtra("data", data);
            i.putExtra("res", ddRes.value());
            i.putExtra("fps", ddFps.value());
            i.putExtra("mbps", ddBit.value());
            i.putExtra("audio", audioDenied ? 0 : audioMode());
            i.putExtra("ori", segOri.sel);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            moveTaskToBack(true);
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
