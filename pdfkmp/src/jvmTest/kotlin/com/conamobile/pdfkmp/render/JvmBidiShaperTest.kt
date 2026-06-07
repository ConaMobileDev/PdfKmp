package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.pdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the JVM-only bidi + Arabic shaping pass.
 *
 * RTL strings are written with explicit `\u` escapes so the assertions are
 * unambiguous regardless of the host's source-file encoding, and so the
 * expected presentation-form code points are visible inline.
 */
class JvmBidiShaperTest {

    // Hebrew letters.
    private val shin = 'ש'
    private val lamed = 'ל'
    private val vav = 'ו'
    private val finalMem = 'ם'

    // "שלום" (shalom) in logical order.
    private val shalom = "$shin$lamed$vav$finalMem"

    @Test
    fun latinPassesThroughUnchanged() {
        val input = "Hello, World 123!"
        // No RTL characters → returned by identity (zero-cost fast path).
        assertEquals(input, JvmBidiShaper.process(input))
    }

    @Test
    fun hebrewIsFullyReversed() {
        // A pure-RTL run is emitted back-to-front for visual LTR drawing.
        val expected = "$finalMem$vav$lamed$shin"
        assertEquals(expected, JvmBidiShaper.process(shalom))
    }

    @Test
    fun mixedKeepsLatinAndReversesHebrewInPlace() {
        val input = "abc $shalom def"
        // 'abc ' and ' def' keep order; the Hebrew run is reversed in place.
        val expected = "abc $finalMem$vav$lamed$shin def"
        assertEquals(expected, JvmBidiShaper.process(input))
    }

    @Test
    fun arabicWordIsContextuallyShaped() {
        // "محمد" = MEEM + HAH + MEEM + DAL (logical order).
        val muhammad = "محمد"
        val shaped = JvmBidiShaper.process(muhammad)

        // Expected presentation forms by joining rule:
        //   MEEM initial U+FEE3, HAH medial U+FEA4,
        //   MEEM medial  U+FEE4, DAL final  U+FEAA.
        assertTrue(shaped.contains('ﻣ'), "missing MEEM initial U+FEE3")
        assertTrue(shaped.contains('ﺤ'), "missing HAH medial U+FEA4")
        assertTrue(shaped.contains('ﻤ'), "missing MEEM medial U+FEE4")
        assertTrue(shaped.contains('ﺪ'), "missing DAL final U+FEAA")

        // No base-letter forms should survive shaping.
        assertTrue(!shaped.contains('م'), "raw MEEM leaked through")
        assertTrue(!shaped.contains('د'), "raw DAL leaked through")

        // Pure-RTL → reversed, so the DAL (last logical) comes out first.
        assertEquals('ﺪ', shaped[0])
    }

    @Test
    fun lamAlefFusesToLigature() {
        // LAM (U+0644) + ALEF (U+0627) → isolated LAM-ALEF ligature U+FEFB,
        // a single glyph.
        val lamAlef = "لا"
        val shaped = JvmBidiShaper.process(lamAlef)
        assertEquals(1, shaped.length, "lam-alef did not collapse to one char")
        assertEquals('ﻻ', shaped[0])
    }

    @Test
    fun digitsInsideRtlKeepTheirOrder() {
        // "מספר 123" — the European digits must stay 1,2,3 left-to-right even
        // though they sit inside a Hebrew (RTL) paragraph.
        val mem = 'מ'; val samekh = 'ס'; val pe = 'פ'; val resh = 'ר'
        val input = "$mem$samekh$pe$resh 123"
        val out = JvmBidiShaper.process(input)

        val one = out.indexOf('1')
        val two = out.indexOf('2')
        val three = out.indexOf('3')
        assertTrue(one in 0 until two && two < three, "digits reordered: $out")
        // The "123" substring must appear intact and in order.
        assertTrue(out.contains("123"), "digit run was broken up: $out")
    }

    @Test
    fun endToEndHebrewDocumentProducesPdf() {
        // The bundled Inter font may lack Hebrew glyphs, in which case
        // encodable() strips them — so we can only assert the pipeline runs
        // end-to-end without throwing and still emits a valid PDF header.
        val doc = pdf {
            page {
                text("$shin$lamed$vav$finalMem $shin$lamed$vav$finalMem")
            }
        }
        val bytes = doc.toByteArray()
        assertTrue(bytes.size > 4, "empty PDF")
        val header = bytes.copyOfRange(0, 5).decodeToString()
        assertEquals("%PDF-", header)
    }
}
