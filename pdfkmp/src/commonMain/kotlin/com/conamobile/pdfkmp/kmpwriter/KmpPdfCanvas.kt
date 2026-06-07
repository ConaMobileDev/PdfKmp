package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.render.PdfCanvas
import com.conamobile.pdfkmp.render.buildRoundedRectPath
import com.conamobile.pdfkmp.style.GradientStop
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * [PdfCanvas] that emits raw PDF content-stream operators into a [KmpPage],
 * the drawing half of the pure-Kotlin (wasm-ready) backend.
 *
 * It mirrors the JVM/PdfBox backend's geometry exactly so a document renders
 * identically on either: PdfKmp uses a top-left origin with Y growing downward,
 * the PDF content stream a bottom-left origin, so every Y coordinate is flipped
 * through [fy] *per operator* rather than via a single page-level CTM flip —
 * a global flip would mirror text. Text is placed by its top-left corner, so the
 * baseline is dropped by the font ascent before the `Td` offset.
 *
 * Transparency, gradients, clipping, rotation, links, destinations, bookmarks,
 * and image embedding are all expressed directly as operators / page objects.
 * Form fields are no-ops here (the renderer already draws a static visual), with
 * a one-time warning. One canvas serves one page; the driver owns the document.
 */
internal class KmpPdfCanvas(
    private val page: KmpPage,
    private val pageIndex: Int,
    private val navigation: KmpNavigation,
    private val textEncoder: WinAnsiTextEncoder,
    private val fontRegistry: KmpFontRegistry,
) : PdfCanvas {

    private val out: ByteBuffer get() = page.content
    private val resources: KmpResources get() = page.resources
    private val pageHeight: Float get() = page.height

    /** Multiplier folded into every draw's alpha; see [beginTransparencyGroup]. */
    private var groupAlpha = 1f
    private val groupAlphaStack = ArrayDeque<Float>()

    private var warnedForm = false

    /** Flips a top-left-origin Y into the PDF's bottom-left space. */
    private fun fy(y: Float): Float = pageHeight - y

    private fun n(value: Float): String = PdfSyntax.formatNumber(value)

    private fun op(line: String) {
        out.append(line)
        out.append("\n")
    }

    // -- Text -------------------------------------------------------------

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        if (text.isEmpty()) return
        textEncoder.noteFont(style.font)
        val plan = fontRegistry.planRun(text, style)

        val size = style.fontSize.value
        // Each text path picks its own font resource, glyph string, and ascent.
        val fontRef: KmpFontRef
        val glyphString: String
        val ascent: Float
        when (plan) {
            is KmpFontRegistry.RunPlan.Helvetica -> {
                val codes = textEncoder.encodeToWinAnsi(text)
                if (codes.isEmpty()) return
                fontRef = KmpFontRef.Helvetica(plan.face)
                glyphString = encodeStringLiteral(codes)
                ascent = ascentPoints(size)
            }
            is KmpFontRegistry.RunPlan.Embedded -> {
                val embedded = plan.embedded
                fontRef = KmpFontRef.Embedded(embedded)
                glyphString = encodeGlyphHex(embedded, text)
                // Embedded faces carry their own ascent; the layout engine
                // measured against the same value (see KmpFontMetrics).
                ascent = embedded.ascentThousandths / 1000f * size
            }
        }

        val fontName = resources.fontName(fontRef)
        // PdfKmp positions text by its top-left corner; PDF text operators place
        // the baseline. Offset down by the ascent to convert.
        val baselineTop = y + ascent

        applyAlpha(style.color.alpha)
        setFillColor(style.color)
        op("BT")
        op("/$fontName ${n(size)} Tf")
        if (style.letterSpacing.value != 0f) op("${n(style.letterSpacing.value)} Tc")
        op("${n(x)} ${n(fy(baselineTop))} Td")
        op("$glyphString Tj")
        op("ET")
        // Reset character spacing so it doesn't leak into later text runs that
        // assume the default zero.
        if (style.letterSpacing.value != 0f) op("0 Tc")
    }

    /**
     * Encodes WinAnsi byte [codes] as a PDF literal string `( … )`, escaping the
     * structural bytes (`\`, `(`, `)`) and emitting everything else as a raw
     * single byte. Single-byte WinAnsi codes map straight to content-stream
     * bytes because the font declares `/Encoding /WinAnsiEncoding`.
     */
    private fun encodeStringLiteral(codes: IntArray): String = buildString {
        append('(')
        for (code in codes) {
            when (code) {
                '\\'.code -> append("\\\\")
                '('.code -> append("\\(")
                ')'.code -> append("\\)")
                else -> append(code.toChar())
            }
        }
        append(')')
    }

    /**
     * Encodes [text] as a hex glyph-id string `<…>` for an embedded Identity-H
     * font: each code point is mapped to its two-byte glyph id (CID == GID under
     * the Identity CIDToGIDMap) and emitted as four hex digits. Recording usage
     * here keeps the subset and width array complete; the same code points were
     * already noted at measure time, and re-noting is idempotent.
     */
    private fun encodeGlyphHex(embedded: KmpEmbeddedFont, text: String): String = buildString {
        append('<')
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            i += if (cp > 0xFFFF) 2 else 1
            val gid = embedded.use(cp)
            append(hex4(gid))
        }
        append('>')
    }

    private fun hex4(v: Int): String {
        val s = v.toString(16).uppercase()
        return "0".repeat((4 - s.length).coerceAtLeast(0)) + s
    }

    /**
     * Decodes the Unicode code point at [index], combining a surrogate pair into
     * one astral code point — stdlib char arithmetic to stay wasm-compatible.
     */
    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            }
        }
        return high.code
    }

    // -- Rectangles -------------------------------------------------------

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: PdfColor) {
        applyAlpha(color.alpha)
        setFillColor(color)
        op("${n(x)} ${n(fy(y + height))} ${n(width)} ${n(height)} re")
        op("f")
    }

    override fun drawRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
    ) {
        applyAlpha(color.alpha)
        setFillColor(color)
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        op("f")
    }

    override fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
        thickness: Float,
    ) {
        applyAlpha(color.alpha)
        setStrokeColor(color)
        op("${n(thickness)} w")
        op("${n(x)} ${n(fy(y + height))} ${n(width)} ${n(height)} re")
        op("S")
    }

    override fun strokeRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
        thickness: Float,
    ) {
        applyAlpha(color.alpha)
        setStrokeColor(color)
        op("${n(thickness)} w")
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        op("S")
    }

    override fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: PdfColor,
        thickness: Float,
        style: LineStyle,
    ) {
        applyAlpha(color.alpha)
        setStrokeColor(color)
        op("${n(thickness)} w")
        applyDash(style, thickness)
        op("${n(x1)} ${n(fy(y1))} m")
        op("${n(x2)} ${n(fy(y2))} l")
        op("S")
        // Reset to a solid butt-capped default for subsequent strokes.
        op("[] 0 d")
        op("0 J")
    }

    private fun applyDash(style: LineStyle, thickness: Float) {
        when (style) {
            LineStyle.Solid -> {
                op("[] 0 d")
                op("0 J")
            }
            LineStyle.Dashed -> {
                op("[${n(thickness * 4f)} ${n(thickness * 2f)}] 0 d")
                op("0 J")
            }
            LineStyle.Dotted -> {
                // Zero-length on-segments with a round cap render as round dots
                // whose diameter equals the stroke width.
                op("[0 ${n(thickness * 2f)}] 0 d")
                op("1 J")
            }
        }
    }

    // -- State + clipping -------------------------------------------------

    override fun saveState() {
        op("q")
    }

    override fun restoreState() {
        op("Q")
    }

    override fun clipRect(x: Float, y: Float, width: Float, height: Float) {
        op("${n(x)} ${n(fy(y + height))} ${n(width)} ${n(height)} re")
        op("W n")
    }

    override fun clipRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        op("W n")
    }

    override fun clipPath(commands: List<PathCommand>) {
        if (commands.isEmpty()) return
        appendPath(commands)
        op("W n")
    }

    // -- Paths ------------------------------------------------------------

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) {
        if (commands.isEmpty()) return
        // Smart-cast the stroke colour to non-null here so the stroke branch
        // below can read it without a redundant null check.
        val stroke = if (strokeWidth > 0f) strokeColor else null
        if (fill == null && stroke == null) return

        when (fill) {
            is PdfPaint.Solid -> {
                applyAlpha(fill.color.alpha)
                setFillColor(fill.color)
                appendPath(commands)
                op("f")
            }
            is PdfPaint.LinearGradient -> drawGradientFill(commands, uniformAlpha(fill.stops)) {
                KmpShadingDef(
                    axial = true,
                    coords = floatArrayOf(fill.startX, fy(fill.startY), fill.endX, fy(fill.endY)),
                    colors = sortedColors(fill.stops),
                    offsets = sortedOffsets(fill.stops),
                )
            }
            is PdfPaint.RadialGradient -> drawGradientFill(commands, uniformAlpha(fill.stops)) {
                KmpShadingDef(
                    axial = false,
                    coords = floatArrayOf(fill.centerX, fy(fill.centerY), fill.radius),
                    colors = sortedColors(fill.stops),
                    offsets = sortedOffsets(fill.stops),
                )
            }
            null -> Unit
        }

        if (stroke != null) {
            applyAlpha(stroke.alpha)
            setStrokeColor(stroke)
            op("${n(strokeWidth)} w")
            appendPath(commands)
            op("S")
        }
    }

    /**
     * Fills the area inside [commands] with a gradient. PDF has no "fill path
     * with gradient" operator, so the documented technique is used: clip to the
     * path then paint the shading over the clipped region, scoped by `q`/`Q` so
     * the clip doesn't leak. Mirrors the JVM backend.
     */
    private inline fun drawGradientFill(
        commands: List<PathCommand>,
        alpha: Float,
        shading: () -> KmpShadingDef,
    ) {
        op("q")
        applyAlpha(alpha)
        appendPath(commands)
        op("W n")
        val name = resources.shadingName(shading())
        op("/$name sh")
        op("Q")
    }

    /**
     * The uniform alpha across all [stops], or `1f` if they differ. PDF shadings
     * carry no alpha channel; a constant non-stroking alpha reproduces a
     * uniformly translucent gradient (the common case). Per-stop-varying alpha
     * would need a soft mask and stays opaque — matching the JVM backend.
     */
    private fun uniformAlpha(stops: List<GradientStop>): Float {
        if (stops.isEmpty()) return 1f
        val a = stops.first().color.alpha
        return if (stops.all { it.color.alpha == a }) a.coerceIn(0f, 1f) else 1f
    }

    private fun sortedColors(stops: List<GradientStop>): List<FloatArray> =
        stops.sortedBy { it.offset }.map { rgb(it.color) }

    private fun sortedOffsets(stops: List<GradientStop>): FloatArray =
        stops.sortedBy { it.offset }.map { it.offset.coerceIn(0f, 1f) }.toFloatArray()

    /**
     * Emits [commands] into the content stream, flipping every Y. PDF has no
     * quadratic Bézier operator, so [PathCommand.QuadTo] is raised to an
     * equivalent cubic using the current point (tracked in top-left space; the
     * flip is affine so converting first and flipping each control point is
     * exact).
     */
    private fun appendPath(commands: List<PathCommand>) {
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    op("${n(cmd.x)} ${n(fy(cmd.y))} m")
                    curX = cmd.x; curY = cmd.y; startX = cmd.x; startY = cmd.y
                }
                is PathCommand.LineTo -> {
                    op("${n(cmd.x)} ${n(fy(cmd.y))} l")
                    curX = cmd.x; curY = cmd.y
                }
                is PathCommand.CubicTo -> {
                    op(
                        "${n(cmd.c1x)} ${n(fy(cmd.c1y))} ${n(cmd.c2x)} ${n(fy(cmd.c2y))} " +
                            "${n(cmd.x)} ${n(fy(cmd.y))} c",
                    )
                    curX = cmd.x; curY = cmd.y
                }
                is PathCommand.QuadTo -> {
                    val c1x = curX + 2f / 3f * (cmd.cx - curX)
                    val c1y = curY + 2f / 3f * (cmd.cy - curY)
                    val c2x = cmd.x + 2f / 3f * (cmd.cx - cmd.x)
                    val c2y = cmd.y + 2f / 3f * (cmd.cy - cmd.y)
                    op("${n(c1x)} ${n(fy(c1y))} ${n(c2x)} ${n(fy(c2y))} ${n(cmd.x)} ${n(fy(cmd.y))} c")
                    curX = cmd.x; curY = cmd.y
                }
                PathCommand.Close -> {
                    op("h")
                    curX = startX; curY = startY
                }
            }
        }
    }

    private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float): List<PathCommand> =
        buildRoundedRectPath(x, y, width, height, radius, radius, radius, radius)

    // -- Rotation + transparency ------------------------------------------

    override fun rotate(degrees: Float, pivotX: Float, pivotY: Float) {
        // PdfKmp degrees are clockwise in top-left space; the PDF CTM is
        // bottom-left, so the angle sign flips and the pivot's Y flips. The CTM
        // is translate(pivot) · rotate(-θ) · translate(-pivot), composed into a
        // single cm matrix [a b c d e f].
        val py = fy(pivotY)
        val radians = -degrees.toDouble() * PI / 180.0
        val cosT = cos(radians).toFloat()
        val sinT = sin(radians).toFloat()
        // translate(px,py) · rotate · translate(-px,-py):
        //   a=cos b=sin c=-sin d=cos
        //   e = px - px*cos + py*sin
        //   f = py - px*sin - py*cos
        val e = pivotX - pivotX * cosT + py * sinT
        val f = py - pivotX * sinT - py * cosT
        op("${n(cosT)} ${n(sinT)} ${n(-sinT)} ${n(cosT)} ${n(e)} ${n(f)} cm")
    }

    override fun beginTransparencyGroup(alpha: Float) {
        // No nestable group-alpha primitive in a flat content stream, so the
        // group alpha is folded into every subsequent draw's per-draw alpha
        // until the matching end call — matching the JVM backend.
        groupAlphaStack.addLast(groupAlpha)
        groupAlpha *= alpha.coerceIn(0f, 1f)
    }

    override fun endTransparencyGroup() {
        groupAlpha = groupAlphaStack.removeLastOrNull() ?: 1f
    }

    // -- Navigation -------------------------------------------------------

    override fun linkAnnotation(x: Float, y: Float, width: Float, height: Float, url: String) {
        if (width <= 0f || height <= 0f) return
        page.annotations.add(
            KmpAnnotation(
                llx = x, lly = fy(y + height), urx = x + width, ury = fy(y),
                url = url,
            ),
        )
    }

    override fun namedDestination(name: String, y: Float) {
        navigation.destinations[name] = KmpDestination(pageIndex, fy(y))
    }

    override fun linkToDestination(name: String, x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        page.annotations.add(
            KmpAnnotation(
                llx = x, lly = fy(y + height), urx = x + width, ury = fy(y),
                destinationName = name,
            ),
        )
    }

    override fun bookmark(title: String, level: Int, y: Float) {
        navigation.bookmarks.add(KmpBookmark(title, level, pageIndex, fy(y)))
    }

    // -- Images -----------------------------------------------------------

    override fun drawImage(
        bytes: ByteArray,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        contentScale: ContentScale,
        sourceTop: Float,
        sourceBottom: Float,
        allowDownScale: Boolean,
        altText: String?,
    ) {
        if (bytes.isEmpty() || width <= 0f || height <= 0f) return
        val embeddable = KmpImageEmbedder.embed(bytes) ?: return
        val name = resources.imageName(embeddable.def)

        // The PDF image-XObject unit square is mapped by the CTM, so a draw is
        // "clip to dest rect, translate+scale the unit square onto it, Do". The
        // source-window (sourceTop/sourceBottom) and ContentScale just shift and
        // resize that mapping — no pixel decode needed. All in PDF (flipped)
        // space, with the dest rect's bottom-left at (x, fy(y+height)).
        val placement = computePlacement(
            scale = contentScale,
            srcWidth = embeddable.widthPx.toFloat(),
            srcHeight = embeddable.heightPx.toFloat(),
            dstX = x,
            dstY = y,
            dstWidth = width,
            dstHeight = height,
            sourceTop = sourceTop,
            sourceBottom = sourceBottom,
        )

        op("q")
        // Clip to the requested destination rectangle so Crop / source-window
        // overflow is trimmed.
        op("${n(x)} ${n(fy(y + height))} ${n(width)} ${n(height)} re")
        op("W n")
        // cm maps the 1×1 image space onto the placed rectangle (bottom-left
        // origin): scale by (w,h), translate to (left, bottom).
        op("${n(placement.width)} 0 0 ${n(placement.height)} ${n(placement.left)} ${n(placement.bottom)} cm")
        op("/$name Do")
        op("Q")
    }

    /** Resolved image placement in PDF (flipped) space: where the full image lands. */
    private class Placement(val left: Float, val bottom: Float, val width: Float, val height: Float)

    /**
     * Computes where the *whole* image should be placed (in PDF bottom-left
     * space) so that, after clipping to the destination rectangle, the requested
     * source window lands exactly on it under the chosen [scale].
     *
     * The source window (`sourceTop`/`sourceBottom`, normalised top-down) selects
     * a horizontal band of the image; the band is fitted to the destination and
     * the full image is then back-extrapolated so the band aligns. ContentScale
     * follows the JVM backend: Fit letterboxes inside the dest, FillBounds
     * stretches to the dest, Crop covers the dest and lets the clip trim the
     * overflow.
     */
    private fun computePlacement(
        scale: ContentScale,
        srcWidth: Float,
        srcHeight: Float,
        dstX: Float,
        dstY: Float,
        dstWidth: Float,
        dstHeight: Float,
        sourceTop: Float,
        sourceBottom: Float,
    ): Placement {
        // Destination rectangle in PDF space.
        val dstLeft = dstX
        val dstBottom = fy(dstY + dstHeight)

        // Fraction of the image height the source window keeps.
        val top = sourceTop.coerceIn(0f, 1f)
        val bottom = sourceBottom.coerceIn(0f, 1f).coerceAtLeast(top + 0.0001f)
        val windowFraction = (bottom - top).coerceIn(0.0001f, 1f)

        if (srcWidth <= 0f || srcHeight <= 0f) {
            return Placement(dstLeft, dstBottom, dstWidth, dstHeight)
        }

        // The visible band's intrinsic aspect (width / visible-height).
        val bandHeightPx = srcHeight * windowFraction
        val bandAspect = srcWidth / bandHeightPx
        val dstAspect = if (dstHeight == 0f) bandAspect else dstWidth / dstHeight

        // Size the band into the destination per the scale mode.
        val bandW: Float
        val bandH: Float
        var bandLeft = dstLeft
        var bandBottom = dstBottom
        when (scale) {
            ContentScale.FillBounds -> {
                bandW = dstWidth
                bandH = dstHeight
            }
            ContentScale.Crop -> {
                // Cover: scale so the band fills the dest, overflow clipped.
                if (bandAspect > dstAspect) {
                    bandH = dstHeight
                    bandW = dstHeight * bandAspect
                    bandLeft = dstLeft - (bandW - dstWidth) / 2f
                } else {
                    bandW = dstWidth
                    bandH = dstWidth / bandAspect
                    bandBottom = dstBottom - (bandH - dstHeight) / 2f
                }
            }
            ContentScale.Fit -> {
                // Contain: scale so the band fits inside, centered (letterbox).
                if (bandAspect > dstAspect) {
                    bandW = dstWidth
                    bandH = dstWidth / bandAspect
                    bandBottom = dstBottom + (dstHeight - bandH) / 2f
                } else {
                    bandH = dstHeight
                    bandW = dstHeight * bandAspect
                    bandLeft = dstLeft + (dstWidth - bandW) / 2f
                }
            }
        }

        // Back-extrapolate from the visible band to the full image: the full
        // image is taller than the band by 1/windowFraction, and the band sits
        // `top` of the way down from the image's top.
        val fullHeight = bandH / windowFraction
        // In PDF (bottom-up) space, the image top is above the band top by
        // (top * fullHeight); the band top is at bandBottom + bandH.
        val imageTopY = (bandBottom + bandH) + top * fullHeight
        val fullBottom = imageTopY - fullHeight
        return Placement(left = bandLeft, bottom = fullBottom, width = bandW, height = fullHeight)
    }

    // -- Forms (unsupported) ----------------------------------------------

    override fun formTextField(
        name: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        value: String,
        multiline: Boolean,
        fontSizePt: Float,
    ) {
        warnFormUnsupported()
    }

    override fun formCheckBox(name: String, x: Float, y: Float, size: Float, checked: Boolean) {
        warnFormUnsupported()
    }

    private fun warnFormUnsupported() {
        if (warnedForm) return
        warnedForm = true
        PdfLog.warn(
            "Interactive AcroForm fields are not produced by the pure-Kotlin PDF backend yet; " +
                "the static visual fallback drawn by the renderer is kept.",
        )
    }

    // -- Colour + alpha helpers -------------------------------------------

    private fun setFillColor(color: PdfColor) {
        op("${n(color.red.coerceIn(0f, 1f))} ${n(color.green.coerceIn(0f, 1f))} ${n(color.blue.coerceIn(0f, 1f))} rg")
    }

    private fun setStrokeColor(color: PdfColor) {
        op("${n(color.red.coerceIn(0f, 1f))} ${n(color.green.coerceIn(0f, 1f))} ${n(color.blue.coerceIn(0f, 1f))} RG")
    }

    /** Sets the current stroking + non-stroking alpha via a cached ExtGState. */
    private fun applyAlpha(alpha: Float) {
        val a = (alpha * groupAlpha).coerceIn(0f, 1f)
        val name = resources.alphaState(a)
        op("/$name gs")
    }

    private fun rgb(color: PdfColor): FloatArray = floatArrayOf(
        color.red.coerceIn(0f, 1f),
        color.green.coerceIn(0f, 1f),
        color.blue.coerceIn(0f, 1f),
    )
}
