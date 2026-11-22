package com.grude.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AddedBg = Color(0x22059669)
private val RemovedBg = Color(0x22DC2626)
private val HunkBg = Color(0x1E2563EB)

private val KeywordColor = Color(0xFF7C3AED)
private val StringColor = Color(0xFF047857)
private val CommentColor = Color(0xFF64748B)
private val AnnotationColor = Color(0xFFD97706)
private val NumberColor = Color(0xFF2563EB)

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "data", "sealed", "interface", "enum",
    "package", "import", "return", "if", "else", "when", "for", "while", "suspend",
    "private", "public", "internal", "override", "companion", "try", "catch",
    "true", "false", "null", "void", "static", "final", "const", "let", "function",
    "async", "await", "name", "uses", "run", "with", "jobs", "steps", "on"
)

private val TOKEN_REGEX = Regex(
    """(//.*|#.*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[A-Za-z_][A-Za-z0-9_]*|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)"""
)

/**
 * Syntax-highlighted unified diff viewer for GitHub Pull Request patches.
 * Highlights added (+), removed (-), and hunk (@@) lines alongside inline code tokens.
 */
@Composable
fun SyntaxDiffViewer(
    patch: String?,
    modifier: Modifier = Modifier
) {
    if (patch.isNullOrBlank()) {
        Text(
            "Binary file or diff too large to preview inline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(8.dp)
        )
        return
    }

    val lines = remember(patch) { patch.lines() }
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
            .padding(vertical = 4.dp)
    ) {
        lines.forEach { rawLine ->
            val bg = when {
                rawLine.startsWith("@@") -> HunkBg
                rawLine.startsWith("+") && !rawLine.startsWith("+++") -> AddedBg
                rawLine.startsWith("-") && !rawLine.startsWith("---") -> RemovedBg
                else -> Color.Transparent
            }
            val prefixColor = when {
                rawLine.startsWith("@@") -> Color(0xFF2563EB)
                rawLine.startsWith("+") -> Color(0xFF059669)
                rawLine.startsWith("-") -> Color(0xFFDC2626)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val styledLine = remember(rawLine) {
                if (rawLine.startsWith("@@")) {
                    AnnotatedString(
                        rawLine,
                        SpanStyle(color = prefixColor, fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    highlightDiffLine(rawLine, prefixColor)
                }
            }

            Text(
                text = styledLine,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

private fun highlightDiffLine(rawLine: String, prefixColor: Color): AnnotatedString {
    if (rawLine.isEmpty()) return AnnotatedString("")
    val prefix = rawLine.first()
    val hasDiffPrefix = prefix == '+' || prefix == '-' || prefix == ' '
    val codePart = if (hasDiffPrefix) rawLine.substring(1) else rawLine

    return buildAnnotatedString {
        if (hasDiffPrefix) {
            pushStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold))
            append(prefix)
            pop()
        }
        var lastIndex = 0
        for (match in TOKEN_REGEX.findAll(codePart)) {
            if (match.range.first > lastIndex) {
                append(codePart.substring(lastIndex, match.range.first))
            }
            val token = match.value
            val style = when {
                token.startsWith("//") || token.startsWith("#") -> SpanStyle(color = CommentColor)
                token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = StringColor)
                token.startsWith("@") -> SpanStyle(color = AnnotationColor, fontWeight = FontWeight.Medium)
                token.first().isDigit() -> SpanStyle(color = NumberColor)
                token in CODE_KEYWORDS -> SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)
                else -> null
            }
            if (style != null) {
                pushStyle(style)
                append(token)
                pop()
            } else {
                append(token)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < codePart.length) {
            append(codePart.substring(lastIndex))
        }
    }
}
