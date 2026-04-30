package com.conamobile.pdfkmp.style

/**
 * Visual recipe used to fill a vector path. Either a flat colour or a
 * smooth gradient.
 *
 * Modeled as a sealed interface so platform backends pattern-match each
 * variant against the matching native primitive: solid colours go through
 * `Paint.color` (Android) / `CGContextSetRGBFillColor` (iOS), gradients go
 * through `LinearGradient` / `CGContextDrawLinearGradient`.
 */
public sealed interface PdfPaint {

    /** Single solid colour fill. */
    public data class Solid(val color: PdfColor) : PdfPaint

    /**
     * Linear gradient from `(startX, startY)` to `(endX, endY)`,
     * interpolating through [stops] in source order.
     *
     * Coordinates are in the same space as the path commands they fill —
     * i.e. the vector's viewport. The renderer scales both together when
     * mapping into the destination rectangle.
     */
    public data class LinearGradient(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val stops: List<GradientStop>,
    ) : PdfPaint

    /**
     * Radial gradient centred at `(centerX, centerY)` with the given
     * [radius], interpolating from the first stop at the centre to the
     * last stop at the perimeter through [stops] in source order.
     */
    public data class RadialGradient(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val stops: List<GradientStop>,
    ) : PdfPaint

    public companion object {
        /**
         * Two-stop linear gradient running from `(startX, startY)` to
         * `(endX, endY)`. Convenience for the common "fade between two
         * colours" case.
         */
        public fun linearGradient(
            from: PdfColor,
            to: PdfColor,
            startX: Float = 0f,
            startY: Float = 0f,
            endX: Float,
            endY: Float,
        ): LinearGradient = LinearGradient(
            startX = startX, startY = startY,
            endX = endX, endY = endY,
            stops = listOf(GradientStop(0f, from), GradientStop(1f, to)),
        )

        /**
         * Two-stop radial gradient centred at `(centerX, centerY)` with
         * the given [radius]. The centre takes [from], the perimeter
         * takes [to].
         */
        public fun radialGradient(
            from: PdfColor,
            to: PdfColor,
            centerX: Float,
            centerY: Float,
            radius: Float,
        ): RadialGradient = RadialGradient(
            centerX = centerX, centerY = centerY, radius = radius,
            stops = listOf(GradientStop(0f, from), GradientStop(1f, to)),
        )
    }
}

/**
 * One colour stop along a gradient.
 *
 * @property offset position along the gradient axis, in `0f..1f`. The
 *   first stop is normally `0f` and the last `1f`; intermediate values
 *   determine the colour at that point.
 * @property color resolved colour at this stop.
 */
public data class GradientStop(
    val offset: Float,
    val color: PdfColor,
)
