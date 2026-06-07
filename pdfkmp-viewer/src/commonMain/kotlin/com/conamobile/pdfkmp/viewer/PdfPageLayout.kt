package com.conamobile.pdfkmp.viewer

/**
 * How [PdfViewer] / [KmpPdfViewer] arranges pages in the scrollable list.
 *
 * Both layouts share the same zoom / pan model, search highlights, and
 * annotations — only the on-screen grouping of pages differs.
 */
public enum class PdfPageLayout {

    /**
     * One page per row, top to bottom — the default continuous-scroll
     * reading surface. Behaviour is byte-identical to the viewer before the
     * two-page mode existed.
     */
    Single,

    /**
     * Side-by-side page pairs, like an open book (best on Desktop / tablet).
     * The cover (page 1) sits alone on the first row; subsequent pages are
     * paired verso/recto — `2-3`, `4-5`, … — so the spread matches a printed
     * book. A trailing odd page sits alone on the last row.
     */
    TwoPageBook,
}

/**
 * Computes the row layout for [PdfPageLayout.TwoPageBook] given a page
 * count: a list of rows, each holding the zero-based page indices shown on
 * that row.
 *
 * The cover (index 0) is always alone so the remaining pages fall on
 * natural verso/recto spreads (`1-2`, `3-4`, … zero-based → `2-3`, `4-5`, …
 * one-based). A trailing odd page is alone on the final row. A document with
 * a single page yields one single-page row.
 *
 * Pure logic with no Compose dependency so it is unit-testable on the JVM.
 *
 * Examples (page indices, zero-based):
 * - `1` → `[[0]]`
 * - `2` → `[[0], [1]]`
 * - `3` → `[[0], [1, 2]]`
 * - `5` → `[[0], [1, 2], [3, 4]]`
 * - `6` → `[[0], [1, 2], [3, 4], [5]]`
 */
public fun bookPagePairs(pageCount: Int): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    val rows = ArrayList<List<Int>>()
    rows += listOf(0)            // cover, always alone
    var index = 1
    while (index < pageCount) {
        if (index + 1 < pageCount) {
            rows += listOf(index, index + 1)
            index += 2
        } else {
            rows += listOf(index) // trailing odd page
            index += 1
        }
    }
    return rows
}
