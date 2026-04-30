package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint

/**
 * Resolved vector graphic ready for layout and drawing.
 *
 * Produced by [parse] (or one of its convenience overloads), which accepts
 * either Android `<vector>` XML or standard `<svg>` XML and returns an
 * instance of this class. The renderer scales [paths] from the
 * `(viewportWidth × viewportHeight)` viewport into whatever destination
 * rectangle the user requested through the `vector(...)` DSL.
 *
 * Two coordinate systems are involved:
 *
 * - **Viewport** (`viewportWidth × viewportHeight`) — the path data is
 *   authored in this space. Most icons use 24×24 because that's the Android
 *   Material Design canvas size.
 * - **Intrinsic size** (`width × height`) — the size the source XML
 *   suggests for the icon. The DSL falls back to this when neither
 *   `width` nor `height` is supplied at the call site.
 */
public data class VectorImage(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val intrinsicWidth: Float,
    val intrinsicHeight: Float,
    val paths: List<VectorPath>,
) {

    public companion object {

        /**
         * Parses [xml] as either Android Vector or SVG and returns the
         * resolved image.
         *
         * The format is auto-detected from the root element name —
         * `<vector>` for the Android format, `<svg>` for SVG. Throws
         * [IllegalArgumentException] if the root element is neither, or
         * [VectorParseException] if the XML / path data is malformed.
         *
         * @param xml the source XML as a string. Typically loaded from
         *   the app's assets, the network, or hard-coded for icons.
         */
        public fun parse(xml: String): VectorImage = VectorParser.parse(xml)
    }
}

/**
 * One path inside a [VectorImage].
 *
 * @property commands the path's strokes and fills as a sequence of absolute
 *   [PathCommand]s. The renderer never inspects this list — it just hands
 *   it to the platform canvas.
 * @property fill paint used to fill the path interior. Can be a solid
 *   colour ([PdfPaint.Solid]) or a gradient
 *   ([PdfPaint.LinearGradient] / [PdfPaint.RadialGradient]). `null`
 *   means do not fill.
 * @property strokeColor solid stroke colour, or `null` to skip stroking.
 *   Strokes are always solid colours; gradient strokes are not yet
 *   supported.
 * @property strokeWidth stroke width in viewport units; ignored when
 *   [strokeColor] is `null`.
 */
public data class VectorPath(
    val commands: List<PathCommand>,
    val fill: PdfPaint? = null,
    val strokeColor: PdfColor? = null,
    val strokeWidth: Float = 0f,
) {
    /**
     * Convenience accessor returning the fill colour when [fill] is a
     * [PdfPaint.Solid], or `null` otherwise. Useful from code that hasn't
     * yet been updated to handle gradients.
     */
    public val fillColor: PdfColor?
        get() = (fill as? PdfPaint.Solid)?.color
}

/** Thrown when [VectorImage.parse] fails. */
public class VectorParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
