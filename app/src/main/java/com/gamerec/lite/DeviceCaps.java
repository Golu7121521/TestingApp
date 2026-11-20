package com.gamerec.lite;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Reads what this phone can actually do (screen size, refresh rate, H.264 encoder limits)
final class DeviceCaps {

    private final int nativeShort;
    private final double ratio;
    private float maxRefresh = 60f;
    private MediaCodecInfo.VideoCapabilities vc;

    DeviceCaps(Context ctx) {
        int ns = 1080;
        double ra = 2.0;
        try {
            DisplayMetrics dm = new DisplayMetrics();
            Display d = ((DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE))
                    .getDisplay(Display.DEFAULT_DISPLAY);
            d.getRealMetrics(dm);
            int mn = Math.min(dm.widthPixels, dm.heightPixels);
            if (mn > 0) {
                ns = mn;
                ra = (double) Math.max(dm.widthPixels, dm.heightPixels) / mn;
            }
            maxRefresh = d.getRefreshRate();
            if (Build.VERSION.SDK_INT >= 23) {
                for (Display.Mode m : d.getSupportedModes()) {
                    maxRefresh = Math.max(maxRefresh, m.getRefreshRate());
                }
            }
        } catch (Exception ignored) { }
        nativeShort = ns;
        ratio = ra;
        findEncoder();
    }

    private void findEncoder() {
        try {
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            MediaCodecInfo.VideoCapabilities fallback = null;
            for (MediaCodecInfo info : infos) {
                if (!info.isEncoder()) continue;
                for (String t : info.getSupportedTypes()) {
                    if (!t.equalsIgnoreCase("video/avc")) continue;
                    MediaCodecInfo.VideoCapabilities c;
                    try {
                        c = info.getCapabilitiesForType(t).getVideoCapabilities();
                    } catch (Exception e) {
                        continue;
                    }
                    if (c == null) continue;
                    String n = info.getName().toLowerCase(Locale.US);
                    boolean soft = n.startsWith("omx.google.") || n.startsWith("c2.android.");
                    if (!soft) {
                        vc = c;
                        return;
                    }
                    if (fallback == null) fallback = c;
                }
            }
            vc = fallback;
        } catch (Exception ignored) { }
    }

    // {long side, short side} of the recording for a given short-side resolution
    private int[] dims(int res) {
        int target = Math.min(res, nativeShort);
        int s = Recorder.align16(target);
        int l = Recorder.align16((int) Math.round(target * ratio));
        return new int[]{l, s};
    }

    int[] resolutions(int[] all) {
        List<Integer> out = new ArrayList<>();
        for (int c : all) {
            if (c > nativeShort + 8) continue;
            if (c > 480 && vc != null) {
                int[] d = dims(c);
                try {
                    if (!vc.isSizeSupported(d[0], d[1])) continue;
                } catch (Exception ignored) { }
            }
            out.add(c);
        }
        if (out.isEmpty()) out.add(all[0]);
        return toArray(out);
    }

    int[] fps(int[] all, int res) {
        float cap = maxRefresh + 1f;
        if (vc != null) {
            int[] d = dims(res);
            try {
                Range<Double> r = vc.getSupportedFrameRatesFor(d[0], d[1]);
                cap = Math.min(cap, (float) (r.getUpper() + 0.5));
            } catch (Exception ignored) { }
        }
        List<Integer> out = new ArrayList<>();
        for (int c : all) {
            if (c <= cap) out.add(c);
        }
        if (out.isEmpty()) out.add(all[0]);
        return toArray(out);
    }

    int[] bitrates(int[] allMbps) {
        long lo = 0;
        long hi = Long.MAX_VALUE;
        if (vc != null) {
            try {
                Range<Integer> r = vc.getBitrateRange();
                lo = r.getLower();
                hi = r.getUpper();
            } catch (Exception ignored) { }
        }
        List<Integer> out = new ArrayList<>();
        for (int c : allMbps) {
            long bps = c * 1000000L;
            if (bps >= lo && bps <= hi) out.add(c);
        }
        if (out.isEmpty()) {
            for (int c : allMbps) {
                if (c <= 20) out.add(c);
            }
        }
        return toArray(out);
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }
}
