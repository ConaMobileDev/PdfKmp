package com.conamobile.pdfkmp.viewer

/**
 * Controls how many rasterised page bitmaps the viewer keeps in
 * memory while scrolling, and how aggressively it prefetches pages
 * around the viewport.
 *
 * The viewer always caps total bitmap memory against a per-platform
 * budget (Android — 25 % of `Runtime.maxMemory()`, iOS — 200 MB) and
 * evicts the least-recently-used pages first when the budget is
 * exceeded. The strategy controls only the *prefetch window* — how
 * far ahead and behind the visible page the renderer tries to keep
 * pages warm. The hard memory cap means you can never crash the
 * process by asking for too large a window: the cache will simply
 * stop holding more pages than fit.
 *
 * Typical choice:
 *
 * - [Auto] — sensible defaults for most apps. The viewer prefetches
 *   a small window (a handful of pages above and below) and lets the
 *   memory budget take it from there. Pages already rendered stay
 *   warm in cache, so scrolling back to a page you visited recently
 *   is instant. Recommended default. The window is **adaptive**: for
 *   large documents (over [LARGE_DOCUMENT_THRESHOLD] pages) it
 *   tightens to a small forward-biased window so the prefetch loop
 *   never tries to budget the whole document, while small documents
 *   keep the symmetric default.
 * - [Window] — explicit knob for hosts that *know* their documents
 *   and want a wider warm window for fluency, or a narrower one to
 *   keep memory pressure low.
 * - [All] — try to keep every page warm. Useful for short documents
 *   (presentations, multi-page forms). The memory cap still applies,
 *   so big documents silently degrade to LRU behaviour.
 */
public sealed interface PdfPageCacheStrategy {

    /**
     * Default strategy. Prefetch is conservative and **adaptive to
     * document size**:
     *
     * - **Small documents** (≤ [LARGE_DOCUMENT_THRESHOLD] pages) use a
     *   symmetric window ([DEFAULT_PAGES_BEFORE] before,
     *   [DEFAULT_PAGES_AFTER] after) — scrolling either way stays warm.
     * - **Large documents** (> [LARGE_DOCUMENT_THRESHOLD] pages) tighten
     *   to a forward-biased window ([LARGE_DOCUMENT_PAGES_BEFORE] before,
     *   [LARGE_DOCUMENT_PAGES_AFTER] after). On a 1000-page manual there
     *   is no point eagerly rasterising a wide ring around the viewport:
     *   the memory budget would evict it before the user reaches it, and
     *   the wasted renders compete with the visible page for the render
     *   mutex. Biasing the window forward matches the dominant
     *   read-downward scroll direction.
     *
     * Everything beyond the window is left to the memory budget.
     * Recommended for most apps.
     */
    public object Auto : PdfPageCacheStrategy

    /**
     * Explicit warm window. The viewer tries to keep [pagesBefore]
     * pages above the visible one and [pagesAfter] below pre-
     * rasterised; the memory budget evicts the oldest entry when
     * that would overflow.
     *
     * Passing `(Int.MAX_VALUE, Int.MAX_VALUE)` is equivalent to
     * [All].
     */
    public class Window(
        public val pagesBefore: Int,
        public val pagesAfter: Int,
    ) : PdfPageCacheStrategy {
        init {
            require(pagesBefore >= 0) { "pagesBefore must be non-negative, was $pagesBefore" }
            require(pagesAfter >= 0) { "pagesAfter must be non-negative, was $pagesAfter" }
        }
    }

    /**
     * Keep every page warm if it fits in the memory budget. Useful
     * for short documents the user is likely to flip through
     * repeatedly. For long documents this degrades naturally to LRU
     * eviction.
     */
    public object All : PdfPageCacheStrategy

    public companion object {

        /** Pages prefetched above the visible page when [Auto] is in effect (small docs). */
        public const val DEFAULT_PAGES_BEFORE: Int = 3

        /** Pages prefetched below the visible page when [Auto] is in effect (small docs). */
        public const val DEFAULT_PAGES_AFTER: Int = 3

        /**
         * Page count above which [Auto] switches from the symmetric
         * small-document window to the tighter forward-biased
         * large-document window. ~200 pages is roughly where a wide
         * warm ring stops paying for itself: at 2× density an A4 page
         * is a few MB, so a couple hundred warm pages would already
         * blow past the memory budget and degrade to LRU thrash.
         */
        public const val LARGE_DOCUMENT_THRESHOLD: Int = 200

        /** Pages prefetched above the visible page when [Auto] is in effect (large docs). */
        public const val LARGE_DOCUMENT_PAGES_BEFORE: Int = 2

        /** Pages prefetched below the visible page when [Auto] is in effect (large docs). */
        public const val LARGE_DOCUMENT_PAGES_AFTER: Int = 4
    }
}

/**
 * Resolves the [Auto] prefetch window for a document of [pageCount]
 * pages. Extracted as a pure, side-effect-free function so the
 * adaptive size-selection logic can be unit-tested without a renderer
 * or a Compose composition.
 *
 * Returns `(pagesBefore, pagesAfter)` — inclusive page-index offsets
 * relative to the currently visible page. Small documents get the
 * symmetric default; documents over [PdfPageCacheStrategy.Companion.LARGE_DOCUMENT_THRESHOLD]
 * pages get the forward-biased large-document window.
 */
internal fun autoPrefetchWindow(pageCount: Int): Pair<Int, Int> =
    if (pageCount > PdfPageCacheStrategy.LARGE_DOCUMENT_THRESHOLD) {
        PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_BEFORE to
            PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_AFTER
    } else {
        PdfPageCacheStrategy.DEFAULT_PAGES_BEFORE to
            PdfPageCacheStrategy.DEFAULT_PAGES_AFTER
    }

/**
 * Resolves the prefetch window for [strategy] given the total page
 * count. Returned values are inclusive page-index offsets relative
 * to the currently visible page.
 */
internal fun PdfPageCacheStrategy.window(pageCount: Int): Pair<Int, Int> = when (this) {
    PdfPageCacheStrategy.Auto -> autoPrefetchWindow(pageCount)
    is PdfPageCacheStrategy.Window -> pagesBefore to pagesAfter
    PdfPageCacheStrategy.All -> pageCount to pageCount
}
