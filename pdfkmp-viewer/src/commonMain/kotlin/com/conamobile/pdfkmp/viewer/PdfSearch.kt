package com.conamobile.pdfkmp.viewer

import com.conamobile.pdfkmp.text.PdfTextRun

/**
 * One matched substring inside a PDF page, expressed in the same
 * coordinate system as [PdfTextRun] (PDF points, top-left origin).
 *
 * The matcher uses a linear-width approximation to slice the parent
 * text run — i.e. the matched substring's bounding box is a
 * proportional fraction of the run's full bounding box. That isn't
 * pixel-perfect for variable-width fonts (an `i` is narrower than an
 * `M`) but it's fine for a translucent highlight rectangle, where the
 * user only needs to see "the word is around here". The active match
 * gets a tighter colour treatment to draw the eye anyway.
 *
 * @property pageIndex zero-based page the match belongs to.
 * @property xPoints left edge of the match's bounding box.
 * @property yPoints top edge of the match's bounding box.
 * @property widthPoints width of the match's bounding box.
 * @property heightPoints height of the match's bounding box —
 *   inherited from the parent run.
 */
public data class PdfSearchHighlight(
    val pageIndex: Int,
    val xPoints: Float,
    val yPoints: Float,
    val widthPoints: Float,
    val heightPoints: Float,
)

/**
 * Scans every [PdfTextRun] for case-insensitive occurrences of [query]
 * and returns a sorted list of [PdfSearchHighlight]s in document
 * order (page → top-to-bottom → left-to-right).
 *
 * Returns an empty list when [query] is blank — callers don't need to
 * pre-trim their input.
 *
 * Multiple non-overlapping matches inside the same run are all
 * captured (e.g. `query = "the"` finds both occurrences in
 * `"the quick brown fox jumps over the lazy dog"`).
 */
public fun searchPdfText(
    textRuns: List<PdfTextRun>,
    query: String,
): List<PdfSearchHighlight> {
    if (query.isBlank()) return emptyList()
    val needle = query.trim()
    val matches = mutableListOf<PdfSearchHighlight>()
    for (run in textRuns) {
        if (run.text.isEmpty() || run.widthPoints <= 0f) continue
        val haystack = run.text
        var startIndex = 0
        while (true) {
            val matchStart = haystack.indexOf(
                string = needle,
                startIndex = startIndex,
                ignoreCase = true,
            )
            if (matchStart < 0) break
            val matchEnd = matchStart + needle.length
            // Linear-width approximation of the matched substring's
            // bounding box inside the parent run.
            val charWidth = run.widthPoints / haystack.length.toFloat()
            val subX = run.xPoints + matchStart * charWidth
            val subWidth = (matchEnd - matchStart) * charWidth
            matches += PdfSearchHighlight(
                pageIndex = run.pageIndex,
                xPoints = subX,
                yPoints = run.yPoints,
                widthPoints = subWidth,
                heightPoints = run.heightPoints,
            )
            startIndex = matchEnd
        }
    }
    return matches
}
