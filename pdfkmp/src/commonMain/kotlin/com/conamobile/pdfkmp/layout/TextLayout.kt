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
        // mid-word breaks), so loop until its remainder is consumed. When a
        // hyphenation dictionary is set and the word carries no explicit soft
        // hyphens, salt the dictionary's break points in as soft hyphens so the
        // existing soft-hyphen path renders a visible '-' at any break it takes.
        var word = hyphenateWord(rawWord, style)
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
 * Returns [rawWord] with the active [TextStyle.hyphenation] dictionary's break
 * points inserted as soft hyphens, or [rawWord] unchanged when no dictionary is
 * set or the word already carries explicit soft hyphens.
 *
 * Funnelling automatic breaks through the soft-hyphen marker lets the wrapper
 * treat author-supplied and dictionary-supplied breaks identically — both
 * surface a visible `-` at the line end and vanish otherwise — so enabling
 * hyphenation changes only *which* breaks exist, never how they render.
 */
private fun hyphenateWord(rawWord: String, style: TextStyle): String {
    val patterns = style.hyphenation ?: return rawWord
    // Respect explicit author breaks: never second-guess a word that already
    // declares its own soft hyphens.
    if (rawWord.indexOf(SOFT_HYPHEN) >= 0) return rawWord
    val breaks = patterns.hyphenate(rawWord)
    if (breaks.isEmpty()) return rawWord

    val sb = StringBuilder(rawWord.length + breaks.size)
    var nextBreak = 0
    for (i in rawWord.indices) {
        if (nextBreak < breaks.size && breaks[nextBreak] == i) {
            sb.append(SOFT_HYPHEN)
            nextBreak++
        }
        sb.append(rawWord[i])
    }
    return sb.toString()
}

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
        var end = start + codePointLengthAt(word, start)
        while (end < word.length) {
            val next = end + codePointLengthAt(word, end)
            if (metrics.measure(word.substring(start, next), style).width > maxWidth) break
            end = next
        }
        chunks += word.substring(start, end)
        start = end
    }
    return chunks
}

private fun codePointLengthAt(text: String, index: Int): Int =
    if (index < text.length && text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1

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
        kept = kept.dropLastCodePoint()
    }
    return line.copy(text = ellipsis, width = metrics.measure(ellipsis, style).width)
}

private fun String.dropLastCodePoint(): String =
    if (length >= 2 && this[length - 1].isLowSurrogate() && this[length - 2].isHighSurrogate()) {
        dropLast(2)
    } else {
        dropLast(1)
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
    val ordered = if (direction == TextDirection.Rtl) logicalWords.asReversed() else logicalWords

    // Kashida: for justified cursive RTL lines, soak up part of the slack by
    // elongating words at joining points before spreading the rest into gaps.
    val rawSlack = paragraphWidth - ordered.sumOf { metrics.measure(it, style).width.toDouble() }.toFloat()
    val words = if (style.kashidaJustify && direction == TextDirection.Rtl && rawSlack > 0f) {
        elongateWithKashida(ordered, rawSlack, style, metrics)
    } else {
        ordered
    }

    val wordWidths = words.map { metrics.measure(it, style).width }
    val totalWords = wordWidths.sum()
    val gap = (paragraphWidth - totalWords) / (words.size - 1)
    if (gap <= 0f || !gap.isFinite()) {
        // Kashida already consumed the slack (or there is none left): pack the
        // elongated words flush so the line still fills the slot exactly.
        if (words !== ordered) {
            var x = 0f
            val placed = words.mapIndexed { i, word ->
                val w = JustifiedWord(text = word, x = x, width = wordWidths[i])
                x += wordWidths[i]
                w
            }
            return line.copy(width = paragraphWidth, justifiedWords = placed)
        }
        return line
    }

    var x = 0f
    val placed = words.mapIndexed { i, word ->
        val justified = JustifiedWord(text = word, x = x, width = wordWidths[i])
        x += wordWidths[i] + gap
        justified
    }
    return line.copy(width = paragraphWidth, justifiedWords = placed)
}

/** Tatweel / kashida elongation character; stretches cursive Arabic joins. */
private const val TATWEEL: Char = 'ـ'

/**
 * Returns [words] with U+0640 (tatweel) characters inserted at cursive joining
 * points to absorb up to half of [slack], leaving the remainder for inter-word
 * gaps. At most two tatweels are placed per joining position; positions are
 * filled round-robin across the line so elongation spreads evenly rather than
 * piling onto one word.
 *
 * This is a deliberate approximation of true Arabic kashida justification (which
 * favours specific letters and adapts per font): we widen wherever a cursive
 * join exists and let the platform shaper stretch the tatweels into the line.
 */
private fun elongateWithKashida(
    words: List<String>,
    slack: Float,
    style: TextStyle,
    metrics: FontMetrics,
): List<String> {
    val tatweelWidth = metrics.measure(TATWEEL.toString(), style).width
    if (tatweelWidth <= 0f || !tatweelWidth.isFinite()) return words

    // Gather every legal insertion point as (wordIndex, charIndex-after).
    data class Slot(val word: Int, val after: Int)
    val slots = ArrayList<Slot>()
    words.forEachIndexed { wi, w ->
        for (after in kashidaPositions(w)) slots += Slot(wi, after)
    }
    if (slots.isEmpty()) return words

    // Reserve gaps for some of the slack; spend at most half on tatweels so the
    // word spacing never collapses entirely.
    val budget = slack * 0.5f
    val maxTatweels = (budget / tatweelWidth).toInt().coerceAtMost(slots.size * 2)
    if (maxTatweels <= 0) return words

    // Count tatweels per slot, capped at two each, filled round-robin.
    val perSlot = IntArray(slots.size)
    var placed = 0
    var pass = 0
    while (placed < maxTatweels && pass < 2) {
        for (s in slots.indices) {
            if (placed >= maxTatweels) break
            perSlot[s]++
            placed++
        }
        pass++
    }

    // Rebuild each word, inserting the assigned tatweels after each slot's char.
    val builders = words.map { StringBuilder(it) }
    // Insert from the highest char index first so earlier indices stay valid.
    for (s in slots.indices.reversed()) {
        val n = perSlot[s]
        if (n == 0) continue
        val slot = slots[s]
        builders[slot.word].insert(slot.after + 1, TATWEEL.toString().repeat(n))
    }
    return builders.map { it.toString() }
}

/**
 * Returns the char indices in [word] *after which* a tatweel may be inserted —
 * positions where two adjacent Arabic letters form a cursive join (the left
 * letter joins forward and the right letter joins backward).
 *
 * Uses a compact joining-class table for the base Arabic block (U+0621–U+064A)
 * plus the common Persian/Urdu extras, mirroring the JVM shaper's classes:
 * dual-joining letters connect on both sides; right-joining letters only connect
 * to a preceding letter (so a join can land *after* them but never *before*).
 */
internal fun kashidaPositions(word: String): List<Int> {
    if (word.length < 2) return emptyList()
    val result = ArrayList<Int>()
    for (i in 0 until word.length - 1) {
        val left = arabicJoining(word[i])
        val right = arabicJoining(word[i + 1])
        // A cursive connection needs the left letter to join forward (only
        // dual-joining letters do) and the right letter to accept a join from
        // before (dual- or right-joining letters do).
        if (left == ArabicJoin.DUAL && (right == ArabicJoin.DUAL || right == ArabicJoin.RIGHT)) {
            result += i
        }
    }
    return result
}

/** Cursive joining class of an Arabic letter; `null` for non-joining chars. */
private enum class ArabicJoin { DUAL, RIGHT }

/**
 * Joining class for [ch] over the base Arabic block and common Persian/Urdu
 * additions. Returns `null` for diacritics, punctuation, and any non-Arabic
 * character. Kept deliberately small — it only needs to spot joining pairs, not
 * shape glyphs (the platform backends do that).
 */
private fun arabicJoining(ch: Char): ArabicJoin? = when (ch.code) {
    // Right-joining: ALEF family, DAL, THAL, REH, ZAIN, WAW, TEH MARBUTA,
    // HAMZA carriers that never join forward, plus Persian JEH.
    0x0621, 0x0622, 0x0623, 0x0624, 0x0625, 0x0627,
    0x0629, 0x062F, 0x0630, 0x0631, 0x0632, 0x0648, 0x0698,
    -> ArabicJoin.RIGHT
    // Dual-joining: the connecting consonants 0x0626 (YEH HAMZA), BEH, TEH..KHAH,
    // SEEN..GHAIN, FEH..HEH, ALEF MAKSURA..YEH, and the Persian/Urdu PEH, TCHEH,
    // GAF, KEHEH, FARSI YEH.
    0x0626, 0x0628,
    in 0x062A..0x062E, in 0x0633..0x063A,
    in 0x0641..0x0647, in 0x0649..0x064A,
    0x067E, 0x0686, 0x06AF, 0x06A9, 0x06CC,
    -> ArabicJoin.DUAL
    else -> null
}
