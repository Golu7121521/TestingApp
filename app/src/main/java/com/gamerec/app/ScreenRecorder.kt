package com.gamerec.app

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Surface
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

data class RecConfig(
    val shortSide: Int,      // 480 / 720 / 1080
    val fps: Int,            // 24 / 30 / 45 / 60
    val mbps: Int,           // video bitrate in Mbps
    val hevc: Boolean,
    val orientation: Int,    // 0 = landscape, 1 = portrait, 2 = auto (current)
    val audio: Boolean       // device internal audio
)

/**
 * Pure hardware pipeline (FPS drop kam karne ke liye):
 *   MediaProjection -> VirtualDisplay -> encoder ka Surface (GPU se seedha, koi CPU copy nahi)
 *   Internal audio -> AudioPlaybackCapture -> AAC hardware encoder
 *   Dono -> MediaMuxer -> Movies/GameRecorder/*.mp4
 */
class ScreenRecorder(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val cfg: RecConfig
) {
    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
        private const val AUDIO_BITRATE = 192_000
    }

    private var videoEnc: MediaCodec? = null
    private var audioEnc: MediaCodec? = null
    private var audioRec: AudioRecord? = null
    private var display: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var sink: Sink? = null

    private var videoThread: Thread? = null
    private var audioThread: Thread? = null
    @Volatile private var stopping = false
    private var baseUs = 0L

    var infoText: String = ""
        private set

    // ---------------------------------------------------------------- start

    fun start() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(0).getRealMetrics(dm)

        val long = maxOf(dm.widthPixels, dm.heightPixels)
        val short = minOf(dm.widthPixels, dm.heightPixels)
        val landscape = when (cfg.orientation) {
            0 -> true
            1 -> false
            else -> dm.widthPixels >= dm.heightPixels
        }

        // Encoder jo chahiye woh dhundo; na mile to size/codec/fps girate hue fallback
        val choice = pickEncoder(long, short, landscape)
        val w = choice.w
        val h = choice.h

        // ---- audio pehle (agar fail ho to bina audio ke chalo)
        var withAudio = cfg.audio
        if (withAudio) {
            withAudio = try { setupAudio(); true } catch (e: Exception) {
                releaseAudio(); false
            }
        }

        // ---- video encoder
        val venc = MediaCodec.createByCodecName(choice.codecName)
        venc.configure(choice.format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = venc.createInputSurface()
        venc.start()
        videoEnc = venc

        // ---- output file (MediaStore: Movies/GameRecorder)
        val name = "GameRec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GameRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val u = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("File nahi ban payi")
        uri = u
        pfd = ctx.contentResolver.openFileDescriptor(u, "rw")
        muxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        sink = Sink(muxer!!, withAudio)

        baseUs = System.nanoTime() / 1000

        // ---- virtual display (ye frames encoder ko dena shuru karta hai)
        display = projection.createVirtualDisplay(
            "GameRec", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null
        )

        videoThread = Thread({ drainVideo() }, "rec-video").also { it.start() }
        if (withAudio) {
            audioRec!!.startRecording()
            audioThread = Thread({ audioLoop() }, "rec-audio").also { it.start() }
        }

        infoText = "${w}x$h @${choice.fps}fps " + (if (cfg.hevc && choice.hevc) "HEVC" else "H.264") +
            (if (withAudio) " + audio" else "")
    }

    // ---------------------------------------------------------------- encoder selection

    private class Choice(
        val codecName: String, val format: MediaFormat,
        val w: Int, val h: Int, val fps: Int, val hevc: Boolean
    )

    private fun even(v: Int) = v and 1.inv()

    private fun pickEncoder(long: Int, short: Int, landscape: Boolean): Choice {
        val target = cfg.shortSide
        val nativeLong = even((long.toLong() * target / short).toInt())
        val wideLong = even(target * 16 / 9)

        val sizes = LinkedHashSet<Pair<Int, Int>>() // (longSide, shortSide)
        sizes.add(nativeLong to target)       // screen jaisa aspect (letterbox nahi)
        sizes.add(wideLong to target)         // 16:9
        if (target > 720) sizes.add(even(720 * 16 / 9) to 720)

        val codecs = if (cfg.hevc) listOf(true, false) else listOf(false)
        val fpsList = listOf(cfg.fps, 30).distinct().filter { it <= cfg.fps }

        for (hevc in codecs) for (fps in fpsList) for ((l, s) in sizes) {
            val w = if (landscape) l else s
            val h = if (landscape) s else l
            val fmt = buildVideoFormat(hevc, w, h, fps)
            val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(fmt)
            if (name != null) return Choice(name, fmt, w, h, fps, hevc)
        }
        throw IllegalStateException("Is phone ka encoder ye setting support nahi karta. Resolution/FPS kam karke try karo.")
    }

    private fun buildVideoFormat(hevc: Boolean, w: Int, h: Int, fps: Int): MediaFormat {
        val mime = if (hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        val f = MediaFormat.createVideoFormat(mime, w, h)
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        f.setInteger(MediaFormat.KEY_BIT_RATE, cfg.mbps * 1_000_000)
        f.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        f.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        // Realtime priority + operating rate: encoder ko game ke saath dhima nahi padne dete
        f.setInteger(MediaFormat.KEY_PRIORITY, 0)
        f.setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
        // Static screen par bhi frames milte rahein (menu/loading screens)
        f.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 500_000L)
        return f
    }

    // ---------------------------------------------------------------- audio setup

    @Suppress("MissingPermission")
    private fun setupAudio() {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val af = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord.Builder()
            .setAudioFormat(af)
            .setBufferSizeInBytes(maxOf(minBuf * 4, 32768))
            .setAudioPlaybackCaptureConfig(capture)
            .build()
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("AudioRecord init fail")
        }
        audioRec = rec

        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        audioEnc = enc
    }

    private fun releaseAudio() {
        try { audioRec?.release() } catch (_: Exception) {}
        try { audioEnc?.stop() } catch (_: Exception) {}
        try { audioEnc?.release() } catch (_: Exception) {}
        audioRec = null
        audioEnc = null
    }

    // ---------------------------------------------------------------- threads

    private fun drainVideo() {
        val enc = videoEnc ?: return
        val info = MediaCodec.BufferInfo()
        var lastPts = 0L
        try {
            while (true) {
                val idx = enc.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> sink?.addTrack(true, enc.outputFormat)
                    idx >= 0 -> {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            val buf = enc.getOutputBuffer(idx)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            var pts = info.presentationTimeUs - baseUs
                            if (pts < lastPts) pts = lastPts
                            lastPts = pts
                            info.presentationTimeUs = pts
                            sink?.write(true, buf, info)
                        }
                        enc.releaseOutputBuffer(idx, false)
                        if (eos) break
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun audioLoop() {
        val rec = audioRec ?: return
        val enc = audioEnc ?: return
        val info = MediaCodec.BufferInfo()
        var totalFrames = 0L
        var firstNs = 0L
        var lastPts = 0L
        var inputDone = false
        var outputDone = false

        fun drain(): Boolean {
            while (true) {
                val idx = enc.dequeueOutputBuffer(info, 0)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> sink?.addTrack(false, enc.outputFormat)
                    idx >= 0 -> {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            val buf = enc.getOutputBuffer(idx)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            if (info.presentationTimeUs < lastPts) info.presentationTimeUs = lastPts
                            lastPts = info.presentationTimeUs
                            sink?.write(false, buf, info)
                        }
                        enc.releaseOutputBuffer(idx, false)
                        if (eos) return true
                    }
                    else -> return false
                }
            }
        }

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = enc.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val bb = enc.getInputBuffer(idx)!!
                        bb.clear()
                        if (stopping) {
                            enc.queueInputBuffer(
                                idx, 0, 0, frameToUs(firstNs, totalFrames),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val n = rec.read(bb, minOf(bb.remaining(), 4096))
                            if (n > 0) {
                                if (firstNs == 0L) firstNs = System.nanoTime()
                                enc.queueInputBuffer(idx, 0, n, frameToUs(firstNs, totalFrames), 0)
                                totalFrames += n / (2 * CHANNELS)
                            } else {
                                enc.queueInputBuffer(idx, 0, 0, 0, 0)
                            }
                        }
                    }
                }
                outputDone = drain()
            }
        } catch (_: Exception) {
        }
    }

    /** Audio PTS: sample counting se, taaki drift na ho (video ke same monotonic clock par). */
    private fun frameToUs(firstNs: Long, frames: Long): Long {
        if (firstNs == 0L) return 0L
        val startUs = firstNs / 1000 - baseUs
        return maxOf(0L, startUs) + frames * 1_000_000L / SAMPLE_RATE
    }

    // ---------------------------------------------------------------- stop

    fun stop() {
        if (stopping) return
        stopping = true
        try { display?.release() } catch (_: Exception) {}
        try { videoEnc?.signalEndOfInputStream() } catch (_: Exception) {}

        try { videoThread?.join(4000) } catch (_: Exception) {}
        try { audioThread?.join(4000) } catch (_: Exception) {}

        try { audioRec?.stop() } catch (_: Exception) {}
        try { audioRec?.release() } catch (_: Exception) {}
        try { audioEnc?.stop() } catch (_: Exception) {}
        try { audioEnc?.release() } catch (_: Exception) {}
        try { videoEnc?.stop() } catch (_: Exception) {}
        try { videoEnc?.release() } catch (_: Exception) {}
        try { inputSurface?.release() } catch (_: Exception) {}

        var ok = false
        try {
            if (sink?.started == true) {
                muxer?.stop()
                ok = true
            }
        } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}

        val u = uri
        if (u != null) {
            try {
                if (ok) {
                    val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    ctx.contentResolver.update(u, v, null, null)
                } else {
                    ctx.contentResolver.delete(u, null, null)
                }
            } catch (_: Exception) {}
        }
        savedOk = ok
    }

    @Volatile var savedOk = false
        private set

    // ---------------------------------------------------------------- muxer sink

    /**
     * Muxer tabhi start hota hai jab video (aur audio, agar on hai) dono ke tracks aa jaye.
     * Tab tak aaye hue samples copy karke queue mein rakhe jaate hain.
     */
    private class Sink(private val muxer: MediaMuxer, private val expectAudio: Boolean) {
        private class Pending(val video: Boolean, val data: ByteBuffer, val info: MediaCodec.BufferInfo)

        private var vTrack = -1
        private var aTrack = -1
        @Volatile var started = false
            private set
        private val pending = ArrayList<Pending>()

        @Synchronized
        fun addTrack(video: Boolean, format: MediaFormat) {
            if (started) return
            if (video) vTrack = muxer.addTrack(format) else aTrack = muxer.addTrack(format)
            if (vTrack >= 0 && (!expectAudio || aTrack >= 0)) {
                muxer.start()
                started = true
                for (p in pending) {
                    muxer.writeSampleData(if (p.video) vTrack else aTrack, p.data, p.info)
                }
                pending.clear()
            }
        }

        @Synchronized
        fun write(video: Boolean, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (started) {
                muxer.writeSampleData(if (video) vTrack else aTrack, buf, info)
            } else {
                val copy = ByteBuffer.allocate(info.size)
                copy.put(buf)
                copy.flip()
                val i = MediaCodec.BufferInfo()
                i.set(0, info.size, info.presentationTimeUs, info.flags)
                pending.add(Pending(video, copy, i))
            }
        }
    }
}
