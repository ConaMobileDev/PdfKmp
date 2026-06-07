package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.vector.PathCommand
import com.conamobile.pdfkmp.vector.VectorPath

/**
 * Receiver of `freeDraw { ... }` — collects vector paths authored in the
 * node's local coordinate space.
 *
 * Each [path] call produces one fill / stroke unit. Coordinates run from
 * `(0, 0)` at the top-left of the drawing area to `(width, height)` at
 * the bottom-right; the renderer scales them into the node's final
 * rectangle, so drawings stay sharp at any zoom level.
 */
@PdfDsl
public class FreeDrawScope internal constructor() {

    internal val paths: MutableList<VectorPath> = mutableListOf()

    /**
     * Adds one path. Provide [fill] (solid shorthand) or [fillPaint]
     * (gradient — wins over [fill]) to fill the interior, and
     * [strokeColor] + [strokeWidth] to outline it. Both may be combined.
     */
    public fun path(
        fill: PdfColor? = null,
        fillPaint: PdfPaint? = null,
        strokeColor: PdfColor? = null,
        strokeWidth: Float = 0f,
        block: PathScope.() -> Unit,
    ) {
        val scope = PathScope().apply(block)
        if (scope.commands.isEmpty()) return
        paths += VectorPath(
            commands = scope.commands.toList(),
            fill = fillPaint ?: fill?.let { PdfPaint.Solid(it) },
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
        )
    }
}

/**
 * Receiver of [FreeDrawScope.path] — records absolute path commands.
 *
 * The pen model matches every 2D canvas API: [moveTo] starts a new
 * subpath, [lineTo] / [quadTo] / [cubicTo] extend it, [close] joins back
 * to the subpath's starting point.
 */
@PdfDsl
public class PathScope internal constructor() {

    internal val commands: MutableList<PathCommand> = mutableListOf()

    /** Starts a new subpath at `(x, y)`. */
    public fun moveTo(x: Float, y: Float) {
        commands += PathCommand.MoveTo(x, y)
    }

    /** Straight segment from the current point to `(x, y)`. */
    public fun lineTo(x: Float, y: Float) {
        commands += PathCommand.LineTo(x, y)
    }

    /** Quadratic Bézier with control point `(cx, cy)` ending at `(x, y)`. */
    public fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        commands += PathCommand.QuadTo(cx, cy, x, y)
    }

    /** Cubic Bézier with control points `(c1x, c1y)` / `(c2x, c2y)` ending at `(x, y)`. */
    public fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        commands += PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
    }

    /** Closes the current subpath back to its starting point. */
    public fun close() {
        commands += PathCommand.Close
    }

    /** Convenience: an axis-aligned rectangle as a closed subpath. */
    public fun rect(x: Float, y: Float, width: Float, height: Float) {
        moveTo(x, y)
        lineTo(x + width, y)
        lineTo(x + width, y + height)
        lineTo(x, y + height)
        close()
    }
}
