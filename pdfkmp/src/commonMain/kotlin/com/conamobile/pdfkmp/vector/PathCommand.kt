package com.conamobile.pdfkmp.vector

/**
 * One step of a vector path, expressed in **absolute** viewport coordinates.
 *
 * The parser in [PathDataParser] converts SVG / Android Vector path data
 * into a list of these — relative commands (`m`, `l`, `c`, …) are folded
 * into their absolute siblings, smooth shorthands (`s`, `t`) are expanded
 * into the equivalent full cubic / quadratic, and horizontal / vertical
 * shortcuts (`h`, `v`) become regular [LineTo]s. Callers therefore only
 * have to handle four primitive cases plus close.
 *
 * Coordinates are in the path's *viewport* (the `viewBox` for SVG, the
 * `viewportWidth` / `viewportHeight` for Android Vector). The renderer
 * scales these into the destination rectangle the user requested.
 */
public sealed interface PathCommand {

    /** Begins a new sub-path at the absolute point. */
    public data class MoveTo(val x: Float, val y: Float) : PathCommand

    /** Adds a straight-line segment from the current point to the absolute point. */
    public data class LineTo(val x: Float, val y: Float) : PathCommand

    /**
     * Adds a cubic Bézier from the current point to `(x, y)` using
     * `(c1x, c1y)` and `(c2x, c2y)` as the off-curve control points.
     */
    public data class CubicTo(
        val c1x: Float,
        val c1y: Float,
        val c2x: Float,
        val c2y: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * Adds a quadratic Bézier from the current point to `(x, y)` using
     * `(cx, cy)` as the single off-curve control point.
     */
    public data class QuadTo(
        val cx: Float,
        val cy: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * Closes the current sub-path with a straight-line segment back to its
     * starting point. Drawing a stroke after [Close] joins cleanly at the
     * corner; without [Close] the last point's stroke ends with a cap.
     */
    public data object Close : PathCommand
}
