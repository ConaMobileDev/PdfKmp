package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Builds the [PathCommand] sequence for a rectangle whose four corners
 * may have different radii.
 *
 * Each corner is approximated by a single cubic Bézier with the standard
 * `α = 4/3 × (√2 − 1) ≈ 0.5523` handle length — the same trick every
 * vector renderer uses for 90° elliptical arcs. The resulting path
 * starts at the top-edge mid-line and traces clockwise.
 *
 * Used by the per-corner draw / stroke / clip code paths in the
 * renderer when a container's `cornerRadiusEach` overrides the uniform
 * corner radius.
 *
 * @param x left edge.
 * @param y top edge.
 * @param width rectangle width.
 * @param height rectangle height.
 * @param tl top-left radius (clamped to `width / 2`).
 * @param tr top-right radius.
 * @param bl bottom-left radius.
 * @param br bottom-right radius.
 */
internal fun buildRoundedRectPath(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tl: Float,
    tr: Float,
    bl: Float,
    br: Float,
): List<PathCommand> {
    val maxRadius = minOf(width, height) / 2f
    val tlR = tl.coerceIn(0f, maxRadius)
    val trR = tr.coerceIn(0f, maxRadius)
    val blR = bl.coerceIn(0f, maxRadius)
    val brR = br.coerceIn(0f, maxRadius)

    // Cubic Bézier handle length for a 90° arc on a unit circle.
    val k = 0.5522847498f

    val left = x
    val top = y
    val right = x + width
    val bottom = y + height

    val out = mutableListOf<PathCommand>()
    // Start at the top edge just past the top-left corner.
    out += PathCommand.MoveTo(left + tlR, top)
    // Top edge to the start of the top-right corner.
    out += PathCommand.LineTo(right - trR, top)
    if (trR > 0f) {
        out += PathCommand.CubicTo(
            c1x = right - trR + trR * k, c1y = top,
            c2x = right, c2y = top + trR - trR * k,
            x = right, y = top + trR,
        )
    }
    // Right edge.
    out += PathCommand.LineTo(right, bottom - brR)
    if (brR > 0f) {
        out += PathCommand.CubicTo(
            c1x = right, c1y = bottom - brR + brR * k,
            c2x = right - brR + brR * k, c2y = bottom,
            x = right - brR, y = bottom,
        )
    }
    // Bottom edge.
    out += PathCommand.LineTo(left + blR, bottom)
    if (blR > 0f) {
        out += PathCommand.CubicTo(
            c1x = left + blR - blR * k, c1y = bottom,
            c2x = left, c2y = bottom - blR + blR * k,
            x = left, y = bottom - blR,
        )
    }
    // Left edge.
    out += PathCommand.LineTo(left, top + tlR)
    if (tlR > 0f) {
        out += PathCommand.CubicTo(
            c1x = left, c1y = top + tlR - tlR * k,
            c2x = left + tlR - tlR * k, c2y = top,
            x = left + tlR, y = top,
        )
    }
    out += PathCommand.Close
    return out
}
