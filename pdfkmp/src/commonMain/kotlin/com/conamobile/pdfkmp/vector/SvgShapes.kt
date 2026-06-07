package com.conamobile.pdfkmp.vector

/**
 * Converts the SVG basic shape elements (`<rect>`, `<circle>`, `<ellipse>`,
 * `<line>`, `<polyline>`, `<polygon>`) into the same flat [PathCommand]
 * list the path mini-language produces, so the rest of the pipeline only
 * ever deals with paths.
 *
 * Curves (rounded-rect corners, circles, ellipses) are emitted as cubic
 * Béziers using the classic `k = 0.5522847498` quarter-arc handle length —
 * the magic constant `(4/3)·(√2 − 1)` that approximates a quarter circle to
 * within ~0.02%.
 */
internal object SvgShapes {

    /** Cubic-Bézier handle length for a quarter ellipse arc. */
    private const val KAPPA = 0.5522847498307936f

    /**
     * Builds the path for [element] given its local name, or `null` when the
     * element is not a basic shape. Missing numeric attributes default to
     * their SVG initial values (`0`); a shape with zero size yields no
     * commands.
     */
    fun toPath(element: XmlElement): List<PathCommand>? = when (element.localName) {
        "rect" -> rect(element)
        "circle" -> circle(element)
        "ellipse" -> ellipse(element)
        "line" -> line(element)
        "polyline" -> polyline(element, close = false)
        "polygon" -> polyline(element, close = true)
        else -> null
    }

    private fun rect(element: XmlElement): List<PathCommand> {
        val x = element.length("x")
        val y = element.length("y")
        val w = element.length("width")
        val h = element.length("height")
        if (w <= 0f || h <= 0f) return emptyList()

        // rx/ry default to each other when only one is given (per SVG spec),
        // and clamp to half the side so the corners never overlap.
        var rx = element.attribute("rx")?.let(::parseLengthOrNull)
        var ry = element.attribute("ry")?.let(::parseLengthOrNull)
        if (rx == null && ry != null) rx = ry
        if (ry == null && rx != null) ry = rx
        val cornerX = (rx ?: 0f).coerceIn(0f, w / 2f)
        val cornerY = (ry ?: 0f).coerceIn(0f, h / 2f)

        if (cornerX <= 0f || cornerY <= 0f) {
            return listOf(
                PathCommand.MoveTo(x, y),
                PathCommand.LineTo(x + w, y),
                PathCommand.LineTo(x + w, y + h),
                PathCommand.LineTo(x, y + h),
                PathCommand.Close,
            )
        }

        // Rounded rect: start after the top-left corner, walk clockwise,
        // rounding each corner with a single cubic.
        val cx = cornerX * KAPPA
        val cy = cornerY * KAPPA
        return listOf(
            PathCommand.MoveTo(x + cornerX, y),
            PathCommand.LineTo(x + w - cornerX, y),
            PathCommand.CubicTo(x + w - cornerX + cx, y, x + w, y + cornerY - cy, x + w, y + cornerY),
            PathCommand.LineTo(x + w, y + h - cornerY),
            PathCommand.CubicTo(x + w, y + h - cornerY + cy, x + w - cornerX + cx, y + h, x + w - cornerX, y + h),
            PathCommand.LineTo(x + cornerX, y + h),
            PathCommand.CubicTo(x + cornerX - cx, y + h, x, y + h - cornerY + cy, x, y + h - cornerY),
            PathCommand.LineTo(x, y + cornerY),
            PathCommand.CubicTo(x, y + cornerY - cy, x + cornerX - cx, y, x + cornerX, y),
            PathCommand.Close,
        )
    }

    private fun circle(element: XmlElement): List<PathCommand> {
        val r = element.length("r")
        if (r <= 0f) return emptyList()
        return ellipseAt(element.length("cx"), element.length("cy"), r, r)
    }

    private fun ellipse(element: XmlElement): List<PathCommand> {
        val rx = element.length("rx")
        val ry = element.length("ry")
        if (rx <= 0f || ry <= 0f) return emptyList()
        return ellipseAt(element.length("cx"), element.length("cy"), rx, ry)
    }

    /** Four-cubic approximation of a full ellipse, starting at the right vertex. */
    private fun ellipseAt(cx: Float, cy: Float, rx: Float, ry: Float): List<PathCommand> {
        val ox = rx * KAPPA
        val oy = ry * KAPPA
        return listOf(
            PathCommand.MoveTo(cx + rx, cy),
            PathCommand.CubicTo(cx + rx, cy + oy, cx + ox, cy + ry, cx, cy + ry),
            PathCommand.CubicTo(cx - ox, cy + ry, cx - rx, cy + oy, cx - rx, cy),
            PathCommand.CubicTo(cx - rx, cy - oy, cx - ox, cy - ry, cx, cy - ry),
            PathCommand.CubicTo(cx + ox, cy - ry, cx + rx, cy - oy, cx + rx, cy),
            PathCommand.Close,
        )
    }

    private fun line(element: XmlElement): List<PathCommand> = listOf(
        PathCommand.MoveTo(element.length("x1"), element.length("y1")),
        PathCommand.LineTo(element.length("x2"), element.length("y2")),
    )

    /**
     * Parses a `points="x,y x,y …"` list into a poly-line, optionally
     * closing it for `<polygon>`. An odd or empty coordinate list yields no
     * commands rather than throwing — a malformed decorative shape should
     * not sink the whole document.
     */
    private fun polyline(element: XmlElement, close: Boolean): List<PathCommand> {
        val raw = element.attribute("points") ?: return emptyList()
        val numbers = raw.trim()
            .split(Regex("[\\s,]+"))
            .filter { it.isNotEmpty() }
            .map { it.toFloatOrNull() ?: return emptyList() }
        if (numbers.size < 4 || numbers.size % 2 != 0) return emptyList()

        val out = mutableListOf<PathCommand>()
        out += PathCommand.MoveTo(numbers[0], numbers[1])
        var i = 2
        while (i + 1 < numbers.size) {
            out += PathCommand.LineTo(numbers[i], numbers[i + 1])
            i += 2
        }
        if (close) out += PathCommand.Close
        return out
    }

    /** Reads a length attribute, defaulting to `0` when absent or unparseable. */
    private fun XmlElement.length(name: String): Float =
        attribute(name)?.let(::parseLengthOrNull) ?: 0f
}
