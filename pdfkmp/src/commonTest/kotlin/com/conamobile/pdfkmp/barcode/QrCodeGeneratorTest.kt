package com.conamobile.pdfkmp.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural invariants for [QrCodeGenerator]. Because the chosen mask (and
 * therefore the exact module values) varies with the payload, these tests
 * assert the spec-mandated function patterns and sizes rather than hardcoded
 * full matrices.
 */
class QrCodeGeneratorTest {

    /** "HELLO WORLD" is 11 bytes; that fits version 1 (14-byte byte capacity) at EC M. */
    @Test
    fun helloWorld_M_isVersion1() {
        val matrix = QrCodeGenerator.encode("HELLO WORLD", QrErrorCorrection.M)
        assertEquals(21, matrix.size, "11 bytes at EC M must fit version 1 (size 21)")
    }

    /** Each of the three finders is a 7×7 dark border around a light ring around a 3×3 dark core. */
    @Test
    fun finderPatterns_present() {
        val matrix = QrCodeGenerator.encode("HELLO WORLD", QrErrorCorrection.M)
        val origins = listOf(0 to 0, matrix.size - 7 to 0, 0 to matrix.size - 7)
        for ((ox, oy) in origins) {
            for (dy in 0 until 7) {
                for (dx in 0 until 7) {
                    val isBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                    val isCore = dx in 2..4 && dy in 2..4
                    val expected = isBorder || isCore
                    assertEquals(
                        expected,
                        matrix[ox + dx, oy + dy],
                        "finder mismatch at offset ($dx,$dy) of finder ($ox,$oy)",
                    )
                }
            }
        }
    }

    /** The timing patterns on row 6 / column 6 alternate, starting dark at index 8. */
    @Test
    fun timingPatterns_alternate() {
        val matrix = QrCodeGenerator.encode("HELLO WORLD", QrErrorCorrection.M)
        for (i in 8 until matrix.size - 8) {
            val expectedDark = i % 2 == 0
            assertEquals(expectedDark, matrix[i, 6], "horizontal timing at x=$i")
            assertEquals(expectedDark, matrix[6, i], "vertical timing at y=$i")
        }
    }

    /** A 100-character payload needs a higher version; size must follow 17 + 4*version. */
    @Test
    fun longInput_selectsHigherVersion() {
        val matrix = QrCodeGenerator.encode("A".repeat(100), QrErrorCorrection.M)
        assertTrue(matrix.size > 21, "100 chars must exceed version 1")
        assertEquals(0, (matrix.size - 17) % 4, "size must be 17 + 4*version")
        val version = (matrix.size - 17) / 4
        assertTrue(version in 1..40, "version must be in range")
    }

    /** Degenerate inputs must encode without throwing. */
    @Test
    fun emptyAndSingleChar_encode() {
        val empty = QrCodeGenerator.encode("", QrErrorCorrection.M)
        assertEquals(21, empty.size)
        val single = QrCodeGenerator.encode("A", QrErrorCorrection.M)
        assertEquals(21, single.size)
    }

    /** Every EC level encodes a realistic URL into a square matrix of valid size. */
    @Test
    fun allEcLevels_encodeUrl() {
        val url = "https://github.com/conamobiledev/PdfKmp"
        for (ec in QrErrorCorrection.entries) {
            val matrix = QrCodeGenerator.encode(url, ec)
            assertEquals(0, (matrix.size - 17) % 4, "EC $ec: size must be 17 + 4*version")
            val version = (matrix.size - 17) / 4
            assertTrue(version in 1..40, "EC $ec: version $version out of range")
            // Confirm it is genuinely square and indexable at the far corner.
            matrix[matrix.size - 1, matrix.size - 1]
        }
    }
}
