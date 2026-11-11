package com.example.gameboost;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "gameboost_prefs";
    private static final String KEY_GAMES = "games";
    private static final String KEY_SELECTED = "selected";
    private static final long MB = 1024L * 1024L;

    private SharedPreferences prefs;
    private PackageManager pm;
    private final ArrayList<String> gamePkgs = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView ramText;
    private TextView hintText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        pm = getPackageManager();

        ramText = findViewById(R.id.ram_text);
        hintText = findViewById(R.id.hint_text);
        Button addBtn = findViewById(R.id.add_btn);
        Button boostBtn = findViewById(R.id.boost_btn);
        ListView list = findViewById(R.id.game_list);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);

        addBtn.setOnClickListener(v -> showAddGameDialog());
        boostBtn.setOnClickListener(v -> boostAndLaunch());

        list.setOnItemClickListener((parent, view, pos, id) -> {
            String pkg = gamePkgs.get(pos);
            prefs.edit().putString(KEY_SELECTED, pkg).apply();
            refreshList();
            Toast.makeText(this, "Selected: " + labelOf(pkg), Toast.LENGTH_SHORT).show();
        });

        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            confirmRemove(gamePkgs.get(pos));
            return true;
        });

        loadGames();
        refreshList();
        updateRamText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRamText();
    }

    // ---------- Game list storage ----------

    private void loadGames() {
        gamePkgs.clear();
        String raw = prefs.getString(KEY_GAMES, "");
        for (String p : raw.split("\n")) {
            if (!p.trim().isEmpty()) gamePkgs.add(p.trim());
        }
    }

    private void saveGames() {
        StringBuilder sb = new StringBuilder();
        for (String p : gamePkgs) sb.append(p).append("\n");
        prefs.edit().putString(KEY_GAMES, sb.toString()).apply();
    }

    private void refreshList() {
        adapter.clear();
        String sel = prefs.getString(KEY_SELECTED, null);
        for (String p : gamePkgs) {
            String label = labelOf(p);
            if (p.equals(sel)) label = "▶ " + label + "  (selected)";
            adapter.add(label);
        }
        adapter.notifyDataSetChanged();
        hintText.setText(gamePkgs.isEmpty()
                ? "Pehle game add karo, phir use select karke BOOST & PLAY dabao."
                : "Game tap karke select karo • long press karke hatao");
    }

    private String labelOf(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // ---------- Add / remove games ----------

    private void showAddGameDialog() {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);

        Set<String> seen = new HashSet<>();
        List<String[]> entries = new ArrayList<>(); // [label, packageName]
        for (ResolveInfo ri : infos) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || gamePkgs.contains(p) || seen.contains(p)) continue;
            seen.add(p);
            entries.add(new String[]{ri.loadLabel(pm).toString(), p});
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, "Add karne ke liye koi app nahi mili", Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.sort(entries, (a, b) -> a[0].compareToIgnoreCase(b[0]));

        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i)[0];

        new AlertDialog.Builder(this)
                .setTitle("Game chuno")
                .setItems(names, (dialog, which) -> addGame(entries.get(which)[1]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addGame(String pkg) {
        if (!gamePkgs.contains(pkg)) gamePkgs.add(pkg);
        if (prefs.getString(KEY_SELECTED, null) == null) {
            prefs.edit().putString(KEY_SELECTED, pkg).apply();
        }
        saveGames();
        refreshList();
        Toast.makeText(this, labelOf(pkg) + " add ho gaya", Toast.LENGTH_SHORT).show();
    }

    private void confirmRemove(String pkg) {
        new AlertDialog.Builder(this)
                .setMessage(labelOf(pkg) + " list se hatana hai?")
                .setPositiveButton("Hatao", (dialog, which) -> {
                    gamePkgs.remove(pkg);
                    if (pkg.equals(prefs.getString(KEY_SELECTED, null))) {
                        prefs.edit().remove(KEY_SELECTED).apply();
                    }
                    saveGames();
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Booster ----------

    private void boostAndLaunch() {
        String game = prefs.getString(KEY_SELECTED, null);
        if (game == null) {
            Toast.makeText(this, "Pehle koi game add karke select karo", Toast.LENGTH_SHORT).show();
            return;
        }

        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final long before = availRam(am);
        final int killed = killBackgroundApps(am, game);

        Intent launch = pm.getLaunchIntentForPackage(game);
        if (launch == null) {
            Toast.makeText(this, "Ye game launch nahi ho pa raha", Toast.LENGTH_SHORT).show();
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }

        // Memory settle hone ke baad result dikhao
        ramText.postDelayed(() -> {
            long after = availRam(am);
            long freed = Math.max(0, after - before) / MB;
            Toast.makeText(this,
                    "Boost done: " + killed + " apps band, ~" + freed + " MB free hua",
                    Toast.LENGTH_LONG).show();
            updateRamText();
        }, 1500);
    }

    /** Game aur Game Booster ko chhod kar baaki sab background apps ko kill karta hai. */
    private int killBackgroundApps(ActivityManager am, String keepPkg) {
        int count = 0;
        String self = getPackageName();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            String p = ai.packageName;
            if (p.equals(keepPkg) || p.equals(self)) continue;
            if (p.equals("android") || p.startsWith("com.android.systemui")) continue;
            am.killBackgroundProcesses(p);
            count++;
        }
        return count;
    }

    private long availRam(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private void updateRamText() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        ramText.setText("RAM free: " + (mi.availMem / MB) + " MB / " + (mi.totalMem / MB) + " MB");
    }
}
