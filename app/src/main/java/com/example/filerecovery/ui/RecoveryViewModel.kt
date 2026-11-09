package com.example.filerecovery.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filerecovery.data.FileCategory
import com.example.filerecovery.data.RecoverableFile
import com.example.filerecovery.data.RootCarver
import com.example.filerecovery.data.Source
import com.example.filerecovery.data.TrashScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanState(
    val scanning: Boolean = false,
    val files: List<RecoverableFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val filter: FileCategory = FileCategory.ALL,
    val status: String = "Scan shuru karne ke liye button dabao"
)

class RecoveryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, selected = emptySet(), status = "Scanning...") }

        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                TrashScanner.scan { folder ->
                    _state.update { s -> s.copy(status = "Checking: $folder") }
                }
            }
            _state.update { s ->
                val all = found + s.files.filter { it.source == Source.CARVED }
                s.copy(
                    scanning = false,
                    files = all,
                    status = if (all.isEmpty()) "Kuch nahi mila. Deep scan (root) try karo"
                    else "${all.size} files mili"
                )
            }
        }
    }

    fun deepScan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, selected = emptySet(), status = "Root check ho raha hai...") }

        viewModelScope.launch {
            val (message, carved) = withContext(Dispatchers.IO) {
                if (!RootCarver.isRooted()) {
                    return@withContext "Root access nahi mila (su permission deni hogi)" to emptyList<RecoverableFile>()
                }
                val device = RootCarver.findDataDevice()
                    ?: return@withContext "/data partition nahi mila" to emptyList<RecoverableFile>()
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FileRecovery/Carved"
                )
                val files = RootCarver.deepScan(device, dir) { msg ->
                    _state.update { s -> s.copy(status = msg) }
                }
                "Deep scan complete: ${files.size} files mili" to files
            }
            _state.update { s ->
                s.copy(
                    scanning = false,
                    files = s.files.filter { it.source != Source.CARVED } + carved,
                    status = message
                )
            }
        }
    }

    fun toggle(id: String) {
        _state.update { s ->
            s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
        }
    }

    fun setFilter(category: FileCategory) {
        _state.update { it.copy(filter = category) }
    }

    fun selectAllVisible() {
        _state.update { s ->
            val visibleIds = s.files
                .filter { s.filter == FileCategory.ALL || it.category == s.filter }
                .map { it.id }
                .toSet()
            s.copy(selected = visibleIds)
        }
    }

    fun restoreSelected() {
        val snapshot = _state.value
        val items = snapshot.files.filter { it.id in snapshot.selected }
        if (items.isEmpty()) return

        viewModelScope.launch {
            val restoredIds = withContext(Dispatchers.IO) {
                TrashScanner.restore(getApplication(), items)
            }
            _state.update { s ->
                s.copy(
                    files = s.files.filterNot { it.id in restoredIds },
                    selected = s.selected - restoredIds.toSet(),
                    status = "${restoredIds.size} files restore ho gayi"
                )
            }
        }
    }
}
