package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextOverflow
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.style.resolve

/**
 * Invisible break opportunity (U+00AD). When a line breaks at a soft
 * hyphen a visible `-` is rendered at the line end; otherwise the
 * character is stripped entirely.
 */
private const val SOFT_HYPHEN: Char = '\u00AD'

/**
 * Soft-wraps a string into lines that fit within [maxWidth].
 *
 * The algorithm preserves hard line breaks (`\n`) and otherwise breaks on
 * whitespace using a greedy first-fit strategy: each word is appended to the
 * current line if it still fits, otherwise it starts a new line. Within a
 * word, soft hyphens (U+00AD) mark extra break opportunities — when one is
 * taken, a visible hyphen is emitted at the line end. Words longer than
 * [maxWidth] with no usable break point are split mid-word so narrow
 * containers never overflow horizontally.
 *
 * After wrapping, [TextStyle.maxLines] truncates the paragraph (optionally
 * ellipsizing the last visible line per [TextStyle.overflow]) and
 * [TextAlign.Justify] distributes the leftover slack of every non-final
 * line between its words.
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
    var lines = mutableListOf<TextLine>()
    val sample = metrics.measure("Hg", style)
    val effectiveLineHeight = if (style.lineHeight.value > 0f) {
        style.lineHeight.value
    } else {
        sample.lineHeight
    }
    val baseline = sample.ascent

    // Indices of lines that end a hard paragraph — justification must leave
    // them start-aligned, matching every word processor's convention.
    val paragraphEnds = mutableSetOf<Int>()
    text.split('\n').forEach { hardLine ->
        if (hardLine.isEmpty()) {
            lines += TextLine(text = "", width = 0f, baseline = baseline, height = effectiveLineHeight)
        } else {
            wrapHardLine(hardLine, style, maxWidth, metrics, baseline, effectiveLineHeight, lines)
        }
        if (lines.isNotEmpty()) paragraphEnds += lines.lastIndex
    }

    val limit = style.maxLines
    if (limit != null && limit > 0 && lines.size > limit) {
        val kept = lines.take(limit).toMutableList()
        if (style.overflow == TextOverflow.Ellipsis) {
            kept[kept.lastIndex] = ellipsize(kept.last(), style, maxWidth, metrics)
        }
        lines = kept
        // The cut line becomes the visual end of the paragraph — never
        // stretch it with justification.
        paragraphEnds += lines.lastIndex
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

    val direction = style.direction.resolve(text)
    if (style.align == TextAlign.Justify) {
        for (i in lines.indices) {
            if (i in paragraphEnds) continue
            lines[i] = justifyLine(lines[i], paragraphWidth, style, metrics, direction)
        }
    }

    return MeasuredText(
        lines = lines.toList(),
        style = style,
        size = Size(width = widest, height = totalHeight),
        paragraphWidth = paragraphWidth,
        resolvedDirection = direction,
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

    for ((index, rawWord) in words.withIndex()) {
        if (rawWord.isEmpty() && index != words.lastIndex) {
            // Two spaces in a row — treat as a literal space inside the current line.
            if (current.isNotEmpty()) {
                current.append(' ')
                currentWidth = metrics.measure(current.toString(), style).width
                // A space that would overflow the slot dies at the line
                // break, like the implicit space between wrapped words.
                if (currentWidth > maxWidth) {
                    current.setLength(current.length - 1)
                    currentWidth = metrics.measure(current.toString(), style).width
                    flush()
                }
            }
            continue
        }

        // A single source word may produce several lines (soft-hyphen or
        // mid-word breaks), so loop until its remainder is consumed.
        var word = rawWord
        while (word.isNotEmpty()) {
            // Soft hyphens are invisible unless a break lands on them.
            val display = stripSoftHyphens(word)
            val candidate = if (current.isEmpty()) display else "$current $display"
            if (candidate.isEmpty()) break
            val candidateWidth = metrics.measure(candidate, style).width

            if (candidateWidth <= maxWidth) {
                current = StringBuilder(candidate)
                currentWidth = candidateWidth
                break
            }

            // Doesn't fit whole. Try ending this line at a soft hyphen first
            // — a hyphenated break reads better than spilling or hard cuts.
            if (word.contains(SOFT_HYPHEN)) {
                val parts = word.split(SOFT_HYPHEN)
                var taken = 0
                var takenText = ""
                var takenWidth = 0f
                for (k in parts.size - 1 downTo 1) {
                    val prefix = stripSoftHyphens(parts.take(k).joinToString("")) + "-"
                    val withPrefix = if (current.isEmpty()) prefix else "$current $prefix"
                    val width = metrics.measure(withPrefix, style).width
                    if (width <= maxWidth) {
                        taken = k
                        takenText = withPrefix
                        takenWidth = width
                        break
                    }
                }
                if (taken > 0) {
                    out += TextLine(takenText, takenWidth, baseline, lineHeight)
                    current = StringBuilder()
                    currentWidth = 0f
                    word = parts.drop(taken).joinToString(SOFT_HYPHEN.toString())
                    continue
                }
            }

            if (current.isEmpty()) {
                // The word alone is wider than the slot and has no usable
                // hyphen point — split it mid-word so it cannot overflow.
                val chunks = breakLongWord(display, style, maxWidth, metrics)
                for (chunk in chunks.dropLast(1)) {
                    out += TextLine(chunk, metrics.measure(chunk, style).width, baseline, lineHeight)
                }
                val tail = chunks.last()
                current = StringBuilder(tail)
                currentWidth = metrics.measure(tail, style).width
                break
            }

            // Line is non-empty and the word doesn't fit — close the line
            // and retry the word at the start of a fresh one.
            flush()
        }
    }
    flush()
}

private fun stripSoftHyphens(text: String): String =
    if (text.indexOf(SOFT_HYPHEN) >= 0) text.replace(SOFT_HYPHEN.toString(), "") else text

/**
 * Splits a word that cannot fit on a line of its own into the largest
 * chunks that do fit. Guarantees at least one character per chunk so
 * pathological slots (`maxWidth <= 0`) still make forward progress —
 * the output then simply overflows by single characters instead of
 * looping forever.
 */
private fun breakLongWord(
    word: String,
    style: TextStyle,
    maxWidth: Float,
    metrics: FontMetrics,
): List<String> {
    if (word.isEmpty()) return listOf(word)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < word.length) {
        var end = start + 1
        while (end < word.length &&
            metrics.measure(word.substring(start, end + 1), style).width <= maxWidth
        ) {
            end++
        }
        chunks += word.substring(start, end)
        start = end
    }
    return chunks
}

/**
 * Trims the end of [line] and appends `…` so the result fits inside
 * [maxWidth]. Used by [TextOverflow.Ellipsis] on the last visible line.
 */
private fun ellipsize(
    line: TextLine,
    style: TextStyle,
    maxWidth: Float,
    metrics: FontMetrics,
): TextLine {
    val ellipsis = "…"
    if (maxWidth == Float.POSITIVE_INFINITY) {
        val text = line.text + ellipsis
        return line.copy(text = text, width = metrics.measure(text, style).width)
    }
    var kept = line.text
    while (kept.isNotEmpty()) {
        val candidate = kept.trimEnd() + ellipsis
        val width = metrics.measure(candidate, style).width
        if (width <= maxWidth) return line.copy(text = candidate, width = width)
        kept = kept.dropLast(1)
    }
    return line.copy(text = ellipsis, width = metrics.measure(ellipsis, style).width)
}

/**
 * Spreads [paragraphWidth]'s leftover slack evenly between the words of
 * [line], producing per-word x-offsets the renderer can draw directly.
 * Lines with fewer than two words have no gaps to stretch and are
 * returned unchanged.
 */
private fun justifyLine(
    line: TextLine,
    paragraphWidth: Float,
    style: TextStyle,
    metrics: FontMetrics,
    direction: TextDirection,
): TextLine {
    val logicalWords = line.text.split(' ').filter { it.isNotEmpty() }
    if (logicalWords.size < 2) return line
    // RTL paragraphs place words in reverse order so the first logical
    // word hugs the right edge.
    val words = if (direction == TextDirection.Rtl) logicalWords.asReversed() else logicalWords
    val wordWidths = words.map { metrics.measure(it, style).width }
    val totalWords = wordWidths.sum()
    val gap = (paragraphWidth - totalWords) / (words.size - 1)
    if (gap <= 0f || !gap.isFinite()) return line

    var x = 0f
    val placed = words.mapIndexed { i, word ->
        val justified = JustifiedWord(text = word, x = x, width = wordWidths[i])
        x += wordWidths[i] + gap
        justified
    }
    return line.copy(width = paragraphWidth, justifiedWords = placed)
}
