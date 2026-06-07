package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for kashida (tatweel, U+0640) justification of cursive RTL lines and
 * the joining-position helper [kashidaPositions]. Arabic letters are written
 * with `\u` escapes so the expectations are encoding-independent.
 */
class KashidaTest {

    private val tatweel = 'ـ'
    // BEH (U+0628): dual-joining, connects on both sides.
    private val beh = 'ب'
    // ALEF (U+0627): right-joining, never connects forward.
    private val alef = 'ا'

    @Test
    fun kashidaPositions_findsJoinsBetweenDualJoiners() {
        // Four BEHs join at every interior boundary: after 0, 1, 2.
        val word = "$beh$beh$beh$beh"
        assertEquals(listOf(0, 1, 2), kashidaPositions(word))
    }

    @Test
    fun kashidaPositions_excludesNonForwardJoins() {
        // ALEF is right-joining: a join may sit *after* it (when preceded by a
        // dual-joiner) but never *before* the next letter because ALEF does not
        // reach forward. BEH+ALEF → join after BEH (index 0); ALEF+BEH → none.
        assertEquals(listOf(0), kashidaPositions("$beh$alef"))
        assertTrue(kashidaPositions("$alef$beh").isEmpty())
        // Latin letters never produce kashida positions.
        assertTrue(kashidaPositions("hello").isEmpty())
    }

    @Test
    fun justify_rtl_withKashida_insertsTatweel() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, align = TextAlign.Justify, kashidaJustify = true)
        // Three 4-letter Arabic words wrap to two lines at width 10; the first
        // (non-final) line is justified and absorbs slack as kashida.
        val w = "$beh$beh$beh$beh"
        val result = layoutText(
            text = "$w $w $w",
            style = style,
            maxWidth = 10f,
            metrics = metrics,
        )
        val justified = result.lines.first()
        assertTrue(justified.justifiedWords.isNotEmpty(), "first line should be justified")
        val joined = justified.justifiedWords.joinToString("") { it.text }
        assertTrue(joined.contains(tatweel), "kashida did not insert a tatweel: $joined")
        // The line still fills the slot exactly.
        assertEquals(10f, justified.width)
    }

    @Test
    fun justify_ltr_neverInsertsTatweel() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, align = TextAlign.Justify, kashidaJustify = true)
        // kashidaJustify is set, but Latin text resolves LTR → no tatweels.
        // Width 8 wraps "aa bb cc dd" so the first line is actually justified.
        val result = layoutText(
            text = "aa bb cc dd",
            style = style,
            maxWidth = 8f,
            metrics = metrics,
        )
        assertTrue(result.lines.first().justifiedWords.isNotEmpty(), "first line should be justified")
        val joined = result.lines.joinToString("") { line ->
            if (line.justifiedWords.isNotEmpty()) line.justifiedWords.joinToString("") { it.text } else line.text
        }
        assertFalse(joined.contains(tatweel), "tatweel leaked into LTR text: $joined")
    }

    @Test
    fun justify_rtl_withoutKashidaFlag_insertsNoTatweel() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        // kashidaJustify defaults to false → behaviour is unchanged: space-only
        // justification, no tatweels.
        val style = TextStyle(fontSize = 1.sp, align = TextAlign.Justify)
        val w = "$beh$beh$beh$beh"
        val result = layoutText("$w $w $w", style, maxWidth = 10f, metrics = metrics)
        val justified = result.lines.first()
        assertTrue(justified.justifiedWords.isNotEmpty(), "first line should be justified")
        val joined = justified.justifiedWords.joinToString("") { it.text }
        assertFalse(joined.contains(tatweel), "tatweel inserted without the flag: $joined")
    }
}
