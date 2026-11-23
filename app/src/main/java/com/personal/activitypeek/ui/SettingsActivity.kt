/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.personal.activitypeek.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.personal.activitypeek.R
import com.personal.activitypeek.extensions.isAccessibilityNotStarted
import com.personal.activitypeek.extensions.isNotificationGranted
import com.personal.activitypeek.extensions.isSystemOverlayGranted
import com.personal.activitypeek.extensions.isUsageStatsGranted
import com.personal.activitypeek.extensions.setStatus
import com.personal.activitypeek.extensions.showMessage
import com.personal.activitypeek.manager.ServiceManager
import com.personal.activitypeek.repository.DataRepository
import com.personal.activitypeek.services.PackageMonitoringService
import com.personal.activitypeek.utils.AutostartUtil.isAutoStartPermissionAvailable
import com.personal.activitypeek.utils.AutostartUtil.requestAutoStartPermission
import com.personal.activitypeek.utils.DatabaseUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    lateinit var baseView: CoordinatorLayout
    lateinit var fabStart: FloatingActionButton
    lateinit var fragment: SettingsFragment

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isServiceBound = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        initViews()
        setupFab()
        setupPreferences()

        if (handleQsTileIntent(intent)) moveTaskToBack(true)
    }

    override fun onDestroy() {
        if (isServiceBound) {
            applicationContext.unbindService(serviceConnection)
            isServiceBound = false
        }
        DataRepository.updateStatus(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleQsTileIntent(intent)) moveTaskToBack(true)
    }

    private fun initViews() {
        fabStart = findViewById(R.id.start)
        baseView = findViewById(R.id.main)
        fragment =
            supportFragmentManager.findFragmentById(R.id.preferences_container) as SettingsFragment

        ViewCompat.setOnApplyWindowInsetsListener(baseView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupFab() {
        scope.launch {
            DataRepository.appState.collectLatest { state ->
                fabStart.setStatus(state.running)
            }
        }
        fabStart.setOnClickListener { onFabStartClicked() }
    }

    private fun setupPreferences() {
        fragment.enableAutostart.isVisible = isAutoStartPermissionAvailable(this)
        fragment.enableAutostart.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            requestAutoStartPermission(this)
            true
        }
    }

    private fun onFabStartClicked() {
        if (DataRepository.appState.value.running) {
            DataRepository.updateStatus(false)
            return
        }

        if (!requestMissingPermissions()) return

        DataRepository.updateStatus(true)
        val intent = Intent(this, PackageMonitoringService::class.java)
        applicationContext.startService(intent)
        applicationContext.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        ServiceManager(this).show()
        DataRepository.updateData(packageName, this::class.java.name)
    }

    private fun handleQsTileIntent(intent: Intent): Boolean {
        if (!intent.getBooleanExtra(EXTRA_FROM_QS_TILE, false)) return false
        onFabStartClicked()
        return DataRepository.appState.value.running
    }

    private fun requestMissingPermissions(): Boolean {
        val needsOverlay = DatabaseUtil.serviceMode == "0" && !isSystemOverlayGranted()

        val missing = listOf(
            isAccessibilityNotStarted() to ::requestAccessibilityPermission,
            !isUsageStatsGranted() to ::requestUsageStatsPermission,
            !isNotificationGranted() to ::requestNotificationPermission,
            needsOverlay to ::requestSystemOverlayPermission,
        )

        missing.filter { it.first }.forEach { it.second() }
        return missing.none { it.first }
    }

    private fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(POST_NOTIFICATIONS)
    }

    private fun requestSystemOverlayPermission() {
        showPermissionDialog(
            R.string.system_overlay_title,
            getString(R.string.system_overlay_description, getString(R.string.app_name))
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData("package:$packageName".toUri())
            )
        }
    }

    private fun requestAccessibilityPermission() {
        showPermissionDialog(
            R.string.accessibility_permission_title,
            getString(R.string.accessibility_permission_description, getString(R.string.app_name))
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun requestUsageStatsPermission() {
        showPermissionDialog(
            R.string.usage_access_title,
            getString(R.string.usage_access_description, getString(R.string.app_name))
        ) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun showPermissionDialog(
        @StringRes titleRes: Int,
        message: String,
        onSettings: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.settings) { dialog, _ -> dialog.dismiss(); onSettings() }
            .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun applyTheme() {
        setTheme(
            if (DatabaseUtil.useSystemFont) R.style.AppTheme_SystemFont
            else R.style.AppTheme
        )
    }

    companion object {
        const val EXTRA_FROM_QS_TILE = "from_qs_tile"
    }
}
