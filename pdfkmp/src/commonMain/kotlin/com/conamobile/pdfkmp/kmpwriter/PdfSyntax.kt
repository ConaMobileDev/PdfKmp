package com.conamobile.pdfkmp.kmpwriter

/**
 * Low-level PDF token formatting shared by the pure-Kotlin writer's content
 * streams and object dictionaries.
 *
 * Two concerns live here because both the page content emitter and the document
 * assembler need them and neither should own the other:
 *
 * - [formatNumber] renders a `Float` as the shortest fixed-point literal a PDF
 *   tokenizer accepts (no scientific notation, no trailing `.0`), which keeps
 *   content streams compact and avoids the locale-dependent grouping a naive
 *   `toString()` could introduce on some platforms.
 * - [pdfString] / [hexUtf16] encode user text (URLs, titles, bookmark labels)
 *   as PDF string objects with the right escaping, falling back to UTF-16BE for
 *   anything outside printable ASCII so accents and CJK survive in metadata and
 *   annotation targets.
 *
 * The escaping rules deliberately mirror the established [com.conamobile.pdfkmp.pdfwriter.PdfPatcher]
 * so both writers produce byte-compatible strings; the code itself is written
 * fresh because the patcher is an incremental updater and this is a full-document
 * writer.
 */
internal object PdfSyntax {

    private const val HEX_DIGITS = "0123456789ABCDEF"

    /**
     * Formats [value] as a PDF real number with up to [decimals] fractional
     * digits, dropping trailing zeros and a bare trailing point. NaN / infinite
     * inputs collapse to `"0"` so a degenerate coordinate can never emit a token
     * the PDF tokenizer would reject and corrupt the stream.
     *
     * The rounding goes through scaled integer arithmetic (not `String.format`,
     * which doesn't exist in common Kotlin and would be locale-sensitive) so the
     * output is identical on every platform — important because the byte offsets
     * in the xref table must match what was actually written.
     */
    fun formatNumber(value: Float, decimals: Int = 4): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val negative = value < 0f
        val magnitude = if (negative) -value else value

        var scale = 1L
        repeat(decimals) { scale *= 10L }
        // Round half-up at the requested precision.
        val scaled = (magnitude.toDouble() * scale + 0.5).toLong()
        val whole = scaled / scale
        var frac = scaled % scale

        val sb = StringBuilder()
        if (negative && scaled != 0L) sb.append('-')
        sb.append(whole)
        if (frac != 0L) {
            // Build the fractional part right-padded to [decimals] then trim
            // trailing zeros: 0.5000 -> ".5", 0.0625 -> ".0625".
            val fracDigits = StringBuilder()
            var divisor = scale / 10L
            while (divisor >= 1L) {
                fracDigits.append(('0' + ((frac / divisor) % 10L).toInt()))
                divisor /= 10L
                if (divisor == 0L) break
            }
            // Trim trailing zeros.
            var end = fracDigits.length
            while (end > 0 && fracDigits[end - 1] == '0') end--
            if (end > 0) {
                sb.append('.')
                sb.append(fracDigits, 0, end)
            }
        }
        return sb.toString()
    }

    /**
     * Encodes [text] as a PDF string object. Pure-ASCII text uses a
     * parenthesised literal with `\`, `(`, `)`, and the EOL controls escaped;
     * any other character forces the UTF-16BE-with-BOM hex form so the full
     * Unicode repertoire survives in metadata and annotation strings (which are
     * not constrained to the WinAnsi content-stream repertoire).
     */
    fun pdfString(text: String): String {
        val asciiOnly = text.all { it.code in 0x20..0x7E || it == '\n' || it == '\r' || it == '\t' }
        return if (asciiOnly) literalString(text) else hexUtf16(text)
    }

    private fun literalString(text: String): String = buildString {
        append('(')
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append(')')
    }

    /**
     * Encodes [text] as a UTF-16BE big-endian hex string with a leading BOM
     * (`<FEFF…>`), the PDF convention for strings carrying non-ASCII text.
     * Iterating over `Char`s emits UTF-16 code units directly, which is exactly
     * what UTF-16BE needs (surrogate pairs pass through as their two units).
     */
    fun hexUtf16(text: String): String = buildString {
        append("<FEFF")
        for (c in text) {
            val code = c.code
            append(hexByte((code ushr 8) and 0xFF))
            append(hexByte(code and 0xFF))
        }
        append('>')
    }

    private fun hexByte(b: Int): String =
        "${HEX_DIGITS[(b ushr 4) and 0xF]}${HEX_DIGITS[b and 0xF]}"
}
