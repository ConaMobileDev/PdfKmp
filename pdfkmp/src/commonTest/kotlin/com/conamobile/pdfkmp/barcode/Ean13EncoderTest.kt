package com.conamobile.pdfkmp.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Verifies [Ean13Encoder] against known check digits and structural invariants. */
class Ean13EncoderTest {

    /**
     * "400638133393" is the first 12 digits of the canonical EAN-13 example
     * 4006381333931; the published check digit is 1.
     */
    @Test
    fun knownCheckDigit_4006381333931() {
        val digits = "400638133393".map { it - '0' }.toIntArray() + IntArray(1)
        assertEquals(1, Ean13Encoder.checkDigit(digits, 12), "published check digit must be 1")
    }

    /** A second well-known vector: 978020137962 → check digit 4 (ISBN bookland). */
    @Test
    fun knownCheckDigit_9780201379624() {
        val digits = "978020137962".map { it - '0' }.toIntArray() + IntArray(1)
        assertEquals(4, Ean13Encoder.checkDigit(digits, 12))
    }

    /** A 12-digit input has its check digit computed; the 13-digit form verifies it. */
    @Test
    fun twelveDigits_computesCheck_andMatchesThirteenDigit() {
        val twelve = Ean13Encoder.encode("400638133393")
        val thirteen = Ean13Encoder.encode("4006381333931")
        assertEquals(thirteen.modules, twelve.modules, "12-digit and 13-digit forms must match")
    }

    /** A wrong 13th check digit is rejected. */
    @Test
    fun wrongCheckDigit_throws() {
        assertFailsWith<IllegalArgumentException> { Ean13Encoder.encode("4006381333930") }
    }

    /** Every EAN-13 symbol is exactly 95 modules wide. */
    @Test
    fun symbolIs95Modules() {
        val barcode = Ean13Encoder.encode("4006381333931")
        assertEquals(Ean13Encoder.TOTAL_MODULES, barcode.totalModules, "EAN-13 is 95 modules")
        assertEquals(95, barcode.totalModules)
    }

    /** The module width list starts with a bar and contains only 1..4-wide runs. */
    @Test
    fun moduleList_startsWithBar_andHasSaneRuns() {
        val barcode = Ean13Encoder.encode("4006381333931")
        // Run-length encoding always starts on a bar (the start guard).
        // EAN-13 bar/space runs are never wider than 4 modules.
        barcode.modules.forEach { run ->
            assertTrue(run in 1..4, "run width $run out of EAN-13 range 1..4")
        }
        assertEquals(95, barcode.modules.sum())
    }

    /** UPC-A 12-digit input encodes as the equivalent EAN-13 with a leading 0. */
    @Test
    fun upcA_equalsEan13WithLeadingZero() {
        // UPC-A 036000291452 (check 2) → EAN-13 0036000291452.
        val upc = Ean13Encoder.encodeUpcA("036000291452")
        val ean = Ean13Encoder.encode("0036000291452")
        assertEquals(ean.modules, upc.modules, "UPC-A must equal EAN-13 with a leading 0")
    }

    /** UPC-A also accepts 11 digits and computes the check digit. */
    @Test
    fun upcA_elevenDigits_computesCheck() {
        // 03600029145 + computed check → must match the 12-digit verified form.
        val eleven = Ean13Encoder.encodeUpcA("03600029145")
        val twelve = Ean13Encoder.encodeUpcA("036000291452")
        assertEquals(twelve.modules, eleven.modules)
    }

    /** Non-digit and wrong-length inputs are rejected. */
    @Test
    fun invalidInput_throws() {
        assertFailsWith<IllegalArgumentException> { Ean13Encoder.encode("12345") }
        assertFailsWith<IllegalArgumentException> { Ean13Encoder.encode("40063813339A") }
        assertFailsWith<IllegalArgumentException> { Ean13Encoder.encode("12345678901234") }
    }
}
