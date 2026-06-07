package com.conamobile.pdfkmp.render

import java.text.Bidi

/**
 * JVM-only bidirectional reordering + Arabic contextual shaping for the PdfBox
 * backend.
 *
 * PdfBox's `showText` writes glyphs in the exact logical order it is handed and
 * performs no script processing whatsoever. For Latin that is fine, but for
 * right-to-left scripts it produces two defects the platform text engines on
 * Android (Canvas) and iOS (Core Text) hide for free:
 *
 *  - **No bidi reordering** — a Hebrew/Arabic run stored in logical order comes
 *    out left-to-right, i.e. visually reversed.
 *  - **No Arabic shaping** — Arabic is cursive; each letter has up to four
 *    context-dependent forms (isolated / initial / medial / final). Without
 *    shaping every letter renders in its disconnected isolated form.
 *
 * [process] fixes both, in logical → shaped → visual order, returning a string
 * whose code units are already in the left-to-right visual order PdfBox should
 * paint. It is a no-op (returns the input by identity) for any string that
 * contains no RTL characters, so Latin-only documents pay nothing.
 */
internal object JvmBidiShaper {

    /**
     * Returns [text] reshaped and reordered for visual (left-to-right) drawing
     * by PdfBox. Latin-only input is returned unchanged.
     */
    fun process(text: String): String {
        if (text.isEmpty() || !containsRtl(text)) return text
        val shaped = shapeArabic(text)
        return reorder(shaped)
    }

    /** True if [text] holds any character from an RTL Unicode block. */
    private fun containsRtl(text: String): Boolean {
        for (ch in text) {
            val c = ch.code
            val rtl = (c in 0x0590..0x05FF) || // Hebrew
                (c in 0x0600..0x06FF) || // Arabic
                (c in 0x0750..0x077F) || // Arabic Supplement
                (c in 0xFB1D..0xFDFF) || // Hebrew + Arabic Presentation Forms-A
                (c in 0xFE70..0xFEFF) // Arabic Presentation Forms-B
            if (rtl) return true
        }
        return false
    }

    /**
     * Positions in already-[shaped][shapeArabic] text *after which* a tatweel
     * (U+0640) may be inserted to elongate the cursive line — i.e. after a letter
     * whose chosen presentation form joins forward (an **initial** or **medial**
     * form), since only those connect to the following glyph.
     *
     * Common layout decides *whether* to add kashida (see
     * [com.conamobile.pdfkmp.style.TextStyle.kashidaJustify]); this JVM helper
     * answers *where it is safe* once glyphs are in presentation-form space, and
     * backs the shaper's own correctness tests. Returned indices are ascending.
     */
    internal fun kashidaCandidates(shapedText: String): List<Int> {
        if (shapedText.length < 2) return emptyList()
        val out = ArrayList<Int>()
        for (i in 0 until shapedText.length - 1) {
            if (joinsForward(shapedText[i])) out += i
        }
        return out
    }

    /**
     * True when [ch] is an Arabic presentation form that connects to the glyph
     * after it — the *initial* and *medial* forms in the Arabic Presentation
     * Forms-B block (U+FE70..U+FEFC). Tatweel (U+0640) itself joins on both
     * sides, so it also qualifies.
     */
    private fun joinsForward(ch: Char): Boolean {
        val c = ch.code
        if (c == 0x0640) return true // tatweel joins both ways
        return c in PRESENTATION_FORWARD
    }

    // region Arabic shaping

    /**
     * Joining class of an Arabic letter.
     *
     * Dual-joining letters connect on both sides (so they can take medial /
     * initial / final forms); right-joining letters only connect to a preceding
     * letter (they have isolated and final forms only and never connect forward,
     * which is why a following letter never gets a medial/final form because of
     * them).
     */
    private enum class Joining { DUAL, RIGHT }

    /**
     * Presentation forms of a single base letter: `[isolated, final, initial,
     * medial]`. Right-joining letters supply only `isolated` and `final`; their
     * `initial` and `medial` slots repeat `isolated`/`final` and are never
     * selected because such letters never sit in an initial/medial position.
     */
    private class Forms(
        val joining: Joining,
        val isolated: Char,
        val final: Char,
        val initial: Char = isolated,
        val medial: Char = final,
    )

    /**
     * Picks the form for a letter given whether it connects to the previous
     * letter ([joinPrev]) and the next letter ([joinNext]).
     *
     * A letter connects forward only when it is dual-joining; a right-joining
     * letter forces [joinNext] to be ignored at the call site.
     */
    private fun Forms.select(joinPrev: Boolean, joinNext: Boolean): Char = when {
        joinPrev && joinNext -> medial
        joinPrev && !joinNext -> final
        !joinPrev && joinNext -> initial
        else -> isolated
    }

    /**
     * Replaces base Arabic letters with their contextual presentation forms and
     * fuses LAM + ALEF sequences into their ligatures.
     *
     * Harakat / tashkeel diacritics are *transparent* for joining: they are
     * skipped when deciding a neighbour's form, but kept in place right after
     * the base letter they belong to so vowel marks survive shaping.
     */
    private fun shapeArabic(text: String): String {
        val src = text.toCharArray()
        val out = StringBuilder(src.size)
        var i = 0
        while (i < src.size) {
            val ch = src[i]
            val forms = FORMS[ch]
            if (forms == null) {
                out.append(ch)
                i++
                continue
            }

            // LAM followed by an ALEF variant (across transparent marks) fuses
            // into a single ligature glyph; handle it before normal shaping.
            if (ch == LAM) {
                val alefAt = nextBaseIndex(src, i + 1)
                val alefForm = alefAt?.let { LAM_ALEF[src[it]] }
                if (alefForm != null) {
                    val joinPrev = connectsFromPrev(src, i)
                    out.append(if (joinPrev) alefForm.second else alefForm.first)
                    // Carry any diacritics that sat between LAM and ALEF, then
                    // skip past the consumed ALEF.
                    appendMarksBetween(src, i + 1, alefAt, out)
                    i = alefAt + 1
                    continue
                }
            }

            val joinPrev = connectsFromPrev(src, i)
            val joinNext = forms.joining == Joining.DUAL && connectsToNext(src, i)
            out.append(forms.select(joinPrev, joinNext))
            i++
        }
        return out.toString()
    }

    /** True if the letter at [index] connects to a joining letter before it. */
    private fun connectsFromPrev(src: CharArray, index: Int): Boolean {
        var j = index - 1
        while (j >= 0 && isTransparent(src[j])) j--
        if (j < 0) return false
        // The previous base must be dual-joining to reach forward to us.
        return FORMS[src[j]]?.joining == Joining.DUAL
    }

    /** True if a joining Arabic letter follows the letter at [index]. */
    private fun connectsToNext(src: CharArray, index: Int): Boolean {
        val n = nextBaseIndex(src, index + 1) ?: return false
        return FORMS.containsKey(src[n])
    }

    /** Index of the next non-transparent character at or after [from], or null. */
    private fun nextBaseIndex(src: CharArray, from: Int): Int? {
        var j = from
        while (j < src.size && isTransparent(src[j])) j++
        return if (j < src.size) j else null
    }

    /** Appends any transparent marks in `[from, to)` verbatim to [out]. */
    private fun appendMarksBetween(src: CharArray, from: Int, to: Int, out: StringBuilder) {
        for (k in from until to) if (isTransparent(src[k])) out.append(src[k])
    }

    /**
     * True for combining marks that are transparent to cursive joining
     * (harakat 0x064B–0x065F and the superscript alef 0x0670).
     */
    private fun isTransparent(ch: Char): Boolean {
        val c = ch.code
        return c in 0x064B..0x065F || c == 0x0670
    }

    // endregion

    // region Bidi reordering

    /**
     * Reorders [shaped] (already in logical order, with Arabic letters replaced
     * by presentation forms) into visual left-to-right order.
     *
     * Uses [Bidi] with the default-LTR base direction. Even-level runs (LTR,
     * including European-number runs embedded in RTL text) keep their order;
     * odd-level runs (RTL) are reversed and have paired brackets mirrored.
     */
    private fun reorder(shaped: String): String {
        val bidi = Bidi(shaped, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        if (!bidi.isMixed) {
            // Single direction: reverse iff the whole paragraph is RTL.
            return if (bidi.isRightToLeft) reverseRun(shaped) else shaped
        }

        val runCount = bidi.runCount
        val levels = ByteArray(runCount)
        @Suppress("UNCHECKED_CAST")
        val runs = arrayOfNulls<Any>(runCount)
        for (r in 0 until runCount) {
            levels[r] = bidi.getRunLevel(r).toByte()
            runs[r] = r
        }
        // Reorder the run indices into visual (left-to-right) order.
        Bidi.reorderVisually(levels, 0, runs, 0, runCount)

        val out = StringBuilder(shaped.length)
        for (visual in 0 until runCount) {
            val r = runs[visual] as Int
            val piece = shaped.substring(bidi.getRunStart(r), bidi.getRunLimit(r))
            // Odd run level == RTL: emit characters back-to-front + mirror.
            if (bidi.getRunLevel(r) % 2 == 1) out.append(reverseRun(piece)) else out.append(piece)
        }
        return out.toString()
    }

    /** Reverses an RTL run's characters and mirrors any paired brackets in it. */
    private fun reverseRun(run: String): String {
        val out = StringBuilder(run.length)
        for (k in run.indices.reversed()) out.append(mirror(run[k]))
        return out.toString()
    }

    /** Mirrors paired punctuation so it points the correct way once reversed. */
    private fun mirror(ch: Char): Char = when (ch) {
        '(' -> ')'
        ')' -> '('
        '[' -> ']'
        ']' -> '['
        '{' -> '}'
        '}' -> '{'
        '<' -> '>'
        '>' -> '<'
        else -> ch
    }

    // endregion

    // region Shaping tables

    /**
     * Presentation forms that join to the following glyph: the *initial* and
     * *medial* forms of every dual-joining letter. Derived from [FORMS] so the
     * two stay in lock-step. Right-joining letters contribute nothing — their
     * forms never connect forward.
     */
    private val PRESENTATION_FORWARD: Set<Int> by lazy {
        buildSet {
            for (forms in FORMS.values) {
                if (forms.joining == Joining.DUAL) {
                    add(forms.initial.code)
                    add(forms.medial.code)
                }
            }
        }
    }

    private const val LAM = 'ل'

    /**
     * LAM + ALEF ligatures keyed by the ALEF variant, valued as
     * `(isolated, final)`. The final form is chosen when the LAM connects to a
     * preceding letter.
     */
    private val LAM_ALEF: Map<Char, Pair<Char, Char>> = mapOf(
        'آ' to ('ﻵ' to 'ﻶ'), // ALEF WITH MADDA ABOVE
        'أ' to ('ﻷ' to 'ﻸ'), // ALEF WITH HAMZA ABOVE
        'إ' to ('ﻹ' to 'ﻺ'), // ALEF WITH HAMZA BELOW
        'ا' to ('ﻻ' to 'ﻼ'), // bare ALEF
    )

    /**
     * Base Arabic letter → its presentation [Forms]. Covers the standard Arabic
     * alphabet (0x0621–0x064A) plus the common Persian/Urdu extras called out
     * in the spec (peh, tcheh, jeh, gaf, keheh, farsi yeh).
     */
    private val FORMS: Map<Char, Forms> = buildMap {
        // Right-joining (isolated + final only).
        put('ء', Forms(Joining.RIGHT, 'ﺀ', 'ﺀ')) // HAMZA
        put('آ', Forms(Joining.RIGHT, 'ﺁ', 'ﺂ')) // ALEF MADDA
        put('أ', Forms(Joining.RIGHT, 'ﺃ', 'ﺄ')) // ALEF HAMZA ABOVE
        put('ؤ', Forms(Joining.RIGHT, 'ﺅ', 'ﺆ')) // WAW HAMZA
        put('إ', Forms(Joining.RIGHT, 'ﺇ', 'ﺈ')) // ALEF HAMZA BELOW
        put('ا', Forms(Joining.RIGHT, 'ﺍ', 'ﺎ')) // ALEF
        put('ة', Forms(Joining.RIGHT, 'ﺓ', 'ﺔ')) // TEH MARBUTA
        put('د', Forms(Joining.RIGHT, 'ﺩ', 'ﺪ')) // DAL
        put('ذ', Forms(Joining.RIGHT, 'ﺫ', 'ﺬ')) // THAL
        put('ر', Forms(Joining.RIGHT, 'ﺭ', 'ﺮ')) // REH
        put('ز', Forms(Joining.RIGHT, 'ﺯ', 'ﺰ')) // ZAIN
        put('و', Forms(Joining.RIGHT, 'ﻭ', 'ﻮ')) // WAW
        put('ژ', Forms(Joining.RIGHT, 'ﮊ', 'ﮋ')) // JEH (Persian)

        // Dual-joining (all four forms).
        put('ئ', Forms(Joining.DUAL, 'ﺉ', 'ﺊ', 'ﺋ', 'ﺌ')) // YEH HAMZA
        put('ب', Forms(Joining.DUAL, 'ﺏ', 'ﺐ', 'ﺑ', 'ﺒ')) // BEH
        put('ت', Forms(Joining.DUAL, 'ﺕ', 'ﺖ', 'ﺗ', 'ﺘ')) // TEH
        put('ث', Forms(Joining.DUAL, 'ﺙ', 'ﺚ', 'ﺛ', 'ﺜ')) // THEH
        put('ج', Forms(Joining.DUAL, 'ﺝ', 'ﺞ', 'ﺟ', 'ﺠ')) // JEEM
        put('ح', Forms(Joining.DUAL, 'ﺡ', 'ﺢ', 'ﺣ', 'ﺤ')) // HAH
        put('خ', Forms(Joining.DUAL, 'ﺥ', 'ﺦ', 'ﺧ', 'ﺨ')) // KHAH
        put('س', Forms(Joining.DUAL, 'ﺱ', 'ﺲ', 'ﺳ', 'ﺴ')) // SEEN
        put('ش', Forms(Joining.DUAL, 'ﺵ', 'ﺶ', 'ﺷ', 'ﺸ')) // SHEEN
        put('ص', Forms(Joining.DUAL, 'ﺹ', 'ﺺ', 'ﺻ', 'ﺼ')) // SAD
        put('ض', Forms(Joining.DUAL, 'ﺽ', 'ﺾ', 'ﺿ', 'ﻀ')) // DAD
        put('ط', Forms(Joining.DUAL, 'ﻁ', 'ﻂ', 'ﻃ', 'ﻄ')) // TAH
        put('ظ', Forms(Joining.DUAL, 'ﻅ', 'ﻆ', 'ﻇ', 'ﻈ')) // ZAH
        put('ع', Forms(Joining.DUAL, 'ﻉ', 'ﻊ', 'ﻋ', 'ﻌ')) // AIN
        put('غ', Forms(Joining.DUAL, 'ﻍ', 'ﻎ', 'ﻏ', 'ﻐ')) // GHAIN
        put('ف', Forms(Joining.DUAL, 'ﻑ', 'ﻒ', 'ﻓ', 'ﻔ')) // FEH
        put('ق', Forms(Joining.DUAL, 'ﻕ', 'ﻖ', 'ﻗ', 'ﻘ')) // QAF
        put('ك', Forms(Joining.DUAL, 'ﻙ', 'ﻚ', 'ﻛ', 'ﻜ')) // KAF
        put('ل', Forms(Joining.DUAL, 'ﻝ', 'ﻞ', 'ﻟ', 'ﻠ')) // LAM
        put('م', Forms(Joining.DUAL, 'ﻡ', 'ﻢ', 'ﻣ', 'ﻤ')) // MEEM
        put('ن', Forms(Joining.DUAL, 'ﻥ', 'ﻦ', 'ﻧ', 'ﻨ')) // NOON
        put('ه', Forms(Joining.DUAL, 'ﻩ', 'ﻪ', 'ﻫ', 'ﻬ')) // HEH
        put('ي', Forms(Joining.DUAL, 'ﻱ', 'ﻲ', 'ﻳ', 'ﻴ')) // YEH
        // Persian / Urdu extras (dual-joining).
        put('پ', Forms(Joining.DUAL, 'ﭖ', 'ﭗ', 'ﭘ', 'ﭙ')) // PEH
        put('چ', Forms(Joining.DUAL, 'ﭺ', 'ﭻ', 'ﭼ', 'ﭽ')) // TCHEH
        put('گ', Forms(Joining.DUAL, 'ﮒ', 'ﮓ', 'ﮔ', 'ﮕ')) // GAF
        put('ک', Forms(Joining.DUAL, 'ﮎ', 'ﮏ', 'ﮐ', 'ﮑ')) // KEHEH
        put('ی', Forms(Joining.DUAL, 'ﯼ', 'ﯽ', 'ﯾ', 'ﯿ')) // FARSI YEH
    }

    // endregion
}
