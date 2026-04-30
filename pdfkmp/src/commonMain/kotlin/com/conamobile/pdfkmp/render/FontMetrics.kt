package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.style.TextStyle

/**
 * Resolved metrics for one piece of laid-out text.
 *
 * All distances are in PDF points and all values are positive (ascent grows
 * up from the baseline, descent grows down).
 */
public data class TextMetrics(
    /** Advance width of the measured glyph run in PDF points. */
    val width: Float,
    /** Distance from the baseline to the top of the tallest glyph. */
    val ascent: Float,
    /** Distance from the baseline to the bottom of the lowest glyph. */
    val descent: Float,
    /** Typographic line gap defined by the font; zero if the font omits it. */
    val lineGap: Float = 0f,
) {
    /** Total height occupied by one line of this run. */
    val lineHeight: Float get() = ascent + descent + lineGap
}

/**
 * Platform abstraction over text measurement.
 *
 * The layout engine consults a [FontMetrics] before any drawing occurs to
 * decide where lines wrap and how tall each line is. Implementations are
 * thread-safe enough for sequential layout — they are not required to be
 * safe for concurrent use across threads.
 *
 * Implementations live in `androidMain` (`Paint`-backed) and `iosMain`
 * (`NSAttributedString`-backed); a fake implementation lives in `commonTest`
 * for layout unit tests.
 */
public interface FontMetrics {

    /**
     * Measures [text] rendered with [style] and returns its dimensions. The
     * result must agree with what the matching [PdfCanvas.drawText] call would
     * actually produce, otherwise wrapping calculations drift away from the
     * rendered output.
     */
    public fun measure(text: String, style: TextStyle): TextMetrics
}
