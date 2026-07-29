package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.image.MAX_DECODE_PIXELS
import com.conamobile.pdfkmp.image.exceedsDecodeBudget
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max

/**
 * [PdfCanvas] backed by a PdfBox [PDPageContentStream].
 *
 * Text is emitted through PdfBox's `Tj` text operators against the embedded
 * subset-enabled TrueType fonts, and shapes through path operators, so the
 * resulting PDF is fully vector and stays sharp at any zoom — matching the
 * Android and iOS backends.
 *
 * PdfKmp coordinates use a top-left origin with Y growing downward, while a
 * PDF content stream uses the native bottom-left origin. Every Y coordinate
 * is therefore flipped through [fy] as it is written; no global CTM flip is
 * applied, so text never renders mirrored.
 *
 * One canvas is created per page and is valid only between the matching
 * [PdfDriver.beginPage] / [PdfDriver.endPage] calls.
 */
internal class JvmPdfCanvas(
    private val document: PDDocument,
    private val page: PDPage,
    private val cs: PDPageContentStream,
    private val pageHeight: Float,
    private val fonts: JvmFontRegistry,
    private val navigation: JvmNavigation = JvmNavigation(),
    private val forms: JvmAcroForm = JvmAcroForm(document),
) : PdfCanvas {

    /** Cache of alpha-only graphics states, keyed by the rounded alpha value. */
    private val alphaStates = HashMap<Float, PDExtendedGraphicsState>()

    /** Flips a top-left-origin Y coordinate into the PDF's bottom-left space. */
    private fun fy(y: Float): Float = pageHeight - y

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        val font = fonts.fontFor(style)
        // PdfBox's showText does no bidi/Arabic shaping; reorder + shape on JVM
        // so RTL scripts render the way Android/iOS get for free. No-op for Latin.
        val shown = fonts.encodable(font, JvmBidiShaper.process(text))
        if (shown.isEmpty()) return
        val size = style.fontSize.value
        // PdfKmp positions text by its top-left corner; PDF text operators
        // place the baseline. Offset down by the ascent to convert.
        val baselineTop = y + font.ascentPoints(size)
        applyAlpha(style.color.alpha)
        cs.beginText()
        cs.setFont(font, size)
        cs.setCharacterSpacing(style.letterSpacing.value)
        cs.setNonStrokingColor(awt(style.color))
        cs.newLineAtOffset(x, fy(baselineTop))
        cs.showText(shown)
        cs.endText()
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: PdfColor) {
        applyAlpha(color.alpha)
        cs.setNonStrokingColor(awt(color))
        cs.addRect(x, fy(y + height), width, height)
        cs.fill()
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
        cs.setNonStrokingColor(awt(color))
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        cs.fill()
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
        cs.setStrokingColor(awt(color))
        cs.setLineWidth(thickness)
        cs.addRect(x, fy(y + height), width, height)
        cs.stroke()
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
        cs.setStrokingColor(awt(color))
        cs.setLineWidth(thickness)
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        cs.stroke()
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
        cs.setStrokingColor(awt(color))
        cs.setLineWidth(thickness)
        applyDash(style, thickness)
        cs.moveTo(x1, fy(y1))
        cs.lineTo(x2, fy(y2))
        cs.stroke()
        // Reset so subsequent strokes default to solid butt-capped lines.
        cs.setLineDashPattern(FloatArray(0), 0f)
        cs.setLineCapStyle(0)
    }

    private fun applyDash(style: LineStyle, thickness: Float) {
        when (style) {
            LineStyle.Solid -> {
                cs.setLineDashPattern(FloatArray(0), 0f)
                cs.setLineCapStyle(0)
            }
            LineStyle.Dashed -> {
                cs.setLineDashPattern(floatArrayOf(thickness * 4f, thickness * 2f), 0f)
                cs.setLineCapStyle(0)
            }
            LineStyle.Dotted -> {
                // Zero-length on-segments with a round cap render as circular
                // dots whose diameter equals the stroke width.
                cs.setLineDashPattern(floatArrayOf(0f, thickness * 2f), 0f)
                cs.setLineCapStyle(1)
            }
        }
    }

    override fun saveState() {
        cs.saveGraphicsState()
    }

    override fun restoreState() {
        cs.restoreGraphicsState()
    }

    override fun clipRect(x: Float, y: Float, width: Float, height: Float) {
        cs.addRect(x, fy(y + height), width, height)
        cs.clip()
    }

    override fun clipRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        appendPath(roundedRect(x, y, width, height, cornerRadius))
        cs.clip()
    }

    override fun clipPath(commands: List<PathCommand>) {
        if (commands.isEmpty()) return
        appendPath(commands)
        cs.clip()
    }

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) {
        if (commands.isEmpty()) return
        val hasStroke = strokeColor != null && strokeWidth > 0f
        if (fill == null && !hasStroke) return

        when (fill) {
            is PdfPaint.Solid -> {
                applyAlpha(fill.color.alpha)
                cs.setNonStrokingColor(awt(fill.color))
                appendPath(commands)
                cs.fill()
            }
            is PdfPaint.LinearGradient -> drawGradientFill(commands, uniformAlpha(fill.stops)) {
                buildAxialShading(fill.startX, fy(fill.startY), fill.endX, fy(fill.endY), fill.stops)
            }
            is PdfPaint.RadialGradient -> drawGradientFill(commands, uniformAlpha(fill.stops)) {
                buildRadialShading(fill.centerX, fy(fill.centerY), fill.radius, fill.stops)
            }
            null -> Unit
        }

        if (hasStroke && strokeColor != null) {
            applyAlpha(strokeColor.alpha)
            cs.setStrokingColor(awt(strokeColor))
            cs.setLineWidth(strokeWidth)
            appendPath(commands)
            cs.stroke()
        }
    }

    /**
     * Fills the area inside [commands] with the shading produced by
     * [shading]. PDF has no "fill path with gradient" operator; the
     * documented technique is to clip to the path and paint the shading
     * over the clipped region, scoped by save/restore so the clip doesn't
     * leak into later draws.
     */
    private inline fun drawGradientFill(
        commands: List<PathCommand>,
        alpha: Float,
        shading: () -> org.apache.pdfbox.pdmodel.graphics.shading.PDShading,
    ) {
        cs.saveGraphicsState()
        applyAlpha(alpha)
        appendPath(commands)
        cs.clip()
        cs.shadingFill(shading())
        cs.restoreGraphicsState()
    }

    /**
     * Uniform alpha across all [stops], or `1f` if they differ. PDF axial /
     * radial shadings carry no alpha channel; a constant non-stroking alpha
     * reproduces a uniformly-translucent gradient (the common case, e.g.
     * `Color.withAlpha(0.5f)` start & end). Genuinely per-stop-varying alpha
     * would need a luminosity soft mask and is left opaque.
     */
    private fun uniformAlpha(stops: List<com.conamobile.pdfkmp.style.GradientStop>): Float {
        if (stops.isEmpty()) return 1f
        val a = stops.first().color.alpha
        return if (stops.all { it.color.alpha == a }) a.coerceIn(0f, 1f) else 1f
    }

    override fun linkAnnotation(x: Float, y: Float, width: Float, height: Float, url: String) {
        if (width <= 0f || height <= 0f) return
        val link = PDAnnotationLink()
        link.rectangle = PDRectangle(x, fy(y + height), width, height)
        link.action = PDActionURI().apply { uri = url }
        // Invisible border — the visual "this is a link" styling comes from
        // the surrounding content, matching the other backends.
        link.borderStyle = PDBorderStyleDictionary().apply { this.width = 0f }
        page.annotations.add(link)
    }

    override fun namedDestination(name: String, y: Float) {
        navigation.destinations[name] = destinationAt(y)
    }

    override fun bookmark(title: String, level: Int, y: Float) {
        navigation.bookmarks += JvmNavigation.Bookmark(title, level, destinationAt(y))
    }

    override fun linkToDestination(name: String, x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val link = PDAnnotationLink()
        link.rectangle = PDRectangle(x, fy(y + height), width, height)
        link.borderStyle = PDBorderStyleDictionary().apply { this.width = 0f }
        page.annotations.add(link)
        // The GoTo action attaches at finish() — the destination may be
        // registered by a later page (TOC entries link forward).
        navigation.pendingLinks += name to link
    }

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
        if (width <= 0f || height <= 0f) return
        val acroForm = forms.acroForm()
        val field = PDTextField(acroForm).apply {
            partialName = forms.uniqueName(name)
            // Inherit the form-level /DA but pin the requested font size so the
            // interactive field matches the static fallback's visual size.
            defaultAppearance = "/Helv $fontSizePt Tf 0 g"
            isMultiline = multiline
        }
        val widget = field.widgets.first().apply {
            rectangle = PDRectangle(x, fy(y + height), width, height)
            page = this@JvmPdfCanvas.page
            // The static fallback already draws the box + border; keep the
            // widget's own border invisible so they don't double up.
            setBorderStyle(PDBorderStyleDictionary().apply { this.width = 0f })
        }
        // Set the value after the widget exists so NeedAppearances has a widget
        // to regenerate against.
        if (value.isNotEmpty()) field.value = value
        page.annotations.add(widget)
        forms.addField(field)
    }

    override fun formCheckBox(name: String, x: Float, y: Float, size: Float, checked: Boolean) {
        if (size <= 0f) return
        val acroForm = forms.acroForm()
        val field = PDCheckBox(acroForm).apply {
            partialName = forms.uniqueName(name)
        }
        val widget = field.widgets.first().apply {
            rectangle = PDRectangle(x, fy(y + size), size, size)
            page = this@JvmPdfCanvas.page
            setBorderStyle(PDBorderStyleDictionary().apply { this.width = 0f })
        }
        page.annotations.add(widget)
        forms.addField(field)
        // Toggle state after the widget is wired up.
        if (checked) field.check() else field.unCheck()
    }

    /**
     * Jump target at the given top-left-origin [y] on this page. Zoom 0
     * keeps the reader's current zoom level, matching what users expect
     * from in-document navigation.
     */
    private fun destinationAt(y: Float): PDPageXYZDestination = PDPageXYZDestination().apply {
        setPage(this@JvmPdfCanvas.page)
        top = fy(y).toInt()
        left = 0
        zoom = 0f
    }

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
        // The common header check only parses PNG/JPEG; the ImageIO probe
        // covers every other format ImageIO would otherwise fully decode
        // (GIF/BMP/TIFF dimension bombs included).
        if (exceedsDecodeBudget(bytes) || imageIoExceedsDecodeBudget(bytes)) {
            PdfLog.warn(
                "drawImage skipped: declared dimensions exceed the $MAX_DECODE_PIXELS-pixel " +
                    "decode budget (JVM backend)",
            )
            return
        }
        val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
        if (decoded == null) {
            PdfLog.warn("drawImage skipped: ${bytes.size}-byte payload is not a decodable image (JVM backend)")
            return
        }

        val sliced = sliceRows(decoded, sourceTop, sourceBottom)
        val shaped = if (contentScale == ContentScale.Crop) {
            cropToFill(sliced, width, height)
        } else {
            sliced
        }
        val scaled = if (allowDownScale) downscaleIfLarger(shaped, width, height) else shaped

        val dst = applyContentScale(
            scale = contentScale,
            srcWidth = shaped.width.toFloat(),
            srcHeight = shaped.height.toFloat(),
            dstX = x,
            dstY = y,
            dstWidth = width,
            dstHeight = height,
        )
        val xObject = LosslessFactory.createFromImage(document, scaled)
        // When alt text is supplied, wrap the image draw in a /Figure marked-
        // content sequence carrying /Alt so tagged-PDF consumers (screen
        // readers) can describe the picture. Best-effort: this records the
        // alternate text on the content; a full tag tree is out of scope.
        if (altText != null) {
            val props = PDPropertyList.create(
                COSDictionary().apply { setItem(COSName.ALT, COSString(altText)) },
            )
            cs.beginMarkedContent(COSName.getPDFName("Figure"), props)
            cs.drawImage(xObject, dst.x, fy(dst.y + dst.height), dst.width, dst.height)
            cs.endMarkedContent()
        } else {
            cs.drawImage(xObject, dst.x, fy(dst.y + dst.height), dst.width, dst.height)
        }
    }

    /**
     * Emits [commands] into the content stream, flipping every Y coordinate.
     *
     * PDF has no quadratic Bézier operator, so [PathCommand.QuadTo] is raised
     * to an equivalent cubic. The current point is tracked in PdfKmp's
     * top-left space (the flip is affine, so converting first and flipping
     * each control point afterwards is exact).
     */
    private fun appendPath(commands: List<PathCommand>) {
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    cs.moveTo(cmd.x, fy(cmd.y))
                    curX = cmd.x; curY = cmd.y; startX = cmd.x; startY = cmd.y
                }
                is PathCommand.LineTo -> {
                    cs.lineTo(cmd.x, fy(cmd.y))
                    curX = cmd.x; curY = cmd.y
                }
                is PathCommand.CubicTo -> {
                    cs.curveTo(cmd.c1x, fy(cmd.c1y), cmd.c2x, fy(cmd.c2y), cmd.x, fy(cmd.y))
                    curX = cmd.x; curY = cmd.y
                }
                is PathCommand.QuadTo -> {
                    // Quadratic → cubic: lift the single control point to two.
                    val c1x = curX + 2f / 3f * (cmd.cx - curX)
                    val c1y = curY + 2f / 3f * (cmd.cy - curY)
                    val c2x = cmd.x + 2f / 3f * (cmd.cx - cmd.x)
                    val c2y = cmd.y + 2f / 3f * (cmd.cy - cmd.y)
                    cs.curveTo(c1x, fy(c1y), c2x, fy(c2y), cmd.x, fy(cmd.y))
                    curX = cmd.x; curY = cmd.y
                }
                PathCommand.Close -> {
                    cs.closePath()
                    curX = startX; curY = startY
                }
            }
        }
    }

    private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float): List<PathCommand> =
        buildRoundedRectPath(x, y, width, height, radius, radius, radius, radius)

    /** Sets the current alpha (stroking + non-stroking) via a cached ExtGState. */
    override fun rotate(degrees: Float, pivotX: Float, pivotY: Float) {
        // PdfKmp degrees are clockwise in top-left space; the PDF CTM is
        // bottom-left, so the angle sign flips and the pivot's Y flips.
        val radians = Math.toRadians(-degrees.toDouble())
        val py = fy(pivotY)
        cs.transform(Matrix.getTranslateInstance(pivotX, py))
        cs.transform(Matrix.getRotateInstance(radians, 0f, 0f))
        cs.transform(Matrix.getTranslateInstance(-pivotX, -py))
    }

    override fun beginTransparencyGroup(alpha: Float) {
        // PdfBox content streams have no nestable group-alpha primitive,
        // so the group alpha is folded into every subsequent draw call's
        // per-draw alpha until the matching end call. Overlapping children
        // therefore blend with each other — documented on the DSL.
        groupAlphaStack.addLast(groupAlpha)
        groupAlpha *= alpha.coerceIn(0f, 1f)
    }

    override fun endTransparencyGroup() {
        groupAlpha = groupAlphaStack.removeLastOrNull() ?: 1f
    }

    /** Multiplier applied to every draw's alpha; see [beginTransparencyGroup]. */
    private var groupAlpha = 1f
    private val groupAlphaStack = ArrayDeque<Float>()

    private fun applyAlpha(alpha: Float) {
        val a = (alpha * groupAlpha).coerceIn(0f, 1f)
        val state = alphaStates.getOrPut(a) {
            PDExtendedGraphicsState().apply {
                strokingAlphaConstant = a
                nonStrokingAlphaConstant = a
            }
        }
        cs.setGraphicsStateParameters(state)
    }

    private fun awt(color: PdfColor): Color =
        Color(
            color.red.coerceIn(0f, 1f),
            color.green.coerceIn(0f, 1f),
            color.blue.coerceIn(0f, 1f),
        )
}

/** Rectangle in PDF points used to communicate an image's draw destination. */
private data class DstRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Pre-decode dimension check for every format ImageIO can read. The header
 * is sized through the format's `ImageReader` without touching pixel data,
 * so a hostile file claiming enormous dimensions is rejected before
 * `ImageIO.read` can allocate them. Unreadable input returns `false` — the
 * subsequent full decode is the one that reports it as undecodable.
 */
private fun imageIoExceedsDecodeBudget(bytes: ByteArray): Boolean {
    return try {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return false
        input.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return false
            val reader = readers.next()
            try {
                reader.setInput(stream)
                val w = reader.getWidth(0)
                val h = reader.getHeight(0)
                w > 0 && h > 0 && w.toLong() * h.toLong() > MAX_DECODE_PIXELS
            } finally {
                reader.dispose()
            }
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Returns the horizontal slice of [image] between the normalised
 * [sourceTop]/[sourceBottom] fractions of its height (used by repeating
 * watermark / banded image embeds). Returns [image] unchanged for the full
 * `0f..1f` range.
 */
private fun sliceRows(image: BufferedImage, sourceTop: Float, sourceBottom: Float): BufferedImage {
    val top = (image.height * sourceTop.coerceIn(0f, 1f)).toInt()
    val bottom = (image.height * sourceBottom.coerceIn(0f, 1f)).toInt().coerceAtLeast(top + 1)
    if (top <= 0 && bottom >= image.height) return image
    val safeBottom = bottom.coerceAtMost(image.height)
    return image.getSubimage(0, top, image.width, (safeBottom - top).coerceAtLeast(1))
}

/**
 * Centre-crops [image] so that, stretched into the destination, it fills the
 * destination rectangle while preserving its own aspect ratio.
 */
private fun cropToFill(image: BufferedImage, dstWidth: Float, dstHeight: Float): BufferedImage {
    if (image.width <= 0 || image.height <= 0 || dstHeight <= 0f) return image
    val srcAspect = image.width.toFloat() / image.height.toFloat()
    val dstAspect = dstWidth / dstHeight
    return if (srcAspect > dstAspect) {
        val targetWidth = (image.height * dstAspect).toInt().coerceIn(1, image.width)
        val padding = (image.width - targetWidth) / 2
        image.getSubimage(padding, 0, targetWidth, image.height)
    } else {
        val targetHeight = (image.width / dstAspect).toInt().coerceIn(1, image.height)
        val padding = (image.height - targetHeight) / 2
        image.getSubimage(0, padding, image.width, targetHeight)
    }
}

/**
 * If [image]'s pixel dimensions exceed what a draw at
 * `dstWidth × dstHeight` PDF points needs at [DEFAULT_TARGET_DPI],
 * redraws it into a proportionally smaller buffer. Keeps the embedded PDF
 * stream small without visible loss at print resolution.
 */
private fun downscaleIfLarger(image: BufferedImage, dstWidth: Float, dstHeight: Float): BufferedImage {
    val targetW = ceil(dstWidth * DEFAULT_TARGET_DPI / 72f).toInt().coerceAtLeast(1)
    val targetH = ceil(dstHeight * DEFAULT_TARGET_DPI / 72f).toInt().coerceAtLeast(1)
    if (image.width <= targetW && image.height <= targetH) return image
    val scale = minOf(targetW.toFloat() / image.width, targetH.toFloat() / image.height)
    val newW = max(1, (image.width * scale).toInt())
    val newH = max(1, (image.height * scale).toInt())
    val scaled = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
    val g = scaled.createGraphics()
    try {
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.drawImage(image, 0, 0, newW, newH, null)
    } finally {
        g.dispose()
    }
    return scaled
}

/**
 * Computes the destination rectangle honouring [scale] for an image of
 * intrinsic size `srcWidth × srcHeight` drawn into
 * `(dstX, dstY, dstWidth, dstHeight)`. Mirrors the Android / iOS backends.
 */
private fun applyContentScale(
    scale: ContentScale,
    srcWidth: Float,
    srcHeight: Float,
    dstX: Float,
    dstY: Float,
    dstWidth: Float,
    dstHeight: Float,
): DstRect {
    if (srcWidth <= 0f || srcHeight <= 0f) {
        return DstRect(dstX, dstY, dstWidth, dstHeight)
    }
    val srcAspect = srcWidth / srcHeight
    val dstAspect = if (dstHeight == 0f) srcAspect else dstWidth / dstHeight
    return when (scale) {
        ContentScale.FillBounds, ContentScale.Crop -> DstRect(dstX, dstY, dstWidth, dstHeight)
        ContentScale.Fit -> if (srcAspect > dstAspect) {
            val drawHeight = dstWidth / srcAspect
            val offset = (dstHeight - drawHeight) / 2f
            DstRect(dstX, dstY + offset, dstWidth, drawHeight)
        } else {
            val drawWidth = dstHeight * srcAspect
            val offset = (dstWidth - drawWidth) / 2f
            DstRect(dstX + offset, dstY, drawWidth, dstHeight)
        }
    }
}
