package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.TextStyle

/**
 * Soft-wraps a string into lines that fit within [maxWidth].
 *
 * The algorithm preserves hard line breaks (`\n`) and otherwise breaks on
 * whitespace using a greedy first-fit strategy: each word is appended to the
 * current line if it still fits, otherwise it starts a new line. Words longer
 * than [maxWidth] are emitted on a line of their own and overflow horizontally
 * — they are not split mid-word, which is the right tradeoff for a developer-
 * facing PDF API where word integrity matters more than perfect packing.
 *
 * @param text input string. Tabs are not interpreted.
 * @param style style applied uniformly to every glyph. Used both to measure
 *   widths and to compute line height.
 * @param maxWidth available horizontal space in PDF points.
 * @param metrics platform-provided text measurement service.
 *
 * @return a [MeasuredText] containing every wrapped line, the resolved style,
 *   and the total occupied size of the block.
 */
public fun layoutText(
    text: String,
    style: TextStyle,
    maxWidth: Float,
    metrics: FontMetrics,
): MeasuredText {
    val lines = mutableListOf<TextLine>()
    val sample = metrics.measure("Hg", style)
    val effectiveLineHeight = if (style.lineHeight.value > 0f) {
        style.lineHeight.value
    } else {
        sample.lineHeight
    }
    val baseline = sample.ascent

    text.split('\n').forEach { hardLine ->
        if (hardLine.isEmpty()) {
            lines += TextLine(text = "", width = 0f, baseline = baseline, height = effectiveLineHeight)
            return@forEach
        }
        wrapHardLine(hardLine, style, maxWidth, metrics, baseline, effectiveLineHeight, lines)
    }

    val totalHeight = effectiveLineHeight * lines.size
    val widest = lines.maxOfOrNull { it.width } ?: 0f
    // [paragraphWidth] is the slot the parent gave us — it stays at
    // [maxWidth] so non-Start alignments (`Center` / `End` / `Justify`)
    // anchor against the full slot rather than the widest line. The
    // measured [size.width], by contrast, reports the *intrinsic*
    // text width so containers like `row(arrangement = SpaceBetween)`
    // can pack children without each one gobbling the full row width.
    val paragraphWidth = if (maxWidth == Float.POSITIVE_INFINITY) widest else maxWidth
    return MeasuredText(
        lines = lines.toList(),
        style = style,
        size = Size(width = widest, height = totalHeight),
        paragraphWidth = paragraphWidth,
    )
}

private fun wrapHardLine(
    hardLine: String,
    style: TextStyle,
    maxWidth: Float,
    metrics: FontMetrics,
    baseline: Float,
    lineHeight: Float,
    out: MutableList<TextLine>,
) {
    val words = hardLine.split(' ')
    var current = StringBuilder()
    var currentWidth = 0f

    fun flush() {
        if (current.isNotEmpty()) {
            out += TextLine(current.toString(), currentWidth, baseline, lineHeight)
            current = StringBuilder()
            currentWidth = 0f
        }
    }

    for ((index, word) in words.withIndex()) {
        if (word.isEmpty() && index != words.lastIndex) {
            // Two spaces in a row — treat as a literal space inside the current line.
            if (current.isNotEmpty()) {
                current.append(' ')
                currentWidth = metrics.measure(current.toString(), style).width
            }
            continue
        }
        val candidate = if (current.isEmpty()) word else "$current $word"
        val candidateWidth = metrics.measure(candidate, style).width
        when {
            candidate.isEmpty() -> Unit
            candidateWidth <= maxWidth || current.isEmpty() -> {
                current = StringBuilder(candidate)
                currentWidth = candidateWidth
            }
            else -> {
                flush()
                current = StringBuilder(word)
                currentWidth = metrics.measure(word, style).width
            }
        }
    }
    flush()
}
