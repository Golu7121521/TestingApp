package com.fileforge.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

enum class Tok { COMMENT, STRING, NUMBER, KEYWORD, TYPE, TAG, ATTR, ANNOT, DIRECTIVE, REG }

/** One combined regex; each rule is its own capture group (rule sources must not contain capture groups). */
class Grammar(val label: String, rules: List<Pair<String, Tok>>) {
    val kinds: List<Tok> = rules.map { it.second }
    val regex: Regex = Regex(rules.joinToString("|") { "(${it.first})" })
}

private const val DQ = "\""
private const val D = "$"

private fun kw(vararg w: String) = "\\b(?:" + w.joinToString("|") + ")\\b"

private val STR_D = """$DQ(?:\\.|[^$DQ\\\n])*$DQ"""
private val STR_S = """'(?:\\.|[^'\\\n])*'"""
private val STR_3D = "$DQ$DQ$DQ[\\s\\S]*?$DQ$DQ$DQ"
private val STR_3S = "'''[\\s\\S]*?'''"
private val NUM = """\b(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFlLuU]*)\b"""

object Highlighter {

    private val CLIKE = Grammar(
        "Code",
        listOf(
            """//[^\n]*""" to Tok.COMMENT,
            """/\*[\s\S]*?\*/""" to Tok.COMMENT,
            STR_3D to Tok.STRING,
            STR_D to Tok.STRING,
            STR_S to Tok.STRING,
            """@[A-Za-z_]\w*""" to Tok.ANNOT,
            NUM to Tok.NUMBER,
            kw(
                "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue",
                "data", "default", "defer", "do", "else", "enum", "export", "extends", "false", "final",
                "finally", "fn", "for", "fun", "func", "function", "go", "if", "impl", "implements", "import",
                "in", "interface", "is", "let", "match", "mod", "mut", "namespace", "new", "null", "nil",
                "object", "override", "package", "private", "protected", "pub", "public", "return", "sealed",
                "self", "static", "struct", "super", "switch", "this", "throw", "throws", "true", "try",
                "type", "typealias", "use", "val", "var", "void", "when", "while", "yield",
                "int", "long", "float", "double", "boolean", "char", "byte", "short", "string", "bool",
            ) to Tok.KEYWORD,
            """\b[A-Z][A-Za-z0-9_]*\b""" to Tok.TYPE,
        ),
    )

    private val HASH = Grammar(
        "Script",
        listOf(
            """#[^\n]*""" to Tok.COMMENT,
            STR_3D to Tok.STRING,
            STR_3S to Tok.STRING,
            STR_D to Tok.STRING,
            STR_S to Tok.STRING,
            NUM to Tok.NUMBER,
            kw(
                "and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del",
                "do", "done", "elif", "else", "esac", "except", "export", "fi", "finally", "for", "from",
                "function", "global", "if", "import", "in", "is", "lambda", "local", "None", "not", "or",
                "pass", "raise", "return", "then", "try", "while", "with", "yield", "True", "False",
                "true", "false", "null", "echo", "exit", "set",
            ) to Tok.KEYWORD,
        ),
    )

    private val XML = Grammar(
        "XML",
        listOf(
            """<!--[\s\S]*?-->""" to Tok.COMMENT,
            """<!\[CDATA\[[\s\S]*?\]\]>""" to Tok.STRING,
            """<\?[\s\S]*?\?>""" to Tok.DIRECTIVE,
            """$DQ[^$DQ]*$DQ""" to Tok.STRING,
            """'[^'<>\n]*'""" to Tok.STRING,
            """</?[A-Za-z_][\w:.-]*""" to Tok.TAG,
            """/?>""" to Tok.TAG,
            """[A-Za-z_:][\w:.-]*(?==)""" to Tok.ATTR,
        ),
    )

    private val JSON = Grammar(
        "JSON",
        listOf(
            """$DQ(?:\\.|[^$DQ\\\n])*$DQ(?=\s*:)""" to Tok.ATTR,
            STR_D to Tok.STRING,
            """-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""" to Tok.NUMBER,
            """\b(?:true|false|null)\b""" to Tok.KEYWORD,
        ),
    )

    private val SMALI = Grammar(
        "Smali",
        listOf(
            """#[^\n]*""" to Tok.COMMENT,
            STR_D to Tok.STRING,
            """\.[a-z][\w-]*""" to Tok.DIRECTIVE,
            """:[\w]+""" to Tok.TAG,
            """\b[vp]\d+\b""" to Tok.REG,
            """\bL[\w/$D]+;""" to Tok.TYPE,
            """-?0x[0-9a-fA-F]+[LlSsTt]?""" to Tok.NUMBER,
            """\b(?:invoke|move|const|return|if|goto|new|iget|iput|sget|sput|aget|aput|add|sub|mul|div|rem|and|or|xor|shl|shr|check|instance|array|throw|nop|cmp|neg|not|int|long|float|double)[\w/-]*""" to Tok.KEYWORD,
        ),
    )

    fun forName(name: String): Grammar? = when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts", "java", "js", "mjs", "ts", "jsx", "tsx", "c", "h", "cpp", "hpp", "cc", "cs", "go",
        "rs", "swift", "dart", "gradle", "scala", "php" -> CLIKE
        "xml", "html", "htm", "svg", "xhtml", "plist" -> XML
        "json" -> JSON
        "py", "sh", "bash", "yml", "yaml", "toml", "conf", "properties", "prop", "ini", "rb", "pl", "mk",
        "cfg" -> HASH
        "smali" -> SMALI
        else -> null
    }

    private fun color(t: Tok, dark: Boolean): Color = if (dark) {
        when (t) {
            Tok.COMMENT -> Color(0xFF7F8C8D)
            Tok.STRING -> Color(0xFF98C379)
            Tok.NUMBER -> Color(0xFFD19A66)
            Tok.KEYWORD -> Color(0xFFC678DD)
            Tok.TYPE -> Color(0xFFE5C07B)
            Tok.TAG -> Color(0xFFE06C75)
            Tok.ATTR -> Color(0xFFD19A66)
            Tok.ANNOT -> Color(0xFF56B6C2)
            Tok.DIRECTIVE -> Color(0xFF61AFEF)
            Tok.REG -> Color(0xFF56B6C2)
        }
    } else {
        when (t) {
            Tok.COMMENT -> Color(0xFF6A737D)
            Tok.STRING -> Color(0xFF2E7D32)
            Tok.NUMBER -> Color(0xFFB35900)
            Tok.KEYWORD -> Color(0xFF8E24AA)
            Tok.TYPE -> Color(0xFF9A6700)
            Tok.TAG -> Color(0xFFC62828)
            Tok.ATTR -> Color(0xFFB35900)
            Tok.ANNOT -> Color(0xFF00838F)
            Tok.DIRECTIVE -> Color(0xFF1565C0)
            Tok.REG -> Color(0xFF00838F)
        }
    }

    fun highlight(text: String, g: Grammar?, dark: Boolean): AnnotatedString {
        if (g == null) return AnnotatedString(text)
        return try {
            buildAnnotatedString {
                append(text)
                for (m in g.regex.findAll(text)) {
                    var gi = 1
                    while (gi <= g.kinds.size && m.groups[gi] == null) gi++
                    if (gi > g.kinds.size) continue
                    addStyle(SpanStyle(color = color(g.kinds[gi - 1], dark)), m.range.first, m.range.last + 1)
                }
            }
        } catch (e: Throwable) { // e.g. StackOverflowError on huge single lines
            AnnotatedString(text)
        }
    }
}
