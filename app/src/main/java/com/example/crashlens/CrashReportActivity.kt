package com.example.crashlens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File

class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_PATH = "report_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val path = intent.getStringExtra(EXTRA_PATH)
        val text = if (path == null) {
            "Report ka path nahi mila."
        } else {
            try {
                File(path).readText()
            } catch (e: Exception) {
                "Report read nahi ho paayi: ${e.message}"
            }
        }

        findViewById<TextView>(R.id.reportText).text = text

        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash_report", text))
            Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(intent, "Report share karein"))
        }
    }
}
