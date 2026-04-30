package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.GradientStop
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint

/**
 * Top-level entry that turns an XML string into a [VectorImage].
 *
 * Auto-detects whether the input is Android `<vector>` or W3C `<svg>` from
 * the root element name. Both share the SVG path mini-language so the same
 * [PathDataParser] is used for both.
 *
 * Limitations of this first version:
 * - `<g>` group transforms are walked recursively but only the
 *   nested `<path>` elements are honoured. Group-level `transform`
 *   attributes are not applied yet.
 * - Gradients, masks, filters, and animations are ignored — we read the
 *   first solid `fill` / `stroke` colour we find on each path.
 * - Elliptical arcs in path data are rejected by [PathDataParser].
 */
internal object VectorParser {

    fun parse(xml: String): VectorImage {
        val root = MiniXml.parse(xml)
        return when (root.localName) {
            "vector" -> parseAndroidVector(root)
            "svg" -> parseSvg(root)
            else -> throw IllegalArgumentException(
                "Unsupported root element <${root.localName}>. Expected <vector> or <svg>.",
            )
        }
    }

    private fun parseAndroidVector(root: XmlElement): VectorImage {
        val viewportWidth = root.attribute("viewportWidth")?.toFloatOrNull()
            ?: throw VectorParseException("<vector> missing android:viewportWidth")
        val viewportHeight = root.attribute("viewportHeight")?.toFloatOrNull()
            ?: throw VectorParseException("<vector> missing android:viewportHeight")
        val intrinsicWidth = root.attribute("width")?.let(::parseDpDimension) ?: viewportWidth
        val intrinsicHeight = root.attribute("height")?.let(::parseDpDimension) ?: viewportHeight

        val paths = mutableListOf<VectorPath>()
        collectAndroidPaths(root, paths, AffineTransform.Identity)
        return VectorImage(
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            paths = paths,
        )
    }

    private fun collectAndroidPaths(
        element: XmlElement,
        sink: MutableList<VectorPath>,
        parentTransform: AffineTransform = AffineTransform.Identity,
    ) {
        for (child in element.children) {
            when (child.localName) {
                "path" -> {
                    val pathData = child.attribute("pathData") ?: continue
                    val fill = child.attribute("fillColor")?.let(::parseHexColor)?.let(PdfPaint::Solid)
                    val stroke = child.attribute("strokeColor")?.let(::parseHexColor)
                    val strokeWidth = child.attribute("strokeWidth")?.toFloatOrNull() ?: 0f
                    val raw = PathDataParser.parse(pathData)
                    val transformed = if (parentTransform == AffineTransform.Identity) raw
                    else raw.map { applyTransform(it, parentTransform) }
                    sink += VectorPath(
                        commands = transformed,
                        fill = fill,
                        strokeColor = stroke,
                        strokeWidth = strokeWidth,
                    )
                }
                "group" -> {
                    val groupTransform = androidGroupTransform(child)
                    collectAndroidPaths(child, sink, parentTransform.multiply(groupTransform))
                }
                else -> Unit
            }
        }
    }

    /**
     * Builds the affine transform implied by an Android Vector `<group>`'s
     * `translateX/Y`, `scaleX/Y`, `rotation`, `pivotX/Y` attributes.
     */
    private fun androidGroupTransform(group: XmlElement): AffineTransform {
        val translateX = group.attribute("translateX")?.toFloatOrNull() ?: 0f
        val translateY = group.attribute("translateY")?.toFloatOrNull() ?: 0f
        val scaleX = group.attribute("scaleX")?.toFloatOrNull() ?: 1f
        val scaleY = group.attribute("scaleY")?.toFloatOrNull() ?: 1f
        val rotation = group.attribute("rotation")?.toFloatOrNull() ?: 0f
        val pivotX = group.attribute("pivotX")?.toFloatOrNull() ?: 0f
        val pivotY = group.attribute("pivotY")?.toFloatOrNull() ?: 0f

        return AffineTransform.translate(translateX, translateY)
            .multiply(AffineTransform.translate(pivotX, pivotY))
            .multiply(AffineTransform.rotate(rotation))
            .multiply(AffineTransform.scale(scaleX, scaleY))
            .multiply(AffineTransform.translate(-pivotX, -pivotY))
    }

    private fun parseSvg(root: XmlElement): VectorImage {
        val viewBoxAttr = root.attribute("viewBox")
        val viewBox = viewBoxAttr?.let(::parseViewBox) ?: ViewBox(
            x = 0f,
            y = 0f,
            width = root.attribute("width")?.let(::parseDimension) ?: 0f,
            height = root.attribute("height")?.let(::parseDimension) ?: 0f,
        )
        val viewportWidth = viewBox.width.takeIf { it > 0f } ?: 24f
        val viewportHeight = viewBox.height.takeIf { it > 0f } ?: 24f
        val intrinsicWidth = root.attribute("width")?.let(::parseDimension) ?: viewportWidth
        val intrinsicHeight = root.attribute("height")?.let(::parseDimension) ?: viewportHeight

        val gradients = mutableMapOf<String, PdfPaint>()
        collectSvgGradients(root, gradients)

        val paths = mutableListOf<VectorPath>()
        collectSvgPaths(
            element = root,
            defaultFill = PdfColor.Black,
            sink = paths,
            originX = viewBox.x,
            originY = viewBox.y,
            gradients = gradients,
        )
        return VectorImage(
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            paths = paths,
        )
    }

    private fun collectSvgPaths(
        element: XmlElement,
        defaultFill: PdfColor,
        sink: MutableList<VectorPath>,
        originX: Float,
        originY: Float,
        gradients: Map<String, PdfPaint>,
        parentTransform: AffineTransform = AffineTransform.Identity,
    ) {
        for (child in element.children) {
            when (child.localName) {
                "path" -> {
                    val d = child.attribute("d") ?: continue
                    val fillAttr = child.attribute("fill")
                    val fill = resolveSvgFill(fillAttr, defaultFill, gradients)
                    val stroke = child.attribute("stroke")?.let(::parseSvgColor)
                    val strokeWidth = child.attribute("stroke-width")?.toFloatOrNull() ?: 0f
                    val pathTransform = TransformParser.parse(child.attribute("transform"))
                    val combined = parentTransform.multiply(pathTransform)
                    val raw = PathDataParser.parse(d)
                    val transformed = raw.map { command ->
                        val withGroupTransform = applyTransform(command, combined)
                        if (originX == 0f && originY == 0f) withGroupTransform
                        else translateCommand(withGroupTransform, -originX, -originY)
                    }
                    sink += VectorPath(
                        commands = transformed,
                        fill = fill,
                        strokeColor = stroke,
                        strokeWidth = strokeWidth,
                    )
                }
                "g" -> {
                    val groupFill = child.attribute("fill")?.let(::parseSvgColor) ?: defaultFill
                    val groupTransform = TransformParser.parse(child.attribute("transform"))
                    collectSvgPaths(
                        element = child,
                        defaultFill = groupFill,
                        sink = sink,
                        originX = originX,
                        originY = originY,
                        gradients = gradients,
                        parentTransform = parentTransform.multiply(groupTransform),
                    )
                }
                else -> Unit
            }
        }
    }

    /**
     * Resolves an SVG `fill` attribute into a [PdfPaint]. Recognised
     * shapes:
     *
     * - `none` → no fill
     * - `url(#gradId)` → gradient looked up in [gradients]
     * - any colour value → solid fill
     */
    private fun resolveSvgFill(
        attr: String?,
        default: PdfColor,
        gradients: Map<String, PdfPaint>,
    ): PdfPaint? {
        if (attr == null) return PdfPaint.Solid(default)
        val trimmed = attr.trim()
        if (trimmed.equals("none", ignoreCase = true)) return null
        if (trimmed.startsWith("url(")) {
            val end = trimmed.indexOf(')')
            if (end < 0) return PdfPaint.Solid(default)
            var ref = trimmed.substring(4, end).trim()
            if (ref.startsWith("#")) ref = ref.substring(1)
            return gradients[ref] ?: PdfPaint.Solid(default)
        }
        val color = parseSvgColor(trimmed) ?: default
        return PdfPaint.Solid(color)
    }

    /**
     * Walks every `<linearGradient>` and `<radialGradient>` element under
     * [root] and registers its resolved [PdfPaint] under its `id` so that
     * later `<path fill="url(#id)">` lookups succeed.
     *
     * The walk is shallow — gradient definitions are typically inside
     * `<defs>`, but SVG also permits them at the root or anywhere else.
     */
    private fun collectSvgGradients(root: XmlElement, sink: MutableMap<String, PdfPaint>) {
        for (child in root.children) {
            when (child.localName) {
                "linearGradient" -> child.attribute("id")?.let { id ->
                    sink[id] = parseLinearGradient(child)
                }
                "radialGradient" -> child.attribute("id")?.let { id ->
                    sink[id] = parseRadialGradient(child)
                }
                else -> collectSvgGradients(child, sink)
            }
        }
    }

    private fun parseLinearGradient(element: XmlElement): PdfPaint.LinearGradient {
        val x1 = element.attribute("x1")?.let(::parseDimension) ?: 0f
        val y1 = element.attribute("y1")?.let(::parseDimension) ?: 0f
        val x2 = element.attribute("x2")?.let(::parseDimension) ?: 0f
        val y2 = element.attribute("y2")?.let(::parseDimension) ?: 0f
        return PdfPaint.LinearGradient(
            startX = x1, startY = y1,
            endX = x2, endY = y2,
            stops = parseStops(element),
        )
    }

    private fun parseRadialGradient(element: XmlElement): PdfPaint.RadialGradient {
        val cx = element.attribute("cx")?.let(::parseDimension) ?: 0.5f
        val cy = element.attribute("cy")?.let(::parseDimension) ?: 0.5f
        val r = element.attribute("r")?.let(::parseDimension) ?: 0.5f
        return PdfPaint.RadialGradient(
            centerX = cx, centerY = cy, radius = r,
            stops = parseStops(element),
        )
    }

    private fun parseStops(element: XmlElement): List<GradientStop> {
        val stops = mutableListOf<GradientStop>()
        for (child in element.children) {
            if (child.localName == "stop") {
                val offset = child.attribute("offset")?.let { parseStopOffset(it) } ?: stops.size.toFloat()
                val color = child.attribute("stop-color")?.let(::parseSvgColor)
                    ?: child.attribute("color")?.let(::parseSvgColor)
                    ?: PdfColor.Black
                stops += GradientStop(offset = offset, color = color)
            }
        }
        return stops
    }

    /** SVG accepts `0.5` or `50%` for stop offsets. */
    private fun parseStopOffset(value: String): Float {
        val trimmed = value.trim()
        return if (trimmed.endsWith("%")) {
            (trimmed.dropLast(1).toFloatOrNull() ?: 0f) / 100f
        } else {
            trimmed.toFloatOrNull() ?: 0f
        }
    }

    private data class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float)

    private fun parseViewBox(value: String): ViewBox? {
        val parts = value.trim().split(Regex("[\\s,]+"))
        if (parts.size != 4) return null
        return ViewBox(
            x = parts[0].toFloatOrNull() ?: return null,
            y = parts[1].toFloatOrNull() ?: return null,
            width = parts[2].toFloatOrNull() ?: return null,
            height = parts[3].toFloatOrNull() ?: return null,
        )
    }

    private fun parseDpDimension(value: String): Float = parseDimension(value)

    /** Strips a trailing unit (`dp`, `px`, `pt`, `mm`, `%`) and parses the number part. */
    private fun parseDimension(value: String): Float {
        val trimmed = value.trim()
        val numberEnd = trimmed.indexOfFirst { !it.isDigit() && it != '.' && it != '-' && it != '+' }
        val numericPart = if (numberEnd < 0) trimmed else trimmed.substring(0, numberEnd)
        return numericPart.toFloatOrNull() ?: 0f
    }

    /** Parses `#RRGGBB`, `#AARRGGBB`, or `#RGB`. Throws on malformed input. */
    private fun parseHexColor(value: String): PdfColor? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("#")) return null
        val hex = trimmed.substring(1)
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                PdfColor(r / 255f, g / 255f, b / 255f, 1f)
            }
            6 -> {
                val rgb = hex.toLong(16)
                PdfColor.fromRgb(rgb)
            }
            8 -> {
                val argb = hex.toLong(16)
                PdfColor.fromArgb(argb)
            }
            else -> null
        }
    }

    /** SVG colour values: `#RGB`, `#RRGGBB`, `none`, or named keywords (limited). */
    private fun parseSvgColor(value: String): PdfColor? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none" || trimmed.isEmpty()) return null
        if (trimmed.startsWith("#")) return parseHexColor(trimmed)
        return when (trimmed) {
            "black" -> PdfColor.Black
            "white" -> PdfColor.White
            "red" -> PdfColor.Red
            "green" -> PdfColor.Green
            "blue" -> PdfColor.Blue
            "gray", "grey" -> PdfColor.Gray
            else -> PdfColor.Black
        }
    }

    private fun translateCommand(cmd: PathCommand, dx: Float, dy: Float): PathCommand = when (cmd) {
        is PathCommand.MoveTo -> PathCommand.MoveTo(cmd.x + dx, cmd.y + dy)
        is PathCommand.LineTo -> PathCommand.LineTo(cmd.x + dx, cmd.y + dy)
        is PathCommand.CubicTo -> PathCommand.CubicTo(
            cmd.c1x + dx, cmd.c1y + dy,
            cmd.c2x + dx, cmd.c2y + dy,
            cmd.x + dx, cmd.y + dy,
        )
        is PathCommand.QuadTo -> PathCommand.QuadTo(cmd.cx + dx, cmd.cy + dy, cmd.x + dx, cmd.y + dy)
        is PathCommand.Close -> cmd
    }
}
