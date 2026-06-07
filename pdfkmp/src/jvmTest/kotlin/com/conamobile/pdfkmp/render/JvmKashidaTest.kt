package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-side tests for kashida support in the PdfBox backend: the shaper's
 * forward-join detection ([JvmBidiShaper.kashidaCandidates]) and its robustness
 * when a word already carries tatweels.
 */
class JvmKashidaTest {

    private val tatweel = "ـ"
    private val beh = "ب" // BEH, dual-joining
    private val alef = "ا" // ALEF, right-joining

    @Test
    fun kashidaCandidates_onShapedText_findsForwardJoiners() {
        // Shape "بببب" first so we work in presentation-form space, then ask
        // where a tatweel may go. The shaped run is reversed (visual order); the
        // initial/medial BEH forms all join forward, so every position but the
        // last (the final form) is a candidate.
        val shaped = JvmBidiShaper.process(beh + beh + beh + beh)
        val candidates = JvmBidiShaper.kashidaCandidates(shaped)
        assertTrue(candidates.isNotEmpty(), "no kashida candidates in a 4-letter join")
        // The final-form glyph (last logical, first visual after reversal) must
        // not be a forward-joining candidate.
        assertTrue(candidates.all { it < shaped.length - 1 })
    }

    @Test
    fun kashidaCandidates_latinHasNone() {
        assertTrue(JvmBidiShaper.kashidaCandidates("hello").isEmpty())
    }

    @Test
    fun shaper_shapesTatweelBearingWord_withoutThrowing() {
        // A word that already contains a tatweel between two BEHs must shape and
        // reorder without error, keeping the tatweel in the output.
        val word = beh + tatweel + beh
        val shaped = JvmBidiShaper.process(word)
        assertTrue(shaped.contains('ـ'), "tatweel was dropped during shaping")
    }

    @Test
    fun endToEnd_kashidaJustifiedArabic_producesPdf() {
        // Drive the full pipeline with kashida justification on; assert only that
        // a valid PDF is emitted (the bundled font may lack some Arabic glyphs).
        val w = beh + beh + beh + beh
        val doc = pdf {
            page {
                text("$w $w $w $w $w $w") {
                    align = TextAlign.Justify
                    kashidaJustify = true
                    fontSize = 10.sp
                }
            }
        }
        val bytes = doc.toByteArray()
        assertTrue(bytes.size > 4, "empty PDF")
        assertEquals("%PDF-", bytes.copyOfRange(0, 5).decodeToString())
    }
}
