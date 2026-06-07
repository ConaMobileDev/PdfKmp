package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.font.BundledFonts
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.ttf.TtfFont
import com.conamobile.pdfkmp.ttf.TtfParser

/**
 * Document-scoped owner of every embeddable TrueType face the pure-Kotlin
 * backend can fall back to or embed, and the decision-maker for *how* a given
 * text run is rendered.
 *
 * Three rendering strategies exist, in priority order per run (see [planRun]):
 *
 * 1. **Embedded custom font** — the style names a [PdfFont.Custom]; its bytes are
 *    parsed once and embedded as a CIDFontType2 (full Unicode for whatever the
 *    font covers).
 * 2. **Embedded bundled Inter** — the run contains a code point WinAnsi can't
 *    represent (e.g. Cyrillic "Привет") but the matching Inter face covers it.
 *    Inter is embedded as a CIDFontType2 so the text renders and extracts.
 * 3. **Standard-14 Helvetica** — the WinAnsi-representable common case; no
 *    embedding, smallest output, exactly the original phase-1 behaviour.
 *
 * The registry is shared between [KmpFontMetrics] and [KmpPdfCanvas] for one
 * document so measurement and drawing always pick the *same* strategy for the
 * same run (a width measured against Inter must be drawn with Inter), and it
 * pools each embedded face so a face used on many pages is embedded once.
 *
 * Bold/italic for an embedded face: the four bundled Inter faces (Regular /
 * Bold / Italic / BoldItalic) are selected by weight/style. A custom font is
 * embedded as supplied — only the one face the user passed exists, so a bold or
 * italic style over a single-face custom font reuses that face unchanged (no
 * synthetic slanting or emboldening), with a one-time note.
 */
internal class KmpFontRegistry(customFonts: List<PdfFont.Custom>) {

    /** How one text run is rendered, decided once and reused by metrics + canvas. */
    internal sealed interface RunPlan {
        /** Standard-14 Helvetica face, WinAnsi single-byte encoding. */
        class Helvetica(val face: HelveticaFace) : RunPlan

        /** An embedded CIDFontType2 face addressed via Identity-H glyph ids. */
        class Embedded(val embedded: KmpEmbeddedFont) : RunPlan
    }

    /** Parsed custom fonts by [PdfFont.Custom.name]; `null` value = a parse that failed. */
    private val customParsed = HashMap<String, TtfFont?>()

    /** Embedded-font pool keyed by a stable face key, built lazily on first real use. */
    private val embeddedPool = HashMap<String, KmpEmbeddedFont>()

    /** Bundled Inter faces parsed on demand, keyed by the bold/italic combination. */
    private val interParsed = HashMap<String, TtfFont?>()

    private var warnedCustomParse = false
    private var warnedCustomSingleFace = false
    private var warnedInterMissingGlyph = false

    init {
        for (font in customFonts) parseCustom(font)
    }

    /** Every embedded face that ended up used, for the assembler to serialise. */
    fun embeddedFonts(): List<KmpEmbeddedFont> = embeddedPool.values.toList()

    /**
     * Decides how to render [text] in [style] and records glyph usage on the
     * chosen embedded face (if any) so the subset is complete. The same call from
     * the metrics and the canvas yields the same plan because it is a pure
     * function of the inputs plus the (idempotent) usage recording.
     */
    fun planRun(text: String, style: TextStyle): RunPlan {
        val font = style.font
        // 1. Custom font → embed if it parsed.
        if (font is PdfFont.Custom) {
            val parsed = customParsed[font.name]
            if (parsed != null) {
                val embedded = embeddedFor("custom:${font.name}", parsed, sanitizeName(font.name))
                recordUsage(embedded, text)
                if (style.fontWeight.value >= FontWeight.SemiBold.value || style.fontStyle == FontStyle.Italic) {
                    noteCustomSingleFace(font.name)
                }
                return RunPlan.Embedded(embedded)
            }
            // Parse failed: fall through to the standard-14 / Inter logic below.
        }

        // 2. Non-WinAnsi code points present that bundled Inter covers → embed Inter.
        if (needsUnicodeFallback(text)) {
            val interPlan = planInterFallback(text, style)
            if (interPlan != null) return interPlan
        }

        // 3. Standard-14 Helvetica.
        return RunPlan.Helvetica(HelveticaFace.forStyle(style.fontWeight, style.fontStyle))
    }

    /** `true` if any code point in [text] cannot be encoded in WinAnsi. */
    private fun needsUnicodeFallback(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            i += if (cp > 0xFFFF) 2 else 1
            if (WinAnsiEncoding.encode(cp) == null) return true
        }
        return false
    }

    /**
     * Plans the bundled-Inter embedding for a Unicode-needing run, or `null` when
     * the matching Inter face can't be parsed (the caller then degrades to
     * Helvetica + `?`). Inter need not cover *every* code point — uncovered ones
     * render as `.notdef` and warn once — but the face must at least parse.
     */
    private fun planInterFallback(text: String, style: TextStyle): RunPlan? {
        val bold = style.fontWeight.value >= FontWeight.SemiBold.value
        val italic = style.fontStyle == FontStyle.Italic
        val key = "inter:${if (bold) "b" else ""}${if (italic) "i" else ""}"
        val parsed = interParsed.getOrPut(key) { parseInter(bold, italic) } ?: return null
        val embedded = embeddedFor(key, parsed, interBaseName(bold, italic))
        recordUsage(embedded, text)
        return RunPlan.Embedded(embedded)
    }

    /** Records each code point of [text] on [embedded], warning once if Inter lacks a glyph. */
    private fun recordUsage(embedded: KmpEmbeddedFont, text: String) {
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            i += if (cp > 0xFFFF) 2 else 1
            val gid = embedded.use(cp)
            if (gid == 0 && !warnedInterMissingGlyph && WinAnsiEncoding.encode(cp) == null) {
                warnedInterMissingGlyph = true
                PdfLog.warn(
                    "Some characters have no glyph in the embedded font and render as a missing-glyph box; " +
                        "supply a PdfFont.Custom with coverage for that script.",
                )
            }
        }
    }

    private fun embeddedFor(key: String, parsed: TtfFont, baseName: String): KmpEmbeddedFont =
        embeddedPool.getOrPut(key) { KmpEmbeddedFont(parsed, baseName) }

    // -- Parsing ----------------------------------------------------------

    private fun parseCustom(font: PdfFont.Custom) {
        customParsed.getOrPut(font.name) {
            runCatchingParse(font.bytes) ?: run {
                noteCustomParseFailure(font.name)
                null
            }
        }
    }

    private fun parseInter(bold: Boolean, italic: Boolean): TtfFont? {
        val bytes = when {
            bold && italic -> BundledFonts.interBoldItalic
            bold -> BundledFonts.interBold
            italic -> BundledFonts.interItalic
            else -> BundledFonts.interRegular
        }
        return runCatchingParse(bytes)
    }

    /** Parses [bytes], returning `null` (rather than throwing) on any malformed font. */
    private fun runCatchingParse(bytes: ByteArray): TtfFont? = try {
        TtfParser.parse(bytes)
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        // A truncated / corrupt font can over-read; treat as unparseable.
        null
    }

    private fun interBaseName(bold: Boolean, italic: Boolean): String = when {
        bold && italic -> "Inter-BoldItalic"
        bold -> "Inter-Bold"
        italic -> "Inter-Italic"
        else -> "Inter-Regular"
    }

    /** A PostScript-safe base name: strip spaces and the bytes a name token forbids. */
    private fun sanitizeName(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            if (c.code in 0x21..0x7E && c != '(' && c != ')' && c != '<' && c != '>' &&
                c != '[' && c != ']' && c != '{' && c != '}' && c != '/' && c != '%' && c != ' '
            ) {
                sb.append(c)
            }
        }
        return if (sb.isEmpty()) "CustomFont" else sb.toString()
    }

    // -- One-time warnings ------------------------------------------------

    private fun noteCustomParseFailure(name: String) {
        if (warnedCustomParse) return
        warnedCustomParse = true
        PdfLog.warn(
            "Custom font '$name' could not be parsed as a TrueType font by the pure-Kotlin PDF " +
                "backend; falling back to Helvetica (only TrueType-outline TTFs are supported, not CFF/OTF).",
        )
    }

    private fun noteCustomSingleFace(name: String) {
        if (warnedCustomSingleFace) return
        warnedCustomSingleFace = true
        PdfLog.warn(
            "A bold/italic style was requested for custom font '$name', but the pure-Kotlin PDF backend " +
                "embeds the single supplied face as-is and does not synthesize bold or italic.",
        )
    }

    /**
     * Decodes the Unicode code point at [index], combining a surrogate pair into
     * one astral code point. Stdlib char arithmetic keeps it wasm-compatible.
     */
    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            }
        }
        return high.code
    }
}
