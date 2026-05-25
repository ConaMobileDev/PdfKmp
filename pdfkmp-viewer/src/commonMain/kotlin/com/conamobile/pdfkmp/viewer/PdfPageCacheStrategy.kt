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
 *   is instant. Recommended default.
 * - [Window] — explicit knob for hosts that *know* their documents
 *   and want a wider warm window for fluency, or a narrower one to
 *   keep memory pressure low.
 * - [All] — try to keep every page warm. Useful for short documents
 *   (presentations, multi-page forms). The memory cap still applies,
 *   so big documents silently degrade to LRU behaviour.
 */
public sealed interface PdfPageCacheStrategy {

    /**
     * Default strategy. Prefetch is conservative
     * ([DEFAULT_PAGES_BEFORE] before, [DEFAULT_PAGES_AFTER] after);
     * everything else is left to the memory budget. Recommended for
     * most apps.
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

        /** Pages prefetched above the visible page when [Auto] is in effect. */
        public const val DEFAULT_PAGES_BEFORE: Int = 3

        /** Pages prefetched below the visible page when [Auto] is in effect. */
        public const val DEFAULT_PAGES_AFTER: Int = 3
    }
}

/**
 * Resolves the prefetch window for [strategy] given the total page
 * count. Returned values are inclusive page-index offsets relative
 * to the currently visible page.
 */
internal fun PdfPageCacheStrategy.window(pageCount: Int): Pair<Int, Int> = when (this) {
    PdfPageCacheStrategy.Auto -> PdfPageCacheStrategy.DEFAULT_PAGES_BEFORE to
        PdfPageCacheStrategy.DEFAULT_PAGES_AFTER
    is PdfPageCacheStrategy.Window -> pagesBefore to pagesAfter
    PdfPageCacheStrategy.All -> pageCount to pageCount
}
