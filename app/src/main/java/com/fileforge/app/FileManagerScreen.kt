@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.fileforge.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private sealed interface Dlg {
    data object Delete : Dlg
    data object NewFolder : Dlg
    data object Zip : Dlg
    data class Rename(val path: String, val name: String) : Dlg
    data class Unzip(val path: String) : Dlg
}

@Composable
fun FileManagerScreen(vm: MainViewModel) {
    val ed = vm.editor
    if (ed != null) {
        EditorScreen(
            path = ed.path,
            original = ed.text,
            onSave = { text, cb -> vm.saveFile(ed.path, text, cb) },
            onClose = { vm.closeEditor() },
        )
    } else {
        BrowserScreen(vm)
    }
}

@Composable
private fun BrowserScreen(vm: MainViewModel) {
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    val ap = vm.panes[vm.active]
    val selCount = ap.selected.size

    BackHandler(enabled = selCount > 0 || (ap.path != vm.home && ap.path != "/")) {
        if (selCount > 0) ap.selected = emptySet() else vm.goUp(vm.active)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selCount > 0) "$selCount selected"
                        else "FileForge" + if (vm.rootMode) "  (root)" else ""
                    )
                },
                actions = {
                    if (selCount > 0) {
                        TextButton(onClick = { vm.selectAll(vm.active) }) { Text("All") }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (vm.rootMode) "Root mode: ON" else "Root mode: OFF") },
                            onClick = { menu = false; vm.setRoot(!vm.rootMode) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.showHidden) "Hidden files: shown" else "Hidden files: hidden") },
                            onClick = { menu = false; vm.showHidden = !vm.showHidden },
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by: ${vm.sort.label}") },
                            onClick = { menu = false; vm.sort = vm.sort.next() },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = { menu = false; vm.load(0); vm.load(1) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val has = selCount > 0
                    val one = selCount == 1
                    val onePath = if (one) ap.selected.first() else ""
                    TextButton(enabled = has, onClick = { vm.copySelected() }) { Text("Copy →") }
                    TextButton(enabled = has, onClick = { vm.moveSelected() }) { Text("Move →") }
                    TextButton(enabled = has, onClick = { dialog = Dlg.Delete }) { Text("Delete") }
                    TextButton(
                        enabled = one,
                        onClick = { dialog = Dlg.Rename(onePath, File(onePath).name) },
                    ) { Text("Rename") }
                    TextButton(enabled = has, onClick = { dialog = Dlg.Zip }) { Text("Zip") }
                    TextButton(
                        enabled = one && onePath.endsWith(".zip", true),
                        onClick = { dialog = Dlg.Unzip(onePath) },
                    ) { Text("Unzip") }
                    TextButton(onClick = { dialog = Dlg.NewFolder }) { Text("New folder") }
                }
            }
        },
    ) { pad ->
        val onItemClick: (Int, FileItem) -> Unit = { idx, item ->
            vm.active = idx
            val p = vm.panes[idx]
            when {
                p.selected.isNotEmpty() -> vm.toggle(idx, item.path)
                item.isDir -> vm.open(idx, item.path)
                item.name.endsWith(".zip", true) -> dialog = Dlg.Unzip(item.path)
                else -> vm.openEditor(item.path)
            }
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            PaneView(vm, 0, Modifier.weight(1f), onItemClick)
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            PaneView(vm, 1, Modifier.weight(1f), onItemClick)
        }
    }

    when (val d = dialog) {
        null -> {}
        Dlg.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete") },
            text = { Text("Delete $selCount item(s)? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.deleteSelected() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        Dlg.NewFolder -> InputDialog("New folder", "", { dialog = null }) { dialog = null; vm.newFolder(it) }
        Dlg.Zip -> InputDialog("Zip name", "archive", { dialog = null }) { dialog = null; vm.zipSelected(it) }
        is Dlg.Rename -> InputDialog("Rename", d.name, { dialog = null }) { dialog = null; vm.rename(d.path, it) }
        is Dlg.Unzip -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Extract") },
            text = { Text("Extract ${File(d.path).name} into a folder next to it?") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.unzip(d.path) }) { Text("Extract") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }

    vm.busy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(label) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
    }
}

@Composable
private fun InputDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onOk(v.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PaneView(vm: MainViewModel, idx: Int, modifier: Modifier, onClick: (Int, FileItem) -> Unit) {
    val p = vm.panes[idx]
    val isActive = vm.active == idx
    val shown = remember(p.items, vm.showHidden, vm.sort) { vm.arrange(p.items) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(onClick = { vm.active = idx }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.active = idx; vm.goUp(idx) }) {
                Icon(Icons.Default.KeyboardArrowUp, "Up")
            }
            Text(
                p.path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("${shown.size}  ", style = MaterialTheme.typography.labelMedium)
        }
        if (p.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val err = p.error
            when {
                err != null -> Text(err, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                shown.isEmpty() && !p.loading -> Text("Empty", Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { item ->
                        val sel = item.path in p.selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .combinedClickable(
                                    onClick = { onClick(idx, item) },
                                    onLongClick = { vm.active = idx; vm.toggle(idx, item.path) },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when {
                                    item.isDir -> "📁"
                                    item.name.endsWith(".zip", true) -> "🗜️"
                                    else -> "📄"
                                },
                                fontSize = 22.sp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (item.isDir) fmtDate(item.modified)
                                    else "${fmtSize(item.size)} · ${fmtDate(item.modified)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sel) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
