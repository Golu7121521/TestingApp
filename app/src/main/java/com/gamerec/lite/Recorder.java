package com.gamerec.lite;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Display;

import com.pedro.common.ConnectChecker;
import com.pedro.library.generic.GenericStream;
import com.pedro.library.util.sources.audio.AudioSource;
import com.pedro.library.util.sources.audio.InternalAudioSource;
import com.pedro.library.util.sources.audio.MicrophoneSource;
import com.pedro.library.util.sources.audio.NoAudioSource;
import com.pedro.library.util.sources.video.ScreenSource;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screen recorder built on top of RootEncoder (com.github.pedroSG94.RootEncoder:library).
 *
 * The library owns the MediaCodec encoders, the muxer, A/V sync and pause/resume.
 * This class only prepares the output file, the capture size and the audio source.
 * The public surface (constructor, start, pause, resume, stop, saved) is unchanged,
 * so RecorderService does not need to know which engine is used.
 */
public class Recorder {

    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BITRATE = 128 * 1000;
    private static final int I_FRAME_INTERVAL = 2;

    private final Context ctx;
    private final MediaProjection mp;
    private final Runnable onProjectionStopped;
    private final int fps, bitrate, audioMode;
    private int w, h;

    private GenericStream stream;
    private ParcelFileDescriptor pfd;
    private Uri uri;
    private File outFile;
    private MediaProjection.Callback cb;
    private boolean recording = false;
    private boolean stopped = false;

    public boolean saved = false;

    // Recording only: nothing is streamed, so every callback is a no-op.
    private static final class NoopChecker implements ConnectChecker {
        public void onConnectionStarted(String url) { }
        public void onConnectionSuccess() { }
        public void onConnectionFailed(String reason) { }
        public void onNewBitrate(long bitrate) { }
        public void onDisconnect() { }
        public void onAuthError() { }
        public void onAuthSuccess() { }
    }

    public Recorder(Context ctx, MediaProjection mp, int shortSide, int fps, int bitrate,
                    int audioMode, int ori, Runnable onProjectionStopped) {
        this.ctx = ctx;
        this.mp = mp;
        this.fps = fps;
        this.bitrate = bitrate;
        this.audioMode = audioMode;
        this.onProjectionStopped = onProjectionStopped;

        DisplayMetrics dm = new DisplayMetrics();
        Display d = ((DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE))
                .getDisplay(Display.DEFAULT_DISPLAY);
        d.getRealMetrics(dm);
        int nativeShort = Math.min(dm.widthPixels, dm.heightPixels);
        int target = Math.min(shortSide, nativeShort);
        double ratio = (double) Math.max(dm.widthPixels, dm.heightPixels) / nativeShort;
        int s = align16(target);
        int l = align16((int) Math.round(target * ratio));
        boolean landscape = ori == 1 || (ori == 0 && dm.widthPixels > dm.heightPixels);
        this.w = landscape ? l : s;
        this.h = landscape ? s : l;
    }

    static int align16(int v) {
        return Math.max(16, ((v + 8) / 16) * 16);
    }

    public void start() throws Exception {
        openOutput();

        cb = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (onProjectionStopped != null) onProjectionStopped.run();
            }
        };
        mp.registerCallback(cb, new Handler(Looper.getMainLooper()));

        // audioMode: 0 none, 1 device, 2 device + mic, 3 mic only.
        // The library takes one audio source at a time: "device + mic" uses device audio
        // and falls back to the microphone when the game blocks playback capture.
        boolean ready = false;
        if (audioMode == 1 || audioMode == 2) {
            ready = tryPrepare(new InternalAudioSource(mp));
        }
        if (!ready && (audioMode == 2 || audioMode == 3)) {
            ready = tryPrepare(new MicrophoneSource());
        }
        if (!ready) {
            ready = tryPrepare(new NoAudioSource());
        }
        if (!ready) throw new Exception("Video encoder error");

        if (pfd != null) {
            stream.startRecord(pfd.getFileDescriptor(), status -> { });
        } else {
            stream.startRecord(outFile.getAbsolutePath(), status -> { });
        }
        recording = true;
    }

    private boolean tryPrepare(AudioSource audio) {
        releaseStream();
        try {
            stream = new GenericStream(ctx, new NoopChecker(), new ScreenSource(ctx, mp), audio);
            return stream.prepareVideo(w, h, bitrate, fps, I_FRAME_INTERVAL, 0)
                    && stream.prepareAudio(SAMPLE_RATE, true, AUDIO_BITRATE);
        } catch (Throwable t) {
            return false;
        }
    }

    private void releaseStream() {
        if (stream == null) return;
        try { stream.release(); } catch (Throwable ignored) { }
        stream = null;
    }

    private void openOutput() throws Exception {
        String name = "Rec_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GameRec");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Could not create the video file");
            pfd = cr.openFileDescriptor(uri, "rw");
            if (pfd == null) throw new Exception("Could not open the video file");
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GameRec");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create Movies/GameRec folder");
            outFile = new File(dir, name);
        }
    }

    public synchronized void pause() {
        if (!recording || stream == null) return;
        try { stream.pauseRecord(); } catch (Throwable ignored) { }
    }

    public synchronized void resume() {
        if (!recording || stream == null) return;
        try { stream.resumeRecord(); } catch (Throwable ignored) { }
    }

    public Uri stop() {
        if (stopped) return uri;
        stopped = true;
        try { if (cb != null) mp.unregisterCallback(cb); } catch (Exception ignored) { }
        if (stream != null && recording) {
            try { stream.stopRecord(); } catch (Throwable ignored) { }
        }
        releaseStream();
        try { mp.stop(); } catch (Exception ignored) { }

        boolean ok = false;
        try {
            if (pfd != null) ok = recording && pfd.getStatSize() > 0;
            else if (outFile != null) ok = recording && outFile.length() > 0;
        } catch (Exception ignored) { }
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }

        try {
            if (uri != null) {
                ContentResolver cr = ctx.getContentResolver();
                if (ok) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    cr.update(uri, v, null, null);
                } else {
                    cr.delete(uri, null, null);
                    uri = null;
                }
            } else if (outFile != null) {
                if (ok) {
                    MediaScannerConnection.scanFile(ctx, new String[]{outFile.getAbsolutePath()},
                            new String[]{"video/mp4"}, null);
                } else {
                    outFile.delete();
                }
            }
        } catch (Exception ignored) { }
        saved = ok;
        return uri;
    }
}
