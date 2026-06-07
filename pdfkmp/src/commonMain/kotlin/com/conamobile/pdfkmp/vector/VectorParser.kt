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
 * SVG subset supported:
 * - Shape elements: `<path>`, `<rect>` (incl. `rx`/`ry` rounded corners),
 *   `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>` — all
 *   converted to the common [PathCommand] list (see [SvgShapes]).
 * - `<g>` groups with `transform` inheritance and cascading presentation
 *   state; nested groups compose. `transform` on a shape composes on top of
 *   the group chain.
 * - Presentation attributes and inline `style="fill:…;stroke:…"` CSS:
 *   `fill`, `stroke`, `stroke-width`, `opacity`, `fill-opacity`,
 *   `stroke-opacity`. Colours accept `#hex` (3/6/8), `rgb(...)`, and the
 *   common named colours (see [SvgColor]). Inline style wins over the
 *   matching presentation attribute.
 * - `viewBox` with `min-x`/`min-y` offsets, `width`/`height` with `px` or
 *   no unit, falling back to the viewBox dimensions.
 * - `<linearGradient>` / `<radialGradient>` referenced via `fill="url(#id)"`.
 *
 * Gracefully ignored (never crash): `<defs>`, `<title>`, `<desc>`,
 * `<style>`, `<use>`, and any unknown element. Masks, filters, clip-paths,
 * patterns, text, and animations are not rendered.
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
                    val fill = child.attribute("fillColor")?.let(SvgColor::parse)?.let(PdfPaint::Solid)
                    val stroke = child.attribute("strokeColor")?.let(SvgColor::parse)
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
        // `width`/`height` expressed as a percentage are relative to the
        // (here unknown) containing viewport, so we ignore them and fall
        // back to the viewBox dimensions instead.
        val widthAttr = root.attribute("width")?.takeUnless { it.trim().endsWith("%") }
        val heightAttr = root.attribute("height")?.takeUnless { it.trim().endsWith("%") }

        val viewBoxAttr = root.attribute("viewBox")
        val viewBox = viewBoxAttr?.let(::parseViewBox) ?: ViewBox(
            x = 0f,
            y = 0f,
            width = widthAttr?.let(::parseDimension) ?: 0f,
            height = heightAttr?.let(::parseDimension) ?: 0f,
        )
        val viewportWidth = viewBox.width.takeIf { it > 0f } ?: 24f
        val viewportHeight = viewBox.height.takeIf { it > 0f } ?: 24f
        val intrinsicWidth = widthAttr?.let(::parseDimension) ?: viewportWidth
        val intrinsicHeight = heightAttr?.let(::parseDimension) ?: viewportHeight

        val gradients = mutableMapOf<String, PdfPaint>()
        collectSvgGradients(root, gradients)

        val paths = mutableListOf<VectorPath>()
        collectSvgPaths(
            element = root,
            inheritedStyle = resolveStyle(root, SvgStyle.Root),
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

    /**
     * Recursively walks the SVG tree, emitting one [VectorPath] per drawable
     * shape with its resolved paint and the composed transform applied.
     *
     * [inheritedStyle] carries the cascading presentation state from the
     * ancestor `<g>` chain; [parentTransform] carries the composed
     * coordinate transform. Container / metadata elements (`<defs>`,
     * `<title>`, `<style>`, `<use>`, …) are skipped.
     */
    private fun collectSvgPaths(
        element: XmlElement,
        inheritedStyle: SvgStyle,
        sink: MutableList<VectorPath>,
        originX: Float,
        originY: Float,
        gradients: Map<String, PdfPaint>,
        parentTransform: AffineTransform = AffineTransform.Identity,
    ) {
        for (child in element.children) {
            when (child.localName) {
                // Pure metadata / definition containers — never drawn.
                "defs", "title", "desc", "style", "metadata", "symbol",
                "linearGradient", "radialGradient", "use", "clipPath", "mask",
                "pattern", "filter",
                -> Unit
                "g" -> {
                    val groupStyle = resolveStyle(child, inheritedStyle)
                    val groupTransform = TransformParser.parse(child.attribute("transform"))
                    collectSvgPaths(
                        element = child,
                        inheritedStyle = groupStyle,
                        sink = sink,
                        originX = originX,
                        originY = originY,
                        gradients = gradients,
                        parentTransform = parentTransform.multiply(groupTransform),
                    )
                }
                "path" -> {
                    val d = child.attribute("d") ?: continue
                    emitShape(
                        element = child,
                        rawCommands = PathDataParser.parse(d),
                        inheritedStyle = inheritedStyle,
                        sink = sink,
                        originX = originX,
                        originY = originY,
                        gradients = gradients,
                        parentTransform = parentTransform,
                    )
                }
                "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
                    val raw = SvgShapes.toPath(child) ?: continue
                    if (raw.isEmpty()) continue
                    emitShape(
                        element = child,
                        rawCommands = raw,
                        inheritedStyle = inheritedStyle,
                        sink = sink,
                        originX = originX,
                        originY = originY,
                        gradients = gradients,
                        parentTransform = parentTransform,
                    )
                }
                // Unknown element: ignore the element itself but still walk
                // its children so a wrapper like <a> doesn't hide its content.
                else -> collectSvgPaths(
                    element = child,
                    inheritedStyle = resolveStyle(child, inheritedStyle),
                    sink = sink,
                    originX = originX,
                    originY = originY,
                    gradients = gradients,
                    parentTransform = parentTransform.multiply(
                        TransformParser.parse(child.attribute("transform")),
                    ),
                )
            }
        }
    }

    /**
     * Resolves [element]'s style on top of [inheritedStyle], applies the
     * element + ancestor transforms (and the viewBox origin shift) to
     * [rawCommands], and appends the resulting [VectorPath] to [sink].
     */
    private fun emitShape(
        element: XmlElement,
        rawCommands: List<PathCommand>,
        inheritedStyle: SvgStyle,
        sink: MutableList<VectorPath>,
        originX: Float,
        originY: Float,
        gradients: Map<String, PdfPaint>,
        parentTransform: AffineTransform,
    ) {
        val style = resolveStyle(element, inheritedStyle)
        val combined = parentTransform.multiply(TransformParser.parse(element.attribute("transform")))
        val transformed = rawCommands.map { command ->
            val withTransform = applyTransform(command, combined)
            if (originX == 0f && originY == 0f) withTransform
            else translateCommand(withTransform, -originX, -originY)
        }
        val stroke = style.resolveStroke()
        sink += VectorPath(
            commands = transformed,
            fill = style.resolveFill(gradients),
            strokeColor = stroke,
            strokeWidth = if (stroke != null) style.resolveStrokeWidth() else 0f,
        )
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
                val color = child.attribute("stop-color")?.let(SvgColor::parse)
                    ?: child.attribute("color")?.let(SvgColor::parse)
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
