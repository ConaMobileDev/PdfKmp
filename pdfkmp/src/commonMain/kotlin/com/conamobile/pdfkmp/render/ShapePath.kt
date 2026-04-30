package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Builds the [PathCommand] sequence approximating an ellipse fitted into
 * the rectangle `(x, y, width, height)` using four cubic Béziers.
 *
 * The standard `α = 4/3 × (√2 − 1) ≈ 0.5523` handle length yields a
 * smooth curve indistinguishable from a true ellipse at every PDF zoom
 * level a reader is likely to use.
 *
 * Pass `width == height` to draw a circle.
 */
internal fun buildEllipsePath(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): List<PathCommand> {
    if (width <= 0f || height <= 0f) return emptyList()
    val rx = width / 2f
    val ry = height / 2f
    val cx = x + rx
    val cy = y + ry
    val k = 0.5522847498f
    val ox = rx * k
    val oy = ry * k
    return listOf(
        PathCommand.MoveTo(cx, y),
        PathCommand.CubicTo(cx + ox, y, cx + rx, cy - oy, cx + rx, cy),
        PathCommand.CubicTo(cx + rx, cy + oy, cx + ox, cy + ry, cx, cy + ry),
        PathCommand.CubicTo(cx - ox, cy + ry, cx - rx, cy + oy, cx - rx, cy),
        PathCommand.CubicTo(cx - rx, cy - oy, cx - ox, y, cx, y),
        PathCommand.Close,
    )
}
