package com.gitactionbuild

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Plain text that is still colored: keywords, strings, numbers, comments, function calls and types.
 * Works on any text file; no grammar files or native libraries are needed.
 */
class TextColorLanguage : Language by EmptyLanguage() {
    private val analyzer = ColorAnalyzer()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer
}

class ColorAnalyzer : SimpleAnalyzeManager<Unit>() {

    private class Seg(val from: Int, val to: Int, val color: Int)

    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<Unit>.Delegate<Unit>): Styles {
        val src = text.toString()

        // start index of every line, and the end (exclusive, without '\n') of every line
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in src.indices) {
            if (src[i] == '\n') starts.add(i + 1)
        }
        val lines = starts.size
        val ends = IntArray(lines) { i -> if (i + 1 < lines) starts[i + 1] - 1 else src.length }

        // collect colored segments per line
        val segs = Array(lines) { ArrayList<Seg>() }
        for (m in TOKEN.findAll(src)) {
            val color = colorOf(m) ?: continue
            var s = m.range.first
            val end = m.range.last + 1
            while (s < end) {
                val line = lineOf(starts, s)
                val e = minOf(end, ends[line])
                if (e > s) segs[line].add(Seg(s - starts[line], e - starts[line], color))
                s = if (line + 1 < lines) starts[line + 1] else end
            }
        }

        // build spans line by line: a normal span at column 0, then colored runs
        val normal = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        val builder = MappedSpans.Builder(lines)
        for (line in 0 until lines) {
            val out = ArrayList<Pair<Int, Long>>()
            fun put(col: Int, style: Long) {
                if (out.isNotEmpty() && out[out.size - 1].first == col) {
                    out[out.size - 1] = col to style
                } else {
                    out.add(col to style)
                }
            }
            put(0, normal)
            for (sg in segs[line]) {
                put(sg.from, TextStyle.makeStyle(sg.color))
                put(sg.to, normal)
            }
            for ((col, style) in out) {
                builder.add(line, SpanFactory.obtainNoExt(col, style))
            }
        }

        val styles = Styles(builder.build())
        styles.finishBuilding()
        return styles
    }

    private fun colorOf(m: MatchResult): Int? = when {
        m.groups[1] != null -> EditorColorScheme.COMMENT
        m.groups[2] != null -> EditorColorScheme.LITERAL
        m.groups[3] != null -> EditorColorScheme.ATTRIBUTE_VALUE
        m.groups[4] != null -> EditorColorScheme.KEYWORD
        m.groups[5] != null -> EditorColorScheme.FUNCTION_NAME
        m.groups[6] != null -> EditorColorScheme.ANNOTATION
        else -> null
    }

    // binary search: index of the line that contains position pos
    private fun lineOf(starts: List<Int>, pos: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        private const val KEYWORDS =
            "if|else|elif|for|while|do|return|fun|val|var|class|interface|object|enum|struct|import|package|" +
                "def|function|const|let|new|true|false|null|None|this|self|try|catch|except|finally|throw|raise|" +
                "when|break|continue|switch|case|default|public|private|protected|static|final|override|void|" +
                "int|long|double|float|boolean|char|async|await|yield|from|in|is|as|and|or|not|with|lambda|pass"

        // group 1 comment, 2 string, 3 number, 4 keyword, 5 function call, 6 type (capitalized word)
        private val TOKEN = Regex(
            "(//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
                "|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
                "|(\\b\\d+(?:\\.\\d+)?\\b)" +
                "|(\\b(?:$KEYWORDS)\\b)" +
                "|(\\b[A-Za-z_]\\w*(?=\\s*\\())" +
                "|(\\b[A-Z]\\w*\\b)"
        )
    }
}
