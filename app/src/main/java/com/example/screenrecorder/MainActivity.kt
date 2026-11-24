package com.example.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.screenrecorder.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val timerHandler = Handler(Looper.getMainLooper())
    private var startTimeMs: Long = 0L

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (ScreenRecordService.isRecording) {
                val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMs) / 1000L
                val minutes = elapsedSec / 60
                val seconds = elapsedSec % 60
                binding.tvTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                timerHandler.postDelayed(this, 500L)
            }
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenRecordService.ACTION_STATE_CHANGED) {
                val recording = intent.getBooleanExtra(ScreenRecordService.EXTRA_IS_RECORDING, false)
                val savedPath = intent.getStringExtra(ScreenRecordService.EXTRA_SAVED_MESSAGE)
                updateUiState(recording)
                if (!recording && !savedPath.isNullOrBlank()) {
                    binding.tvStatusDetail.text = savedPath
                    Toast.makeText(this@MainActivity, savedPath, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)

        if (binding.switchAudio.isChecked && !recordAudioGranted) {
            binding.switchAudio.isChecked = false
            Toast.makeText(this, "Microphone permission denied. Recording video only.", Toast.LENGTH_SHORT).show()
        }
        launchScreenCapturePrompt()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnRecordToggle.setOnClickListener {
            if (ScreenRecordService.isRecording) {
                stopRecordingService()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.btnOpenGallery.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Saved in Movies/ScreenRecorder", Toast.LENGTH_SHORT).show()
            }
        }

        updateUiState(ScreenRecordService.isRecording)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ScreenRecordService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
        updateUiState(ScreenRecordService.isRecording)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(stateReceiver)
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (binding.switchAudio.isChecked) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            launchScreenCapturePrompt()
        }
    }

    private fun launchScreenCapturePrompt() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        val fps = if (binding.radio60Fps.isChecked) 60 else 30
        val recordAudio = binding.switchAudio.isChecked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordService.EXTRA_RECORD_AUDIO, recordAudio)
            putExtra(ScreenRecordService.EXTRA_FPS, fps)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        startTimeMs = SystemClock.elapsedRealtime()
        updateUiState(true)
    }

    private fun stopRecordingService() {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        startService(stopIntent)
    }

    private fun updateUiState(recording: Boolean) {
        if (recording) {
            binding.tvStatus.text = "Recording in Progress..."
            binding.tvStatusDetail.text = "Swipe down notification bar anytime to Stop"
            binding.btnRecordToggle.text = "Stop Recording"
            binding.btnRecordToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_red))
            binding.switchAudio.isEnabled = false
            binding.radio30Fps.isEnabled = false
            binding.radio60Fps.isEnabled = false

            if (startTimeMs == 0L) {
                startTimeMs = ScreenRecordService.recordingStartElapsedMs.takeIf { it > 0L }
                    ?: SystemClock.elapsedRealtime()
            }
            timerHandler.removeCallbacks(timerRunnable)
            timerHandler.post(timerRunnable)
        } else {
            binding.tvStatus.text = "Ready to Record"
            binding.btnRecordToggle.text = "Start Recording"
            binding.btnRecordToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue))
            binding.switchAudio.isEnabled = true
            binding.radio30Fps.isEnabled = true
            binding.radio60Fps.isEnabled = true

            timerHandler.removeCallbacks(timerRunnable)
            startTimeMs = 0L
            binding.tvTimer.text = "00:00"
        }
    }
}
