package com.gamerec.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqPerms = 10
    private val reqProjection = 11

    private val resValues = intArrayOf(480, 720, 1080)
    private val fpsValues = intArrayOf(24, 30, 45, 60)
    private val mbpsValues = intArrayOf(6, 8, 12, 16, 20, 30)

    private lateinit var prefs: SharedPreferences
    private lateinit var spRes: Spinner
    private lateinit var spFps: Spinner
    private lateinit var spBitrate: Spinner
    private lateinit var spCodec: Spinner
    private lateinit var spOrient: Spinner
    private lateinit var swAudio: Switch
    private lateinit var btnStart: Button
    private lateinit var tvStatus: TextView

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        spRes = findViewById(R.id.spRes)
        spFps = findViewById(R.id.spFps)
        spBitrate = findViewById(R.id.spBitrate)
        spCodec = findViewById(R.id.spCodec)
        spOrient = findViewById(R.id.spOrient)
        swAudio = findViewById(R.id.swAudio)
        btnStart = findViewById(R.id.btnStart)
        tvStatus = findViewById(R.id.tvStatus)

        setupSpinner(spRes, R.array.res_labels, "res", 2)       // 1080p
        setupSpinner(spFps, R.array.fps_labels, "fps", 3)       // 60 FPS
        setupSpinner(spBitrate, R.array.bitrate_labels, "mbps", 4) // 20 Mbps
        setupSpinner(spCodec, R.array.codec_labels, "codec", 0)  // H.264
        setupSpinner(spOrient, R.array.orient_labels, "orient", 0) // Landscape
        swAudio.isChecked = prefs.getBoolean("audio", true)

        btnStart.setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.btnOpen).setOnClickListener { openGallery() }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun setupSpinner(sp: Spinner, arrayRes: Int, key: String, def: Int) {
        val adapter = ArrayAdapter.createFromResource(this, arrayRes, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        sp.setSelection(prefs.getInt(key, def).coerceIn(0, adapter.count - 1))
    }

    private fun saveSettings() {
        prefs.edit()
            .putInt("res", spRes.selectedItemPosition)
            .putInt("fps", spFps.selectedItemPosition)
            .putInt("mbps", spBitrate.selectedItemPosition)
            .putInt("codec", spCodec.selectedItemPosition)
            .putInt("orient", spOrient.selectedItemPosition)
            .putBoolean("audio", swAudio.isChecked)
            .apply()
    }

    private fun updateUi() {
        if (RecorderService.isRecording) {
            btnStart.text = "Recording roko"
            tvStatus.text = "Recording chal rahi hai..."
        } else {
            btnStart.text = "Recording shuru karo"
            tvStatus.text = ""
        }
    }

    private fun onStartClicked() {
        if (RecorderService.isRecording) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
            tvStatus.text = "Save ho raha hai..."
            ui.postDelayed({ updateUi() }, 2000)
            return
        }
        saveSettings()

        val need = ArrayList<String>()
        if (swAudio.isChecked &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), reqPerms)
        } else {
            requestProjection()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != reqPerms) return
        val idx = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        if (idx >= 0 && grantResults[idx] != PackageManager.PERMISSION_GRANTED) {
            swAudio.isChecked = false
            Toast.makeText(this, "Audio permission nahi mili, recording bina audio ke hogi", Toast.LENGTH_LONG).show()
        }
        requestProjection()
    }

    private fun requestProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), reqProjection)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqProjection) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture permission cancel hui", Toast.LENGTH_SHORT).show()
            return
        }
        val svc = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_DATA, data)
            putExtra(RecorderService.EXTRA_SHORT, resValues[spRes.selectedItemPosition])
            putExtra(RecorderService.EXTRA_FPS, fpsValues[spFps.selectedItemPosition])
            putExtra(RecorderService.EXTRA_MBPS, mbpsValues[spBitrate.selectedItemPosition])
            putExtra(RecorderService.EXTRA_HEVC, spCodec.selectedItemPosition == 1)
            putExtra(RecorderService.EXTRA_ORIENT, spOrient.selectedItemPosition)
            putExtra(RecorderService.EXTRA_AUDIO, swAudio.isChecked)
        }
        startForegroundService(svc)
        // App peeche bhej do taaki user seedha game khol sake
        moveTaskToBack(true)
        ui.postDelayed({ updateUi() }, 1200)
    }

    private fun openGallery() {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Gallery app mein Movies/GameRecorder dekho", Toast.LENGTH_LONG).show()
        }
    }
}
