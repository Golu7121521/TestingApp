package com.example.filerecovery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filerecovery.data.FileCategory
import com.example.filerecovery.data.RecoverableFile
import com.example.filerecovery.data.TrashScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                s.copy(
                    scanning = false,
                    files = found,
                    status = if (found.isEmpty()) "Koi deleted file nahi mili"
                    else "${found.size} recoverable files mili"
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
