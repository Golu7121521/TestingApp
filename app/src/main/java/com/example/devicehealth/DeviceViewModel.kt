package com.example.devicehealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    init {
        // Live refresh every 2 seconds (battery temp, level, charging state)
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(2000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _snapshot.value = withContext(Dispatchers.IO) {
                DeviceInfo.snapshot(getApplication())
            }
        }
    }
}
