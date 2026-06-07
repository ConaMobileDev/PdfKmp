package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.node.Span
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextScript
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.style.resolve
import com.conamobile.pdfkmp.unit.Sp

/**
 * Font-size multiplier applied to superscript / subscript spans. 62% is
 * the typographic sweet spot most word processors converge on — small
 * enough to read as a script, large enough to stay legible at body sizes.
 */
private const val SCRIPT_SCALE: Float = 0.62f

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
    /**
     * Vertical shift from the line's top edge, in PDF points. Non-zero
     * only for superscript / subscript segments, whose baseline is moved
     * relative to the line's dominant baseline.
     */
    val yOffset: Float = 0f,
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
    /**
     * Paragraph direction resolved against the spans' combined text. RTL
     * flips what `Start` / `End` anchor to. Segment order within a line
     * stays logical — uniform-style RTL paragraphs collapse to one
     * segment per line, which the platform text engines render correctly;
     * mixed-style RTL paragraphs may order segments LTR (documented
     * limitation).
     */
    val resolvedDirection: TextDirection = TextDirection.Ltr,
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
 *
 * Spans whose style sets [TextScript.Superscript] / [TextScript.Subscript]
 * are measured at a reduced size and receive a per-segment baseline shift;
 * [TextAlign.Justify] stretches the inter-word spaces of every line except
 * the last line of each hard paragraph.
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

    val effectiveSpans = spans.map { it.withScriptSizing() }
    val tokens = tokeniseSpans(effectiveSpans)
    val lines = mutableListOf<RichLine>()
    // Parallel to [lines]: marks lines that end a hard paragraph so the
    // justification pass can leave them start-aligned.
    val paragraphEnds = mutableListOf<Boolean>()
    val current = mutableListOf<RichSegment>()
    var currentLineWidth = 0f
    // Justification stretches the standalone space segments between words,
    // so spaces must not be merged into their neighbouring word segments.
    val keepSpacesSeparate = align == TextAlign.Justify

    fun flush(isParagraphEnd: Boolean, blankLineStyle: TextStyle = effectiveSpans.first().style) {
        if (current.isEmpty()) {
            // Emit an empty line that still carries height (for hard
            // newlines that produce a blank line). The height comes from
            // the style of the span that owns the hard break, so a blank
            // line between large spans stays proportionate.
            val style = blankLineStyle
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
                segments = current.map { it.withScriptShift(maxAscent, metrics) },
                totalWidth = currentLineWidth,
                baseline = maxAscent,
                height = maxLineHeight,
            )
        }
        paragraphEnds += isParagraphEnd
        current.clear()
        currentLineWidth = 0f
    }

    for (token in tokens) {
        if (token.hardBreak) {
            flush(isParagraphEnd = true, blankLineStyle = token.style)
            continue
        }
        val tokenWidth = if (token.text.isEmpty()) 0f else metrics.measure(token.text, token.style).width
        // Token fits on the current line — append it.
        if (currentLineWidth + tokenWidth <= maxWidth || current.isEmpty()) {
            appendOrExtendSegment(current, token, tokenWidth, xOffsetOnNewSegment = currentLineWidth, keepSpacesSeparate)
            currentLineWidth += tokenWidth
        } else {
            // Doesn't fit — start a new line. Skip leading whitespace on
            // the new line so wrapped paragraphs don't have a stray
            // indent.
            flush(isParagraphEnd = false)
            if (token.text.trim().isEmpty()) continue
            appendOrExtendSegment(current, token, tokenWidth, xOffsetOnNewSegment = 0f, keepSpacesSeparate)
            currentLineWidth += tokenWidth
        }
    }
    flush(isParagraphEnd = true)

    val paragraphWidth = if (maxWidth == Float.POSITIVE_INFINITY) {
        lines.maxOfOrNull { it.totalWidth } ?: 0f
    } else {
        maxWidth
    }
    val widest = lines.maxOfOrNull { it.totalWidth } ?: 0f
    val totalHeight = lines.sumOf { it.height.toDouble() }.toFloat()

    if (align == TextAlign.Justify) {
        for (i in lines.indices) {
            if (!paragraphEnds[i]) lines[i] = justifyRichLine(lines[i], paragraphWidth)
        }
    }

    val direction = (spans.first().style.direction)
        .resolve(spans.joinToString(separator = "") { it.text })

    return MeasuredRichText(
        lines = lines.toList(),
        align = align,
        paragraphWidth = paragraphWidth,
        // size.width is the intrinsic width (widest line); paragraphWidth
        // carries the parent's slot for non-Start alignment. Mirrors the
        // separation in plain `MeasuredText`.
        size = Size(width = widest, height = totalHeight),
        resolvedDirection = direction,
    )
}

/**
 * Applies the script-size reduction up front so wrapping and measuring see
 * the size the glyphs will actually render at. The [TextScript] marker is
 * kept on the style so [withScriptShift] can compute the baseline shift
 * once the line's dominant ascent is known.
 */
private fun Span.withScriptSizing(): Span = if (style.script == TextScript.None) {
    this
} else {
    copy(style = style.copy(fontSize = Sp(style.fontSize.value * SCRIPT_SCALE)))
}

/**
 * Computes the vertical shift that puts a script segment's baseline above
 * (superscript) or below (subscript) the line's dominant baseline. Normal
 * segments stay untouched.
 */
private fun RichSegment.withScriptShift(lineAscent: Float, metrics: FontMetrics): RichSegment =
    when (style.script) {
        TextScript.None -> this
        TextScript.Superscript, TextScript.Subscript -> {
            val ownAscent = metrics.measure(text, style).ascent
            // Shift is proportional to the already-reduced font size:
            // ~55% up reads as an exponent, ~22% down as an index.
            val baselineShift = if (style.script == TextScript.Superscript) {
                -(style.fontSize.value * 0.55f)
            } else {
                style.fontSize.value * 0.22f
            }
            copy(yOffset = (lineAscent + baselineShift) - ownAscent)
        }
    }

/**
 * Stretches the inter-word space segments of [line] so its content fills
 * [paragraphWidth] exactly. Trailing whitespace segments are dropped
 * first — they are invisible, but their width would otherwise keep the
 * last visible word short of the right margin.
 */
private fun justifyRichLine(line: RichLine, paragraphWidth: Float): RichLine {
    val lastContent = line.segments.indexOfLast { it.text.isNotBlank() }
    if (lastContent <= 0) return line
    val segments = line.segments.take(lastContent + 1)
    val visibleWidth = segments.sumOf { it.width.toDouble() }.toFloat()
    val extra = paragraphWidth - visibleWidth
    if (extra <= 0f || !extra.isFinite()) return line
    val stretchable = segments.count { it.text.isNotEmpty() && it.text.isBlank() }
    if (stretchable == 0) return line
    val perSpace = extra / stretchable

    var x = 0f
    val adjusted = segments.map { seg ->
        val stretched = seg.text.isNotEmpty() && seg.text.isBlank()
        val width = if (stretched) seg.width + perSpace else seg.width
        val moved = seg.copy(xOffset = x, width = width)
        x += width
        moved
    }
    return line.copy(segments = adjusted, totalWidth = paragraphWidth)
}

/**
 * Adds [token] to [current], merging with the previous segment when both
 * share the same [TextStyle] so the output doesn't accumulate hundreds of
 * one-character segments. When [keepSpacesSeparate] is set (justified
 * paragraphs), whitespace tokens always become their own segment so the
 * justification pass can stretch them independently.
 */
private fun appendOrExtendSegment(
    current: MutableList<RichSegment>,
    token: TokenisedSpan,
    tokenWidth: Float,
    xOffsetOnNewSegment: Float,
    keepSpacesSeparate: Boolean = false,
) {
    val last = current.lastOrNull()
    val mergeBlocked = keepSpacesSeparate &&
        (token.text.isBlank() || (last != null && last.text.isBlank()))
    if (last != null && last.style == token.style && !mergeBlocked) {
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
