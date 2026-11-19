package com.fileforge.app

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class PaneState(start: String) {
    var path by mutableStateOf(start)
    var items by mutableStateOf<List<FileItem>>(emptyList())
    var selected by mutableStateOf<Set<String>>(emptySet())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val home: String = Environment.getExternalStorageDirectory().absolutePath
    val panes = listOf(PaneState(home), PaneState(home))

    var active by mutableIntStateOf(0)
    var showHidden by mutableStateOf(false)
    var rootMode by mutableStateOf(false)
    var sort by mutableStateOf(SortMode.NAME)
    var busy by mutableStateOf<String?>(null)
    var editor by mutableStateOf<EditorFile?>(null)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var started = false

    init {
        FileOps.cacheDir = app.cacheDir
    }

    fun start() {
        if (started) return
        started = true
        load(0)
        load(1)
    }

    // ------------------------------------------------------------ browsing

    fun load(i: Int) {
        val p = panes[i]
        val path = p.path
        val root = rootMode
        viewModelScope.launch(Dispatchers.IO) {
            p.loading = true
            try {
                p.items = FileOps.list(path, root)
                p.error = null
            } catch (e: Throwable) {
                p.items = emptyList()
                p.error = e.message ?: "Cannot open folder"
            }
            p.selected = emptySet()
            p.loading = false
        }
    }

    fun open(i: Int, path: String) {
        panes[i].path = path
        load(i)
    }

    fun goUp(i: Int): Boolean {
        val parent = File(panes[i].path).parent ?: return false
        open(i, parent)
        return true
    }

    fun arrange(items: List<FileItem>): List<FileItem> {
        val base: Comparator<FileItem> = when (sort) {
            SortMode.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.SIZE -> compareByDescending<FileItem> { it.size }
            SortMode.DATE -> compareByDescending<FileItem> { it.modified }
        }
        return items
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareByDescending<FileItem> { it.isDir }.then(base))
    }

    fun toggle(i: Int, path: String) {
        val p = panes[i]
        p.selected = if (path in p.selected) p.selected - path else p.selected + path
    }

    fun selectAll(i: Int) {
        val p = panes[i]
        p.selected = arrange(p.items).map { it.path }.toSet()
    }

    fun setRoot(on: Boolean) {
        if (!on) {
            rootMode = false
            load(0)
            load(1)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (FileOps.hasRoot()) {
                rootMode = true
                load(0)
                load(1)
            } else {
                messages.tryEmit("Root access not available")
            }
        }
    }

    // ------------------------------------------------------------ operations

    private fun runOp(label: String, block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = label
            try {
                block()
                messages.tryEmit("Done")
            } catch (e: Throwable) {
                messages.tryEmit("Error: ${e.message ?: e.javaClass.simpleName}")
            }
            busy = null
            load(0)
            load(1)
        }
    }

    fun copySelected() {
        val items = panes[active].selected.toList()
        if (items.isEmpty()) return
        val to = panes[1 - active].path
        val root = rootMode
        runOp("Copying…") { items.forEach { FileOps.copy(it, to, root) } }
    }

    fun moveSelected() {
        val items = panes[active].selected.toList()
        if (items.isEmpty()) return
        val to = panes[1 - active].path
        val root = rootMode
        runOp("Moving…") { items.forEach { FileOps.move(it, to, root) } }
    }

    fun deleteSelected() {
        val items = panes[active].selected.toList()
        if (items.isEmpty()) return
        val root = rootMode
        runOp("Deleting…") { items.forEach { FileOps.delete(it, root) } }
    }

    fun rename(path: String, newName: String) {
        val root = rootMode
        runOp("Renaming…") { FileOps.rename(path, newName, root) }
    }

    fun newFolder(name: String) {
        val dir = panes[active].path
        val root = rootMode
        runOp("Creating folder…") { FileOps.mkdir(File(dir, name).absolutePath, root) }
    }

    fun zipSelected(name: String) {
        val p = panes[active]
        val items = p.selected.toList()
        if (items.isEmpty()) return
        val dest = File(p.path, if (name.endsWith(".zip", true)) name else "$name.zip").absolutePath
        runOp("Compressing…") { FileOps.zip(items, dest) }
    }

    fun unzip(path: String) {
        val f = File(path)
        val dest = File(f.parentFile, f.nameWithoutExtension).absolutePath
        runOp("Extracting…") { FileOps.unzip(path, dest) }
    }

    // ------------------------------------------------------------ editor

    fun openEditor(path: String) {
        val root = rootMode
        viewModelScope.launch(Dispatchers.IO) {
            busy = "Opening…"
            try {
                editor = EditorFile(path, FileOps.readText(path, root))
            } catch (e: Throwable) {
                messages.tryEmit(e.message ?: "Cannot open file")
            }
            busy = null
        }
    }

    fun saveFile(path: String, text: String, onResult: (Boolean) -> Unit) {
        val root = rootMode
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileOps.writeText(path, text, root)
                messages.tryEmit("Saved")
                onResult(true)
            } catch (e: Throwable) {
                messages.tryEmit("Save failed: ${e.message ?: e.javaClass.simpleName}")
                onResult(false)
            }
        }
    }

    fun closeEditor() {
        editor = null
        load(0)
        load(1)
    }
}
