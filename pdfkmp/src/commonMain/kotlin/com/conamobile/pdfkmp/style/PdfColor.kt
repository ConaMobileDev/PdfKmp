package com.conamobile.pdfkmp.style

/**
 * RGBA color, components in `0f..1f`.
 */
public data class PdfColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    public companion object {
        public val Black: PdfColor = PdfColor(0f, 0f, 0f)
        public val White: PdfColor = PdfColor(1f, 1f, 1f)
        public val Gray: PdfColor = PdfColor(0.5f, 0.5f, 0.5f)
        public val LightGray: PdfColor = PdfColor(0.83f, 0.83f, 0.83f)
        public val DarkGray: PdfColor = PdfColor(0.25f, 0.25f, 0.25f)
        public val Red: PdfColor = PdfColor(1f, 0f, 0f)
        public val Green: PdfColor = PdfColor(0f, 0.6f, 0f)
        public val Blue: PdfColor = PdfColor(0f, 0f, 1f)
        public val Transparent: PdfColor = PdfColor(0f, 0f, 0f, 0f)

        /** Creates a color from a `0xAARRGGBB` integer. */
        public fun fromArgb(argb: Long): PdfColor {
            val a = ((argb shr 24) and 0xFF) / 255f
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return PdfColor(r, g, b, a)
        }

        /** Creates a color from a `0xRRGGBB` integer with full opacity. */
        public fun fromRgb(rgb: Long): PdfColor = fromArgb(0xFF_00_00_00L or (rgb and 0xFFFFFFL))

        /**
         * Parses a hex color string. Accepts `#RGB`, `#RRGGBB`, `#AARRGGBB`,
         * or the same forms without the leading `#`. Useful when the color
         * comes from a design tool ("paste this hex into the code").
         *
         * @throws IllegalArgumentException when [hex] is not 3, 6, or 8 hex digits.
         */
        public fun fromHex(hex: String): PdfColor {
            val cleaned = hex.removePrefix("#")
            return when (cleaned.length) {
                3 -> {
                    // #RGB → expand each digit (e.g. "F0A" → "FF00AA")
                    val r = cleaned[0].toString().repeat(2).toInt(16)
                    val g = cleaned[1].toString().repeat(2).toInt(16)
                    val b = cleaned[2].toString().repeat(2).toInt(16)
                    PdfColor(r / 255f, g / 255f, b / 255f)
                }
                6 -> fromRgb(cleaned.toLong(16))
                8 -> fromArgb(cleaned.toLong(16))
                else -> throw IllegalArgumentException(
                    "Hex color must be 3, 6, or 8 digits (got '$hex')",
                )
            }
        }
    }

    /**
     * Returns a copy of this color with the given [alpha] (0f opaque-less,
     * 1f fully opaque). Convenient for fading existing palette colors:
     *
     * ```
     * val ghostBlue = PdfColor.Blue.withAlpha(0.2f)
     * ```
     */
    public fun withAlpha(alpha: Float): PdfColor = copy(alpha = alpha)
}
