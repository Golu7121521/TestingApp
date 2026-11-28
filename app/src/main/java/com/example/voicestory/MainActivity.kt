package com.example.voicestory

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHUNK_PREFIX = "chunk_"
        private const val PREVIEW_ID = "preview"
        private const val WAV_HEADER_SIZE = 44
        private const val MAX_CHUNK_LENGTH = 3000
    }

    private lateinit var storyInput: EditText
    private lateinit var voiceSpinner: Spinner
    private lateinit var pitchSeek: SeekBar
    private lateinit var speedSeek: SeekBar
    private lateinit var previewButton: Button
    private lateinit var createButton: Button
    private lateinit var shareButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var tts: TextToSpeech
    private var engineReady = false
    private var voices: List<Voice> = emptyList()

    // State for the current audio-creation job
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private val partFiles = mutableListOf<File>()
    private lateinit var tempDir: File
    private lateinit var outputFile: File
    private var lastOutput: File? = null
    private var isBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storyInput = findViewById(R.id.storyInput)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        pitchSeek = findViewById(R.id.pitchSeek)
        speedSeek = findViewById(R.id.speedSeek)
        previewButton = findViewById(R.id.previewButton)
        createButton = findViewById(R.id.createButton)
        shareButton = findViewById(R.id.shareButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        setupListeners()
        statusText.text = getString(R.string.status_starting)
        tts = TextToSpeech(this, this)
    }

    private fun setupListeners() {
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) = applySettings()
        }
        pitchSeek.setOnSeekBarChangeListener(seekListener)
        speedSeek.setOnSeekBarChangeListener(seekListener)

        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applySettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        previewButton.setOnClickListener { previewVoice() }
        createButton.setOnClickListener { createAudioFile() }
        shareButton.setOnClickListener { shareAudio() }
    }

    // ---------- Text-to-speech engine setup ----------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = getString(R.string.status_no_engine)
            return
        }

        tts.setLanguage(Locale.US)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread { onUtteranceDone(utteranceId.orEmpty()) }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread { onUtteranceError() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread { onUtteranceError() }
            }
        })

        // Use only voices that work offline, prefer English voices, sort by name
        val all = (tts.voices ?: emptySet()).filter { !it.isNetworkConnectionRequired }
        val english = all.filter { it.locale.language == "en" }
        voices = english.ifEmpty { all }.sortedBy { it.name }

        val labels = if (voices.isEmpty()) {
            listOf(getString(R.string.default_voice))
        } else {
            voices.map { "${it.name} (${it.locale})" }
        }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        engineReady = true
        statusText.text = getString(R.string.status_ready)
        applySettings()
    }

    private fun applySettings() {
        if (!engineReady) return
        tts.setPitch(pitchValue())
        tts.setSpeechRate(speedValue())
        val position = voiceSpinner.selectedItemPosition
        if (voices.isNotEmpty() && position in voices.indices) {
            tts.setVoice(voices[position])
        }
    }

    // Pitch and speed go from 0.5 (low) to 1.5 (high). Default values give a deeper voice.
    private fun pitchValue(): Float = 0.5f + pitchSeek.progress / 100f
    private fun speedValue(): Float = 0.5f + speedSeek.progress / 100f

    // ---------- Preview ----------

    private fun previewVoice() {
        if (!engineReady) {
            statusText.text = getString(R.string.status_no_engine)
            return
        }
        applySettings()
        tts.stop()
        tts.speak(getString(R.string.preview_text), TextToSpeech.QUEUE_FLUSH, null, PREVIEW_ID)
    }

    // ---------- Create audio file ----------

    private fun createAudioFile() {
        if (isBusy) return
        if (!engineReady) {
            statusText.text = getString(R.string.status_no_engine)
            return
        }
        val text = storyInput.text.toString().trim()
        if (text.isEmpty()) {
            statusText.text = getString(R.string.status_empty)
            return
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folder = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        folder.mkdirs()

        outputFile = File(folder, "story_$stamp.wav")
        tempDir = File(cacheDir, "tts_parts_$stamp").apply { mkdirs() }
        chunks = splitIntoChunks(text, MAX_CHUNK_LENGTH)
        chunkIndex = 0
        partFiles.clear()
        lastOutput = null
        shareButton.isEnabled = false

        setBusy(true)
        applySettings()
        synthesizeNextChunk()
    }

    private fun synthesizeNextChunk() {
        if (chunkIndex >= chunks.size) {
            finishJob()
            return
        }
        statusText.text = getString(R.string.status_creating, chunkIndex + 1, chunks.size)

        val part = File(tempDir, "part_$chunkIndex.wav")
        val result = tts.synthesizeToFile(chunks[chunkIndex], Bundle(), part, "$CHUNK_PREFIX$chunkIndex")
        if (result != TextToSpeech.SUCCESS) {
            setBusy(false)
            statusText.text = getString(R.string.status_error)
        }
    }

    private fun onUtteranceDone(utteranceId: String) {
        // Ignore preview playback and stale callbacks
        if (!isBusy || utteranceId != "$CHUNK_PREFIX$chunkIndex") return
        partFiles.add(File(tempDir, "part_$chunkIndex.wav"))
        chunkIndex++
        synthesizeNextChunk()
    }

    private fun onUtteranceError() {
        if (isBusy) {
            setBusy(false)
            statusText.text = getString(R.string.status_error)
        }
    }

    private fun finishJob() {
        try {
            mergeWavParts(partFiles, outputFile)
            tempDir.deleteRecursively()
            lastOutput = outputFile
            statusText.text = getString(R.string.status_saved, outputFile.absolutePath)
            shareButton.isEnabled = true
        } catch (e: Exception) {
            statusText.text = getString(R.string.status_error)
        } finally {
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        isBusy = busy
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        createButton.isEnabled = !busy
        previewButton.isEnabled = !busy
    }

    // ---------- Share ----------

    private fun shareAudio() {
        val file = lastOutput ?: run {
            Toast.makeText(this, R.string.status_empty_share, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    // ---------- Text splitting ----------

    // Android's TTS has a length limit per request, so long stories are split into
    // chunks at sentence ends and joined again into a single WAV file.
    private fun splitIntoChunks(text: String, maxLength: Int): List<String> {
        val pieces = text.split(Regex("(?<=[.!?।\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { if (it.length > maxLength) it.chunked(maxLength) else listOf(it) }

        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (piece in pieces) {
            if (current.isNotEmpty() && current.length + 1 + piece.length > maxLength) {
                result.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    // ---------- WAV joining ----------

    private data class WavFormat(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataLength: Long
    )

    private fun readWavFormat(file: File): WavFormat {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(12) // skip "RIFF", file size and "WAVE"
            var channels = 0
            var sampleRate = 0
            var bits = 0
            val header = ByteArray(8)

            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(header)
                val id = String(header, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val bodyStart = raf.filePointer

                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(16)
                        raf.readFully(fmt)
                        val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        channels = buf.getShort(2).toInt()
                        sampleRate = buf.getInt(4)
                        bits = buf.getShort(14).toInt()
                    }
                    "data" -> return WavFormat(
                        channels, sampleRate, bits, bodyStart,
                        minOf(size, raf.length() - bodyStart)
                    )
                }
                // Move to next chunk (chunks are word-aligned)
                raf.seek(bodyStart + size + (size and 1L))
            }
        }
        throw IllegalStateException("Invalid WAV file: ${file.name}")
    }

    private fun mergeWavParts(parts: List<File>, output: File) {
        if (parts.isEmpty()) throw IllegalStateException("No audio parts to merge")
        val format = readWavFormat(parts.first())
        var totalData = 0L

        output.parentFile?.mkdirs()
        RandomAccessFile(output, "rw").use { out ->
            out.setLength(0)
            out.write(ByteArray(WAV_HEADER_SIZE)) // placeholder, rewritten at the end
            val buffer = ByteArray(64 * 1024)

            for (part in parts) {
                val info = readWavFormat(part)
                RandomAccessFile(part, "r").use { input ->
                    input.seek(info.dataOffset)
                    var remaining = info.dataLength
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                        totalData += read
                    }
                }
            }

            out.seek(0)
            out.write(wavHeader(format, totalData))
        }
    }

    private fun wavHeader(format: WavFormat, dataLength: Long): ByteArray {
        val byteRate = format.sampleRate * format.channels * format.bitsPerSample / 8
        val blockAlign = format.channels * format.bitsPerSample / 8

        return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataLength).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(format.channels.toShort())
            putInt(format.sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(format.bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLength.toInt())
        }.array()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
