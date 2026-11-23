package com.example.crashlens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCrash).setOnClickListener {
            crashNow()
        }

        findViewById<Button>(R.id.btnLastReport).setOnClickListener {
            val latest = CrashReporter.listReports(this).firstOrNull()
            if (latest == null) {
                Toast.makeText(this, "Koi crash report nahi mili", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(this, CrashReportActivity::class.java)
                        .putExtra(CrashReportActivity.EXTRA_PATH, latest.absolutePath)
                )
            }
        }
    }

    /** Demo crash: NullPointerException aayega, report save hogi. */
    private fun crashNow() {
        val name: String? = null
        val length = name!!.length
        findViewById<TextView>(R.id.txtInfo).text = "Length: $length"
    }
}
