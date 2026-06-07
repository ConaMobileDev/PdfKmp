package com.conamobile.pdfkmp.viewer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the prefetch-window resolution in
 * [PdfPageCacheStrategy], focused on the **adaptive [Auto]** logic:
 * small documents keep the symmetric default window, large documents
 * (over [PdfPageCacheStrategy.LARGE_DOCUMENT_THRESHOLD] pages) tighten
 * to the forward-biased large-document window. Pure logic, so it runs
 * in the fast jvmTest surface with no renderer or composition.
 */
class PdfPageCacheStrategyTest {

    @Test
    fun auto_smallDocument_usesSymmetricDefaultWindow() {
        val (before, after) = PdfPageCacheStrategy.Auto.window(pageCount = 10)
        assertEquals(PdfPageCacheStrategy.DEFAULT_PAGES_BEFORE, before)
        assertEquals(PdfPageCacheStrategy.DEFAULT_PAGES_AFTER, after)
    }

    @Test
    fun auto_atThreshold_stillUsesSmallWindow() {
        // The switch is strictly ">" the threshold, so a document of
        // exactly LARGE_DOCUMENT_THRESHOLD pages stays on the small window.
        val (before, after) =
            PdfPageCacheStrategy.Auto.window(PdfPageCacheStrategy.LARGE_DOCUMENT_THRESHOLD)
        assertEquals(PdfPageCacheStrategy.DEFAULT_PAGES_BEFORE, before)
        assertEquals(PdfPageCacheStrategy.DEFAULT_PAGES_AFTER, after)
    }

    @Test
    fun auto_largeDocument_usesForwardBiasedWindow() {
        val (before, after) =
            PdfPageCacheStrategy.Auto.window(PdfPageCacheStrategy.LARGE_DOCUMENT_THRESHOLD + 1)
        assertEquals(PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_BEFORE, before)
        assertEquals(PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_AFTER, after)
    }

    @Test
    fun auto_hugeDocument_windowStaysBounded() {
        // The whole point: a 1000-page doc must NOT widen toward the
        // page count the way All would — the window stays the small
        // forward-biased one regardless of how many pages there are.
        val (before, after) = PdfPageCacheStrategy.Auto.window(pageCount = 1000)
        assertEquals(PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_BEFORE, before)
        assertEquals(PdfPageCacheStrategy.LARGE_DOCUMENT_PAGES_AFTER, after)
    }

    @Test
    fun autoPrefetchWindow_matchesAutoStrategy() {
        // The Auto strategy delegates to the pure helper; assert they
        // agree so the extracted function stays the single source of truth.
        for (count in intArrayOf(0, 1, 50, 200, 201, 5000)) {
            assertEquals(
                autoPrefetchWindow(count),
                PdfPageCacheStrategy.Auto.window(count),
                "Auto.window($count) must match autoPrefetchWindow($count)",
            )
        }
    }

    @Test
    fun window_explicitWindow_isPassedThroughVerbatim() {
        val strategy = PdfPageCacheStrategy.Window(pagesBefore = 1, pagesAfter = 7)
        // Explicit windows ignore the page count entirely.
        assertEquals(1 to 7, strategy.window(pageCount = 9999))
        assertEquals(1 to 7, strategy.window(pageCount = 2))
    }

    @Test
    fun window_all_expandsToFullDocument() {
        assertEquals(42 to 42, PdfPageCacheStrategy.All.window(pageCount = 42))
    }
}
