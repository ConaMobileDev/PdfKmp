package com.conamobile.pdfkmp.image

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The shared sub-sample arithmetic every backend with a real decoder routes
 * through. Refusing an oversized image on one platform while sampling it on
 * another would make one document produce two different sets of pages, so the
 * factor has to come from one place and be exact.
 */
class DecodeBudgetTest {

    @AfterTest
    fun restoreDefault() {
        PdfImagePolicy.maxDecodePixels = PdfImagePolicy.DEFAULT_MAX_DECODE_PIXELS
    }

    @Test
    fun imageInsideTheBudget_isNotSampled() {
        assertEquals(1, decodeSampleFactorFor(4000, 3000))
    }

    @Test
    fun dimensionBomb_collapsesToAHarmlessBuffer() {
        val sample = decodeSampleFactorFor(50_000, 50_000)
        assertTrue(sample > 1, "2.5 gigapixels must be sampled down, got factor $sample")
        val decodedPixels = (50_000L / sample) * (50_000L / sample)
        assertTrue(
            decodedPixels <= PdfImagePolicy.DEFAULT_MAX_DECODE_PIXELS,
            "sampled decode is still $decodedPixels pixels",
        )
    }

    @Test
    fun sampleFactorIsAlwaysAPowerOfTwo() {
        // Android's inSampleSize rounds non-powers of two *down* to the nearest
        // power of two, which would silently under-sample and blow the budget.
        for (edge in listOf(9_000, 20_000, 50_000, 200_000)) {
            val sample = decodeSampleFactorFor(edge, edge)
            assertEquals(0, sample and (sample - 1), "factor $sample for ${edge}px is not a power of two")
        }
    }

    @Test
    fun extremeDimensions_terminateAtTheFactorCeiling() {
        // The doubling loop must be bounded: a header may claim Int.MAX_VALUE
        // on both axes, and an unbounded factor would overflow to a negative.
        val sample = decodeSampleFactorFor(Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(sample > 0, "factor overflowed to $sample")
    }

    @Test
    fun edgeBound_matchesTheSampleFactor() {
        // iOS bounds the longest edge instead of taking a factor; the two
        // spellings must describe the same decode.
        val sample = decodeSampleFactorFor(50_000, 25_000)
        assertEquals(50_000 / sample, maxDecodeEdgeFor(50_000, 25_000))
    }

    @Test
    fun raisingTheBudget_stopsSamplingALegitimatelyHugeScan() {
        // A0 at 300 DPI is ~139 MP — real input, above the untrusted default.
        assertTrue(decodeSampleFactorFor(9_930, 14_040) > 1)
        PdfImagePolicy.maxDecodePixels = 200_000_000L
        assertEquals(1, decodeSampleFactorFor(9_930, 14_040))
    }

    @Test
    fun budgetMustBePositive() {
        assertFailsWith<IllegalArgumentException> { PdfImagePolicy.maxDecodePixels = 0L }
        assertFailsWith<IllegalArgumentException> { PdfImagePolicy.maxDecodePixels = -1L }
    }
}
