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
    }
}
