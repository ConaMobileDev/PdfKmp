package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.PdfColor

/**
 * Resolver for SVG `<color>` values used by `fill`, `stroke`, and
 * `stop-color`.
 *
 * Kept separate from [VectorParser] because colour resolution is a
 * self-contained concern shared by paths and every shape element, and the
 * named-colour table is large enough to deserve its own home.
 *
 * Recognised forms:
 * - `none` / `transparent` → `null` (no paint)
 * - `currentColor` → `null` (no inheritable "current colour" concept here)
 * - `#RGB`, `#RRGGBB`, `#AARRGGBB` (and the same without `#`)
 * - `rgb(r, g, b)` and `rgb(r%, g%, b%)`
 * - the ~20 most common CSS named colours (see [NAMED])
 */
internal object SvgColor {

    /**
     * Parses an SVG colour token into a [PdfColor], or `null` for the
     * "no paint" keywords (`none`, `transparent`) and for unrecognised
     * values.
     *
     * Returning `null` rather than throwing on an unknown keyword keeps
     * malformed-but-harmless documents rendering instead of failing the
     * whole parse — the SVG spec itself treats unknown paint as "ignore".
     */
    fun parse(value: String?): PdfColor? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower == "none" || lower == "transparent" || lower == "currentcolor") return null
        if (trimmed.startsWith("#")) return parseHex(trimmed)
        if (lower.startsWith("rgb")) return parseRgb(lower)
        return NAMED[lower]
    }

    /**
     * Parses `#RGB`, `#RRGGBB`, or `#AARRGGBB` (leading `#` required by the
     * caller). Returns `null` on any malformed length / digit so callers can
     * fall back gracefully.
     */
    fun parseHex(value: String): PdfColor? {
        val hex = value.trim().removePrefix("#")
        return when (hex.length) {
            3 -> {
                val r = hex[0].hexOrNull() ?: return null
                val g = hex[1].hexOrNull() ?: return null
                val b = hex[2].hexOrNull() ?: return null
                PdfColor((r * 17) / 255f, (g * 17) / 255f, (b * 17) / 255f, 1f)
            }
            6 -> hex.toLongOrNull(16)?.let(PdfColor::fromRgb)
            8 -> hex.toLongOrNull(16)?.let(PdfColor::fromArgb)
            else -> null
        }
    }

    /**
     * Parses `rgb(...)` with either 0–255 integer channels or `%` channels.
     * Alpha is always opaque — `rgba()` alpha is folded in by the caller via
     * the `*-opacity` presentation attributes instead.
     */
    private fun parseRgb(value: String): PdfColor? {
        val open = value.indexOf('(')
        val close = value.indexOf(')')
        if (open < 0 || close < 0 || close < open) return null
        val parts = value.substring(open + 1, close)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val channels = parts.take(3).map { part ->
            if (part.endsWith("%")) {
                val pct = part.dropLast(1).toFloatOrNull() ?: return null
                (pct / 100f).coerceIn(0f, 1f)
            } else {
                val raw = part.toFloatOrNull() ?: return null
                (raw / 255f).coerceIn(0f, 1f)
            }
        }
        return PdfColor(channels[0], channels[1], channels[2], 1f)
    }

    private fun Char.hexOrNull(): Int? =
        if ((this in '0'..'9') || (this in 'a'..'f') || (this in 'A'..'F')) digitToInt(16) else null

    /** The ~20 most common CSS named colours used by real-world SVG icons. */
    private val NAMED: Map<String, PdfColor> = mapOf(
        "black" to PdfColor(0f, 0f, 0f),
        "white" to PdfColor(1f, 1f, 1f),
        "red" to PdfColor(1f, 0f, 0f),
        // CSS `green` is the dark 0,128,0 — not PdfColor.Green (which is 0,0.6,0).
        "green" to PdfColor(0f, 128f / 255f, 0f),
        "blue" to PdfColor(0f, 0f, 1f),
        "gray" to PdfColor(128f / 255f, 128f / 255f, 128f / 255f),
        "grey" to PdfColor(128f / 255f, 128f / 255f, 128f / 255f),
        "yellow" to PdfColor(1f, 1f, 0f),
        "orange" to PdfColor(1f, 165f / 255f, 0f),
        "purple" to PdfColor(128f / 255f, 0f, 128f / 255f),
        "pink" to PdfColor(1f, 192f / 255f, 203f / 255f),
        "brown" to PdfColor(165f / 255f, 42f / 255f, 42f / 255f),
        "cyan" to PdfColor(0f, 1f, 1f),
        "magenta" to PdfColor(1f, 0f, 1f),
        "lime" to PdfColor(0f, 1f, 0f),
        "navy" to PdfColor(0f, 0f, 128f / 255f),
        "teal" to PdfColor(0f, 128f / 255f, 128f / 255f),
        "silver" to PdfColor(192f / 255f, 192f / 255f, 192f / 255f),
        "maroon" to PdfColor(128f / 255f, 0f, 0f),
        "olive" to PdfColor(128f / 255f, 128f / 255f, 0f),
    )
}
