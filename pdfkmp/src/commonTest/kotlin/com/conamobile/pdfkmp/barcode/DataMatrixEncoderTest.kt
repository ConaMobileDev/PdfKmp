package com.conamobile.pdfkmp.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Structural and known-answer tests for [DataMatrixEncoder]. The chosen symbol
 * size and the finder/timing patterns are deterministic, so those are asserted
 * exactly; the data-region contents are validated through known ASCII codeword
 * vectors plus the Reed-Solomon parity for a published example.
 */
class DataMatrixEncoderTest {

    /** ASCII encodation: a non-digit byte `b` becomes `b + 1`. */
    @Test
    fun asciiEncodation_singleBytes() {
        // 'A' = 65 → 66.
        assertEquals(intArrayOf(66).toList(), DataMatrixEncoder.encodeAscii("A".encodeToByteArray()).toList())
    }

    /** ASCII encodation: a digit pair packs into 10*d1 + d2 + 130. */
    @Test
    fun asciiEncodation_digitPairs() {
        // "12" → 10*1 + 2 + 130 = 142.
        assertEquals(intArrayOf(142).toList(), DataMatrixEncoder.encodeAscii("12".encodeToByteArray()).toList())
        // "123456" → 142, 164 (10*3+4+130), 186 (10*5+6+130).
        assertEquals(
            intArrayOf(142, 164, 186).toList(),
            DataMatrixEncoder.encodeAscii("123456".encodeToByteArray()).toList(),
        )
    }

    /**
     * Known-answer: the canonical "123456" example encodes to data codewords
     * [142, 164, 186] and the 10×10 symbol carries 3 data + 5 EC codewords; the
     * five Reed-Solomon codewords for [142, 164, 186] are [114, 25, 5, 88, 102].
     */
    @Test
    fun knownAnswer_123456_reedSolomon() {
        val data = DataMatrixEncoder.encodeAscii("123456".encodeToByteArray())
        assertEquals(intArrayOf(142, 164, 186).toList(), data.toList())
        val ec = DataMatrixEncoder.reedSolomon(intArrayOf(142, 164, 186), 5)
        assertEquals(intArrayOf(114, 25, 5, 88, 102).toList(), ec.toList(), "ECC 200 RS parity mismatch")
    }

    /** "123456" fits the smallest 10×10 square symbol. */
    @Test
    fun smallPayload_uses10x10() {
        val matrix = DataMatrixEncoder.encode("123456")
        assertEquals(10, matrix.size)
    }

    /** The finder "L" — solid left column and solid bottom row of the symbol. */
    @Test
    fun finderPattern_solidLAndBottom() {
        val matrix = DataMatrixEncoder.encode("123456")
        val n = matrix.size
        for (y in 0 until n) assertTrue(matrix[0, y], "left finder column must be solid at y=$y")
        for (x in 0 until n) assertTrue(matrix[x, n - 1], "bottom finder row must be solid at x=$x")
    }

    /** Timing tracks — alternating top row and right column. */
    @Test
    fun timingPattern_alternates() {
        val matrix = DataMatrixEncoder.encode("123456")
        val n = matrix.size
        // Top row: dark at even x, the standard timing phase.
        for (x in 0 until n) assertEquals(x % 2 == 0, matrix[x, 0], "top timing at x=$x")
        // Right column: dark at odd y.
        for (y in 0 until n) assertEquals(y % 2 == 1, matrix[n - 1, y], "right timing at y=$y")
    }

    /** A larger payload selects a larger square symbol of valid even size. */
    @Test
    fun largerPayload_selectsLargerSymbol() {
        val matrix = DataMatrixEncoder.encode("HELLO WORLD, THIS IS A LONGER DATA MATRIX PAYLOAD 12345")
        assertTrue(matrix.size > 10, "longer payload must exceed 10x10")
        assertEquals(0, matrix.size % 2, "square symbol sizes are even")
    }

    /** Empty input and bytes above 127 are rejected. */
    @Test
    fun invalidInput_throws() {
        assertFailsWith<IllegalArgumentException> { DataMatrixEncoder.encode("") }
        assertFailsWith<IllegalArgumentException> { DataMatrixEncoder.encode("é") } // U+00E9 → > 127.
    }

    /** A payload too large for the 52×52 symbol is rejected. */
    @Test
    fun oversizePayload_throws() {
        assertFailsWith<IllegalArgumentException> { DataMatrixEncoder.encode("A".repeat(500)) }
    }
}
