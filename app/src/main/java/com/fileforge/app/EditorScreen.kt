@file:OptIn(ExperimentalMaterial3Api::class)

package com.fileforge.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

private fun buildRegex(q: String, ignoreCase: Boolean, useRegex: Boolean): Regex? = try {
    val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    if (useRegex) Regex(q, opts) else Regex(Regex.escape(q), opts)
} catch (e: Exception) {
    null
}

private fun findAll(text: String, re: Regex): List<IntRange> {
    val out = ArrayList<IntRange>()
    for (m in re.findAll(text)) {
        if (m.value.isEmpty()) continue
        out.add(m.range)
        if (out.size >= 5000) break
    }
    return out
}

private fun withMatches(base: AnnotatedString, matches: List<IntRange>, cur: Int, dark: Boolean): AnnotatedString {
    val b = AnnotatedString.Builder(base)
    val n = base.length
    val normal = if (dark) Color(0x66FFD54F) else Color(0x66FFB300)
    val current = if (dark) Color(0xCCFF9800) else Color(0xCCFF6D00)
    matches.forEachIndexed { i, r ->
        val s = r.first
        val e = r.last + 1
        if (s < n && e <= n) b.addStyle(SpanStyle(background = if (i == cur) current else normal), s, e)
    }
    return b.toAnnotatedString()
}

@Composable
fun EditorScreen(
    path: String,
    original: String,
    onSave: (String, (Boolean) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val name = remember(path) { File(path).name }
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var tfv by remember { mutableStateOf(TextFieldValue(original)) }
    var savedText by remember { mutableStateOf(original) }
    val undo = remember { ArrayDeque<TextFieldValue>() }
    val redo = remember { ArrayDeque<TextFieldValue>() }
    var lastEdit by remember { mutableLongStateOf(0L) }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var repl by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var useRegex by remember { mutableStateOf(false) }
    var matchIdx by remember { mutableIntStateOf(-1) }
    var confirmExit by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val dirty = tfv.text != savedText
    val grammar = remember(name) { Highlighter.forName(name) }
    val big = tfv.text.length > 300_000

    val re = remember(query, ignoreCase, useRegex) { if (query.isEmpty()) null else buildRegex(query, ignoreCase, useRegex) }
    val matches = remember(tfv.text, re, searching) {
        if (!searching || re == null) emptyList() else findAll(tfv.text, re)
    }

    val cur = matchIdx
    val transformation = remember(grammar, dark, matches, cur, big) {
        VisualTransformation { t ->
            val hl = if (big) AnnotatedString(t.text) else Highlighter.highlight(t.text, grammar, dark)
            TransformedText(
                if (matches.isEmpty()) hl else withMatches(hl, matches, cur, dark),
                OffsetMapping.Identity,
            )
        }
    }

    fun pushUndo(force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || undo.isEmpty() || now - lastEdit > 700) {
            undo.addLast(tfv)
            if (undo.size > 100) undo.removeFirst()
        }
        lastEdit = now
    }

    fun doUndo() {
        if (undo.isEmpty()) return
        redo.addLast(tfv)
        tfv = undo.removeLast()
        lastEdit = 0L
    }

    fun doRedo() {
        if (redo.isEmpty()) return
        undo.addLast(tfv)
        tfv = redo.removeLast()
        lastEdit = 0L
    }

    fun goTo(i: Int) {
        if (matches.isEmpty()) return
        val k = ((i % matches.size) + matches.size) % matches.size
        matchIdx = k
        val r = matches[k]
        tfv = tfv.copy(selection = TextRange(r.first, r.last + 1))
        try {
            layout?.let { l ->
                val y = l.getLineTop(l.getLineForOffset(r.first)).toInt()
                scope.launch { scroll.animateScrollTo((y - 150).coerceAtLeast(0)) }
            }
        } catch (e: Exception) {
        }
    }

    fun replArg(): String = if (useRegex) repl else Regex.escapeReplacement(repl)

    fun replaceCurrent() {
        val r = re ?: return
        val k = matchIdx
        if (k !in matches.indices) {
            goTo(0)
            return
        }
        val m = matches[k]
        val text = tfv.text
        try {
            val rep = r.replaceFirst(text.substring(m.first, m.last + 1), replArg())
            pushUndo(true)
            redo.clear()
            tfv = TextFieldValue(text.replaceRange(m.first, m.last + 1, rep), TextRange(m.first + rep.length))
            matchIdx = k - 1
        } catch (e: Exception) {
        }
    }

    fun replaceAll() {
        val r = re ?: return
        try {
            val newText = r.replace(tfv.text, replArg())
            pushUndo(true)
            redo.clear()
            tfv = TextFieldValue(newText)
            matchIdx = -1
        } catch (e: Exception) {
        }
    }

    fun save() {
        val t = tfv.text
        onSave(t) { ok -> if (ok) savedText = t }
    }

    BackHandler {
        when {
            searching -> searching = false
            dirty -> confirmExit = true
            else -> onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmExit = true else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = { Text(name + if (dirty) " •" else "", maxLines = 1) },
                actions = {
                    TextButton(onClick = { doUndo() }) { Text("Undo") }
                    TextButton(onClick = { doRedo() }) { Text("Redo") }
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Find") }
                    TextButton(onClick = { save() }, enabled = dirty) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (searching) {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it; matchIdx = -1 },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Find") },
                                isError = query.isNotEmpty() && re == null,
                            )
                            IconButton(onClick = { goTo(matchIdx - 1) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
                            IconButton(onClick = { goTo(matchIdx + 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = repl,
                                onValueChange = { repl = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Replace with") },
                            )
                            TextButton(onClick = { replaceCurrent() }) { Text("Replace") }
                            TextButton(onClick = { replaceAll() }) { Text("All") }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = ignoreCase,
                                onClick = { ignoreCase = !ignoreCase; matchIdx = -1 },
                                label = { Text("Ignore case") },
                            )
                            FilterChip(
                                selected = useRegex,
                                onClick = { useRegex = !useRegex; matchIdx = -1 },
                                label = { Text("Regex") },
                            )
                            Text(
                                if (matches.isEmpty()) "0 found" else "${(cur + 1).coerceAtLeast(0)}/${matches.size}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    BasicTextField(
                        value = tfv,
                        onValueChange = { nv ->
                            if (nv.text != tfv.text) {
                                pushUndo(false)
                                redo.clear()
                            }
                            tfv = nv
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        onTextLayout = { layout = it },
                    )
                }
            }

            val sel = tfv.selection.start.coerceIn(0, tfv.text.length)
            val before = tfv.text.substring(0, sel)
            val line = before.count { it == '\n' } + 1
            val col = sel - (before.lastIndexOf('\n') + 1) + 1
            Surface(tonalElevation = 3.dp) {
                Text(
                    "Ln $line, Col $col  ·  ${tfv.text.length} chars  ·  ${grammar?.label ?: "Plain text"}" +
                        if (big) "  ·  (highlighting off: large file)" else "",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Unsaved changes") },
            text = { Text("Discard your changes to $name?") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("Discard") } },
            dismissButton = {
                TextButton(onClick = {
                    confirmExit = false
                    save()
                }) { Text("Save") }
            },
        )
    }
}
