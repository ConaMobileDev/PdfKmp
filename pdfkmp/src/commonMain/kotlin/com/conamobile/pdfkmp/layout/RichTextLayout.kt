package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.node.Span
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Sp

/**
 * One segment of a wrapped rich-text line. Multiple segments make up a
 * [RichLine]; each segment carries its own style so the renderer can
 * issue a single `drawText` call per segment without losing the per-span
 * formatting.
 */
public data class RichSegment(
    val text: String,
    val style: TextStyle,
    /** Distance from the start of the line to this segment's left edge. */
    val xOffset: Float,
    /** Advance width of [text] at [style]. */
    val width: Float,
)

/** One wrapped line of a [RichTextNode] paragraph. */
public data class RichLine(
    val segments: List<RichSegment>,
    /** Sum of every segment's [RichSegment.width]. */
    val totalWidth: Float,
    /** Distance from the line top to the dominant baseline. */
    val baseline: Float,
    /** Total height of the line including ascent + descent + line gap. */
    val height: Float,
)

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.RichTextNode].
 *
 * Mirrors [MeasuredText] but every line carries its own styled segments.
 * Renderer treats this almost identically to [MeasuredText] except that
 * it issues one `drawText` per segment instead of one per line.
 */
public data class MeasuredRichText(
    val lines: List<RichLine>,
    val align: TextAlign,
    val paragraphWidth: Float,
    override val size: Size,
) : MeasuredNode

/**
 * Word-wraps the supplied [spans] across [maxWidth].
 *
 * The algorithm flattens every span into a stream of `(word, style)`
 * tokens (preserving spaces as zero-width separators), then runs the
 * same first-fit greedy wrapper as plain text — but with per-token
 * style awareness. When a line break happens inside a span, the span is
 * physically split so each line records the slice of text it contains.
 *
 * Hard newlines (`\n`) inside a span text always force a break.
 */
public fun layoutRichText(
    spans: List<Span>,
    maxWidth: Float,
    align: TextAlign,
    paragraphLineHeight: Sp,
    metrics: FontMetrics,
): MeasuredRichText {
    if (spans.isEmpty()) {
        val width = if (maxWidth == Float.POSITIVE_INFINITY) 0f else maxWidth
        return MeasuredRichText(
            lines = emptyList(),
            align = align,
            paragraphWidth = width,
            size = Size(width = width, height = 0f),
        )
    }

    val tokens = tokeniseSpans(spans)
    val lines = mutableListOf<RichLine>()
    val current = mutableListOf<RichSegment>()
    var currentLineWidth = 0f

    fun flush() {
        if (current.isEmpty()) {
            // Emit an empty line that still carries height (for hard
            // newlines that produce a blank line).
            val style = spans.first().style
            val sample = metrics.measure("Hg", style)
            val lineHeight = if (paragraphLineHeight.value > 0f) paragraphLineHeight.value
            else sample.lineHeight
            lines += RichLine(
                segments = emptyList(),
                totalWidth = 0f,
                baseline = sample.ascent,
                height = lineHeight,
            )
        } else {
            val maxAscent = current.maxOf { metrics.measure(it.text, it.style).ascent }
            val maxLineHeight = if (paragraphLineHeight.value > 0f) paragraphLineHeight.value
            else current.maxOf { metrics.measure(it.text, it.style).lineHeight }
            lines += RichLine(
                segments = current.toList(),
                totalWidth = currentLineWidth,
                baseline = maxAscent,
                height = maxLineHeight,
            )
        }
        current.clear()
        currentLineWidth = 0f
    }

    for (token in tokens) {
        if (token.hardBreak) {
            flush()
            continue
        }
        val tokenWidth = if (token.text.isEmpty()) 0f else metrics.measure(token.text, token.style).width
        // Token fits on the current line — append it.
        if (currentLineWidth + tokenWidth <= maxWidth || current.isEmpty()) {
            appendOrExtendSegment(current, token, tokenWidth, xOffsetOnNewSegment = currentLineWidth)
            currentLineWidth += tokenWidth
        } else {
            // Doesn't fit — start a new line. Skip leading whitespace on
            // the new line so wrapped paragraphs don't have a stray
            // indent.
            flush()
            if (token.text.trim().isEmpty()) continue
            appendOrExtendSegment(current, token, tokenWidth, xOffsetOnNewSegment = 0f)
            currentLineWidth += tokenWidth
        }
    }
    flush()

    val paragraphWidth = if (maxWidth == Float.POSITIVE_INFINITY) {
        lines.maxOfOrNull { it.totalWidth } ?: 0f
    } else {
        maxWidth
    }
    val widest = lines.maxOfOrNull { it.totalWidth } ?: 0f
    val totalHeight = lines.sumOf { it.height.toDouble() }.toFloat()

    return MeasuredRichText(
        lines = lines.toList(),
        align = align,
        paragraphWidth = paragraphWidth,
        // size.width is the intrinsic width (widest line); paragraphWidth
        // carries the parent's slot for non-Start alignment. Mirrors the
        // separation in plain `MeasuredText`.
        size = Size(width = widest, height = totalHeight),
    )
}

/**
 * Adds [token] to [current], merging with the previous segment when both
 * share the same [TextStyle] so the output doesn't accumulate hundreds of
 * one-character segments.
 */
private fun appendOrExtendSegment(
    current: MutableList<RichSegment>,
    token: TokenisedSpan,
    tokenWidth: Float,
    xOffsetOnNewSegment: Float,
) {
    val last = current.lastOrNull()
    if (last != null && last.style == token.style) {
        current[current.lastIndex] = last.copy(
            text = last.text + token.text,
            width = last.width + tokenWidth,
        )
    } else {
        current += RichSegment(
            text = token.text,
            style = token.style,
            xOffset = xOffsetOnNewSegment,
            width = tokenWidth,
        )
    }
}

/**
 * One unit consumed by the rich-text wrapper: either a measured word, a
 * measured space, or a hard line break sentinel.
 */
private sealed class TokenisedSpan {
    abstract val text: String
    abstract val style: TextStyle
    val hardBreak: Boolean get() = this is HardBreak

    data class Word(
        override val text: String,
        override val style: TextStyle,
        val width: Float,
    ) : TokenisedSpan()

    data class HardBreak(override val style: TextStyle) : TokenisedSpan() {
        override val text: String = ""
    }
}

/**
 * Splits every span into the granular tokens the wrapper expects: words
 * (non-empty, no whitespace), spaces (literal `' '` characters preserved
 * so the line keeps its inter-word gaps), and hard-break sentinels for
 * `\n`.
 */
private fun tokeniseSpans(spans: List<Span>): List<TokenisedSpan> {
    val out = mutableListOf<TokenisedSpan>()
    for (span in spans) {
        val text = span.text
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                out += TokenisedSpan.HardBreak(span.style)
                i++
            } else if (c == ' ' || c == '\t') {
                // Preserve as a "word" so the wrapper treats it like any
                // other measurable token. Width will be measured by the
                // caller; we keep the literal character so spaces survive
                // through to drawText.
                out += TokenisedSpan.Word(c.toString(), span.style, width = 0f)
                i++
            } else {
                val end = i + (text.substring(i).indexOfAny(charArrayOf(' ', '\t', '\n')).takeIf { it >= 0 } ?: (text.length - i))
                val word = text.substring(i, end)
                out += TokenisedSpan.Word(word, span.style, width = 0f)
                i = end
            }
        }
    }
    return out
}
