package com.conamobile.pdfkmp.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Verifies [Code128Encoder] produces correct symbol values and module patterns. */
class Code128EncoderTest {

    @Test
    fun rainbow_encodesInCodeSetB_withHandComputedChecksum() {
        // Code set B: value = ascii - 32.
        // R=50, A=33, I=41, N=46, B=34, O=47, W=55; Start B = 104.
        val r = 50; val a = 33; val i = 41; val n = 46; val b = 34; val o = 47; val w = 55
        val expectedData = listOf(104, r, a, i, n, b, o, w)

        val values = Code128Encoder.buildValueSequence("RAINBOW")
        assertEquals(expectedData, values, "Start code + data values mismatch")

        // Weighted mod-103 checksum, start has weight 1, then 1-based position weights.
        val sum = 104 + r * 1 + a * 2 + i * 3 + n * 4 + b * 5 + o * 6 + w * 7
        val expectedChecksum = sum % 103
        assertEquals(25, expectedChecksum, "Hand-computed checksum sanity check")

        // The checksum symbol is the second-to-last 11-module symbol in the output;
        // confirm its module widths equal the table entry for value 25.
        val barcode = Code128Encoder.encode("RAINBOW")
        val checksumWidths = symbolWidths(barcode, symbolIndexFromEnd = 1)
        assertEquals(tablePattern(25), checksumWidths, "Checksum symbol pattern mismatch")
    }

    @Test
    fun allDigitsEvenLength_usesCodeSetC() {
        val values = Code128Encoder.buildValueSequence("123456")
        // Start C (105) + three digit-pairs.
        assertEquals(listOf(105, 12, 34, 56), values)

        val barcode = Code128Encoder.encode("123456")
        // start + 3 data + checksum + stop = 6 symbols.
        // 5 standard symbols (11 modules) + stop (13 modules) = 55 + 13 = 68.
        assertEquals(68, barcode.totalModules)
        assertEquals(11 * 5 + 13, barcode.totalModules)
    }

    @Test
    fun mixedInput_switchesToCodeCForDigitRun() {
        // "ABC" in B, then an 8-digit run packs four pairs in C.
        // A=33, B=34, C=35; CODE C = 99; pairs 12,34,56,78.
        val values = Code128Encoder.buildValueSequence("ABC12345678")
        assertEquals(listOf(104, 33, 34, 35, 99, 12, 34, 56, 78), values)
    }

    @Test
    fun moduleList_invariants_holdForVariedInputs() {
        listOf("RAINBOW", "123456", "ABC12345678", "Hello 42 World", "9").forEach { input ->
            val barcode = Code128Encoder.encode(input)
            val modules = barcode.modules

            // Each module width is 1..4.
            modules.forEach { width ->
                assertTrue(width in 1..4, "width $width out of range for input '$input'")
            }

            // Symbol count: every symbol but the stop is 6 widths; stop adds 7.
            // total widths = 6 * (symbolCount - 1) + 7  =>  symbolCount derivable.
            assertEquals(0, (modules.size - 7) % 6, "module count not symbol-aligned for '$input'")
            val symbolCount = (modules.size - 7) / 6 + 1

            // Total modules = 11 per standard symbol + 13 for stop = 11*symbolCount + 2.
            assertEquals(
                11 * symbolCount + 2,
                barcode.totalModules,
                "totalModules mismatch for '$input'",
            )

            // Starts and ends with a bar: bars sit at even indices, so an
            // alternating sequence of even total length ends on a space — but the
            // 13-module stop carries a final terminating bar, making the count odd.
            assertEquals(1, modules.size % 2, "symbol must start and end with a bar for '$input'")
        }
    }

    @Test
    fun emptyInput_throws() {
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("") }
    }

    @Test
    fun nonAsciiInput_throws() {
        assertFailsWith<IllegalArgumentException> { Code128Encoder.encode("ä") }
    }

    /**
     * Extracts the six module widths of one 11-module standard symbol, counted
     * [symbolIndexFromEnd] symbols back from the trailing stop pattern (0 = stop,
     * 1 = checksum, ...). The stop is 7 widths; standard symbols are 6 each.
     */
    private fun symbolWidths(barcode: Code128Barcode, symbolIndexFromEnd: Int): List<Int> {
        val modules = barcode.modules
        val end = modules.size - 7 - (symbolIndexFromEnd - 1) * 6
        val start = end - 6
        return modules.subList(start, end)
    }

    /** The pattern table entry for [value] as a plain list, for comparison. */
    private fun tablePattern(value: Int): List<Int> = when (value) {
        25 -> listOf(3, 2, 1, 1, 2, 2)
        else -> error("pattern $value not needed by tests")
    }
}
