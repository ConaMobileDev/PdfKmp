package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the Liang hyphenation algorithm ([HyphenationPatterns]) and its
 * integration into [layoutText].
 *
 * The expected break points are the *actual* output of the bundled en-US
 * subset (hand-verified by tracing the patterns), not idealised dictionary
 * values — the embedded set is reduced, so some classic breaks are absent and
 * the assertions reflect exactly what the shipped patterns produce.
 */
class HyphenationTest {

    private val enUs get() = Hyphenators.EnUs

    @Test
    fun hyphenation_word_breaksLikeTeX() {
        // .hy3phen5a5tion. → odd scores after "hy" and after "phen".
        assertEquals(listOf(2, 6), enUs.hyphenate("hyphenation"))
        assertEquals("hy-phen-ation", render("hyphenation", enUs.hyphenate("hyphenation")))
    }

    @Test
    fun computer_breaksAfterCom() {
        // The reduced set offers the com- break; the put/er break needs a
        // long-tail pattern this subset omits, so only [3] is produced.
        assertEquals(listOf(3), enUs.hyphenate("computer"))
    }

    @Test
    fun common_words_haveExpectedBreaks() {
        assertEquals(listOf(2, 4), enUs.hyphenate("algorithm")) // al-go-rithm
        assertEquals(listOf(2, 4), enUs.hyphenate("example")) // ex-am-ple
        assertEquals(listOf(2, 5, 7), enUs.hyphenate("typography")) // ty-pog-ra-phy
        assertEquals(listOf(3, 6), enUs.hyphenate("wonderful")) // won-der-ful
        assertEquals(listOf(4), enUs.hyphenate("paragraph")) // para-graph
    }

    @Test
    fun shortWords_andLeftRightMin_yieldNoBreaks() {
        // Below leftMin + rightMin (2 + 3 = 5) there is nothing to break.
        assertTrue(enUs.hyphenate("cat").isEmpty())
        assertTrue(enUs.hyphenate("the").isEmpty())
        // leftMin/rightMin must keep at least 2 leading + 3 trailing letters,
        // so no break index is < 2 or > length-3.
        val breaks = enUs.hyphenate("hyphenation")
        assertTrue(breaks.all { it in 2..("hyphenation".length - 3) })
    }

    @Test
    fun preHyphenatedOrNumericWords_areLeftAlone() {
        // Words carrying an explicit '-' or digits already have (or preclude)
        // break opportunities — the dictionary must not add more.
        assertTrue(enUs.hyphenate("co-operate").isEmpty())
        assertTrue(enUs.hyphenate("h2ophobia").isEmpty())
    }

    @Test
    fun caseIsIgnored_butIndicesMapToOriginal() {
        // Patterns are lower-case; capitalised input must still break, with
        // indices referring to the original string.
        assertEquals(enUs.hyphenate("hyphenation"), enUs.hyphenate("Hyphenation"))
        assertEquals(enUs.hyphenate("hyphenation"), enUs.hyphenate("HYPHENATION"))
    }

    @Test
    fun wrap_withHyphenation_splitsWithVisibleHyphen() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, hyphenation = enUs)
        // "hyphenation" is 11 wide; a 6-point slot forces a break. The first
        // legal break (index 2) keeps "hy-" (3 cols incl. the hyphen).
        val result = layoutText(
            text = "hyphenation",
            style = style,
            maxWidth = 6f,
            metrics = metrics,
        )
        // Greedy fit: take the widest hyphen prefix ≤ 6 → "phen-" after "hy".
        // hy(2) phen(4)+'-' = "hyphen-" is 7 > 6, so first line is "hy-".
        assertEquals("hy-", result.lines.first().text)
        assertTrue(result.lines.all { it.width <= 6f })
        // Reassembling without hyphens reproduces the source word.
        val rejoined = result.lines.joinToString("") { it.text.removeSuffix("-") }
        assertEquals("hyphenation", rejoined)
    }

    @Test
    fun wrap_withoutHyphenation_isUnchanged() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val plain = TextStyle(fontSize = 1.sp)
        val result = layoutText("hyphenation", plain, maxWidth = 6f, metrics = metrics)
        // No dictionary → falls back to the existing mid-word hard break with
        // no visible hyphen, exactly as before this feature existed. The 11-char
        // word splits into the widest full-width chunks ("hyphen" = 6, "ation").
        assertEquals(listOf("hyphen", "ation"), result.lines.map { it.text })
        assertTrue(result.lines.none { it.text.endsWith("-") })
    }

    @Test
    fun explicitSoftHyphen_winsOverDictionary() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, hyphenation = enUs)
        // Author placed one soft hyphen; the dictionary must defer to it.
        val result = layoutText("ab­cdefgh", style, maxWidth = 5f, metrics = metrics)
        assertEquals("ab-", result.lines.first().text)
    }

    private fun render(word: String, breaks: List<Int>): String {
        val sb = StringBuilder()
        var next = 0
        for (i in word.indices) {
            if (next < breaks.size && breaks[next] == i) {
                sb.append('-'); next++
            }
            sb.append(word[i])
        }
        return sb.toString()
    }
}
