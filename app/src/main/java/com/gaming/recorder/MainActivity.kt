package com.gaming.recorder

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gaming.recorder.databinding.ActivityMain

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMain
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenRecorderService::class.java).apply {
                putExtra("code", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(intent)
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            Toast.makeText(this, "Recording Started (1080p 60FPS)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen Recording Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnStart.setOnClickListener {
            val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(permissionIntent)
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, ScreenRecorderService::class.java).apply {
                action = ScreenRecorderService.ACTION_STOP
            }
            startService(intent)
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            Toast.makeText(this, "Recording Saved to Movies Folder", Toast.LENGTH_LONG).show()
        }
    }
}
