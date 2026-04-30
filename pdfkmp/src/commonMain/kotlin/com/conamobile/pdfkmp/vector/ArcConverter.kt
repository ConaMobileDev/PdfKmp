package com.conamobile.pdfkmp.vector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Converts a single SVG / Android Vector elliptical arc into a sequence of
 * cubic Bézier curves.
 *
 * Implements the W3C SVG endpoint → centre parameterisation conversion
 * (https://www.w3.org/TR/SVG/implnote.html#ArcImplementationNotes), then
 * splits the resulting arc into segments of at most 90° and approximates
 * each one with a cubic Bézier — the standard technique that every modern
 * rasteriser uses.
 *
 * Returned commands are appended to the caller's command list. The first
 * point of the arc is the *current* point so this function does not emit a
 * [PathCommand.MoveTo].
 */
internal object ArcConverter {

    /**
     * Appends cubic Bézier approximations of the arc to [out].
     *
     * @param x0 current point x.
     * @param y0 current point y.
     * @param rx ellipse semi-axis along the local x-axis.
     * @param ry ellipse semi-axis along the local y-axis.
     * @param xAxisRotationDeg rotation of the ellipse, in degrees.
     * @param largeArc `true` for the longer of the two possible arcs.
     * @param sweep `true` to sweep clockwise (positive angle direction).
     * @param x endpoint x.
     * @param y endpoint y.
     */
    fun appendArc(
        out: MutableList<PathCommand>,
        x0: Float, y0: Float,
        rx: Float, ry: Float,
        xAxisRotationDeg: Float,
        largeArc: Boolean, sweep: Boolean,
        x: Float, y: Float,
    ) {
        // Degenerate cases — collapse to a straight line.
        if (x0 == x && y0 == y) return
        if (rx == 0f || ry == 0f) {
            out += PathCommand.LineTo(x, y)
            return
        }

        var rxAbs = abs(rx).toDouble()
        var ryAbs = abs(ry).toDouble()
        val phi = xAxisRotationDeg.toDouble() * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        // Step 1 — translate to the midpoint of the chord and rotate so the
        // ellipse axis aligns with the global x-axis.
        val dx = (x0 - x).toDouble() / 2.0
        val dy = (y0 - y).toDouble() / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy

        // Ensure radii are large enough to accommodate the chord.
        val lambda = (x1p * x1p) / (rxAbs * rxAbs) + (y1p * y1p) / (ryAbs * ryAbs)
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rxAbs *= scale
            ryAbs *= scale
        }

        val rxSq = rxAbs * rxAbs
        val rySq = ryAbs * ryAbs
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        var radicand = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) /
            (rxSq * y1pSq + rySq * x1pSq)
        if (radicand < 0.0) radicand = 0.0
        val factor = sqrt(radicand) * if (largeArc == sweep) -1.0 else 1.0
        val cxp = factor * (rxAbs * y1p) / ryAbs
        val cyp = factor * -(ryAbs * x1p) / rxAbs

        // Step 2 — un-rotate / un-translate to find the centre in the
        // original space.
        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x).toDouble() / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y).toDouble() / 2.0

        // Step 3 — start angle and angle delta.
        val startAngle = vectorAngle(
            ux = 1.0, uy = 0.0,
            vx = (x1p - cxp) / rxAbs, vy = (y1p - cyp) / ryAbs,
        )
        var deltaAngle = vectorAngle(
            ux = (x1p - cxp) / rxAbs, uy = (y1p - cyp) / ryAbs,
            vx = (-x1p - cxp) / rxAbs, vy = (-y1p - cyp) / ryAbs,
        )
        if (!sweep && deltaAngle > 0.0) deltaAngle -= 2.0 * PI
        if (sweep && deltaAngle < 0.0) deltaAngle += 2.0 * PI

        // Step 4 — split into ≤90° segments, each approximated by a cubic.
        val segmentCount = ceil(abs(deltaAngle) / (PI / 2.0)).toInt().coerceAtLeast(1)
        val segmentDelta = deltaAngle / segmentCount
        var theta = startAngle
        var px = x0.toDouble()
        var py = y0.toDouble()

        for (i in 0 until segmentCount) {
            val nextTheta = theta + segmentDelta
            val (sx, sy, ex, ey, c1x, c1y, c2x, c2y) = arcSegmentToCubic(
                cx = cx, cy = cy,
                rx = rxAbs, ry = ryAbs,
                cosPhi = cosPhi, sinPhi = sinPhi,
                theta1 = theta, theta2 = nextTheta,
            )
            // First control point starts from the previous endpoint.
            // px/py kept for chain continuity even though we only consume
            // the segment's own start/end.
            @Suppress("UNUSED_VALUE") run { px = sx; py = sy }
            out += PathCommand.CubicTo(
                c1x = c1x.toFloat(),
                c1y = c1y.toFloat(),
                c2x = c2x.toFloat(),
                c2y = c2y.toFloat(),
                x = ex.toFloat(),
                y = ey.toFloat(),
            )
            px = ex
            py = ey
            theta = nextTheta
        }
    }

    /**
     * Approximates the arc between [theta1] and [theta2] with one cubic
     * Bézier curve.
     *
     * Uses the standard cubic-Bézier-from-arc formula:
     * `α = (4/3) tan((θ2 - θ1) / 4)` for the off-curve handle length.
     */
    private fun arcSegmentToCubic(
        cx: Double, cy: Double,
        rx: Double, ry: Double,
        cosPhi: Double, sinPhi: Double,
        theta1: Double, theta2: Double,
    ): ArcSegment {
        val cosT1 = cos(theta1); val sinT1 = sin(theta1)
        val cosT2 = cos(theta2); val sinT2 = sin(theta2)
        val alpha = 4.0 / 3.0 * tan((theta2 - theta1) / 4.0)

        val sxLocal = rx * cosT1
        val syLocal = ry * sinT1
        val exLocal = rx * cosT2
        val eyLocal = ry * sinT2

        val c1xLocal = sxLocal - alpha * rx * sinT1
        val c1yLocal = syLocal + alpha * ry * cosT1
        val c2xLocal = exLocal + alpha * rx * sinT2
        val c2yLocal = eyLocal - alpha * ry * cosT2

        // Un-rotate from local ellipse space into the user's coord system,
        // then translate by the centre.
        val sx = cosPhi * sxLocal - sinPhi * syLocal + cx
        val sy = sinPhi * sxLocal + cosPhi * syLocal + cy
        val ex = cosPhi * exLocal - sinPhi * eyLocal + cx
        val ey = sinPhi * exLocal + cosPhi * eyLocal + cy
        val c1x = cosPhi * c1xLocal - sinPhi * c1yLocal + cx
        val c1y = sinPhi * c1xLocal + cosPhi * c1yLocal + cy
        val c2x = cosPhi * c2xLocal - sinPhi * c2yLocal + cx
        val c2y = sinPhi * c2xLocal + cosPhi * c2yLocal + cy
        return ArcSegment(sx, sy, ex, ey, c1x, c1y, c2x, c2y)
    }

    /** Signed angle from `(ux, uy)` to `(vx, vy)` measured in radians. */
    private fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val sign = if (ux * vy - uy * vx < 0.0) -1.0 else 1.0
        val dot = (ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))
        val clamped = dot.coerceIn(-1.0, 1.0)
        return sign * kotlin.math.acos(clamped)
    }

    private data class ArcSegment(
        val sx: Double, val sy: Double,
        val ex: Double, val ey: Double,
        val c1x: Double, val c1y: Double,
        val c2x: Double, val c2y: Double,
    )
}
