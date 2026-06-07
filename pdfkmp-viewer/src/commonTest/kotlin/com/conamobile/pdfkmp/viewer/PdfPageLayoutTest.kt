package com.conamobile.pdfkmp.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic tests for the two-page-book pairing function [bookPagePairs].
 * No Compose harness — runs on every platform's test surface.
 */
class PdfPageLayoutTest {

    @Test
    fun emptyDocumentHasNoRows() {
        assertEquals(emptyList(), bookPagePairs(0))
        assertEquals(emptyList(), bookPagePairs(-3))
    }

    @Test
    fun singlePageIsOneRowAlone() {
        assertEquals(listOf(listOf(0)), bookPagePairs(1))
    }

    @Test
    fun coverIsAlwaysAlone() {
        // Two pages: cover alone, then page 2 alone (no recto to pair with).
        assertEquals(listOf(listOf(0), listOf(1)), bookPagePairs(2))
    }

    @Test
    fun threePagesPairAfterCover() {
        // Cover alone, then a 2-3 spread (zero-based 1,2).
        assertEquals(listOf(listOf(0), listOf(1, 2)), bookPagePairs(3))
    }

    @Test
    fun fivePagesEndOnAFullSpread() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            bookPagePairs(5),
        )
    }

    @Test
    fun sixPagesLeaveATrailingOddPage() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
            bookPagePairs(6),
        )
    }

    @Test
    fun everyPageAppearsExactlyOnceInOrder() {
        // Property check across a range of counts: the flattened rows must be
        // 0..count-1 in order, with no page lost or duplicated.
        for (count in 1..40) {
            val flat = bookPagePairs(count).flatten()
            assertEquals((0 until count).toList(), flat, "pairing dropped/duped a page for count=$count")
        }
    }

    @Test
    fun noRowHoldsMoreThanTwoPages() {
        for (count in 1..40) {
            assertTrue(
                bookPagePairs(count).all { it.size <= 2 },
                "a row held more than two pages for count=$count",
            )
        }
    }
}
