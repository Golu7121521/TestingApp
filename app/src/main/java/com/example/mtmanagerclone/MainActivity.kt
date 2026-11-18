package com.example.mtmanagerclone

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell

class MainActivity : AppCompatActivity() {

    init {
        // Setup libsu for root access
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkRootAccess()
    }

    private fun checkRootAccess() {
        if (Shell.getShell().isRoot) {
            Toast.makeText(this, "Root Access Granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No Root Access", Toast.LENGTH_SHORT).show()
        }
    }
}
