package com.example.gameboost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

/** RAM info aur background apps ko clear karne ka shared logic. */
final class BoostUtil {

    static final long MB = 1024L * 1024L;

    private BoostUtil() {
    }

    private static ActivityManager am(Context c) {
        return (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
    }

    static long availRam(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am(c).getMemoryInfo(mi);
        return mi.availMem;
    }

    static long totalRam(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am(c).getMemoryInfo(mi);
        return mi.totalMem;
    }

    /** Game aur Game Booster ko chhod kar baaki background apps ko kill karta hai. */
    static int killBackgroundApps(Context c, String keepPkg) {
        ActivityManager activityManager = am(c);
        PackageManager pm = c.getPackageManager();
        String self = c.getPackageName();
        int count = 0;

        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            String p = ai.packageName;
            if (p.equals(keepPkg) || p.equals(self)) continue;
            if (p.equals("android") || p.startsWith("com.android.systemui")) continue;
            activityManager.killBackgroundProcesses(p);
            count++;
        }
        return count;
    }
}
