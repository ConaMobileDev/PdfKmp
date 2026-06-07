package com.conamobile.pdfkmp.test

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.render.PdfCanvas
import com.conamobile.pdfkmp.render.PdfDriver
import com.conamobile.pdfkmp.render.PdfDriverFactory
import com.conamobile.pdfkmp.render.TextMetrics
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Drawing operations recorded by [FakePdfCanvas]. Tests assert against this
 * sealed hierarchy to verify what the renderer asked the canvas to draw.
 */
sealed interface DrawCall {
    data class Text(val text: String, val x: Float, val y: Float, val style: TextStyle) : DrawCall
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val color: PdfColor) : DrawCall
    data class RoundedRect(val x: Float, val y: Float, val width: Float, val height: Float, val cornerRadius: Float, val color: PdfColor) : DrawCall
    data class StrokeRect(val x: Float, val y: Float, val width: Float, val height: Float, val color: PdfColor, val thickness: Float) : DrawCall
    data class StrokeRoundedRect(val x: Float, val y: Float, val width: Float, val height: Float, val cornerRadius: Float, val color: PdfColor, val thickness: Float) : DrawCall
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: PdfColor, val thickness: Float, val style: LineStyle = LineStyle.Solid) : DrawCall
    data object SaveState : DrawCall
    data object RestoreState : DrawCall
    data class ClipRect(val x: Float, val y: Float, val width: Float, val height: Float) : DrawCall
    data class ClipRoundedRect(val x: Float, val y: Float, val width: Float, val height: Float, val cornerRadius: Float) : DrawCall
    data class ClipPath(val commands: List<PathCommand>) : DrawCall
    data class Image(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val byteSize: Int,
        val contentScale: ContentScale,
        val sourceTop: Float,
        val sourceBottom: Float,
        val allowDownScale: Boolean,
        val altText: String? = null,
    ) : DrawCall
    data class FormTextField(
        val name: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val value: String,
        val multiline: Boolean,
        val fontSizePt: Float,
    ) : DrawCall
    data class FormCheckBox(
        val name: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val checked: Boolean,
    ) : DrawCall
    data class Path(
        val commands: List<PathCommand>,
        val fill: PdfPaint?,
        val strokeColor: PdfColor?,
        val strokeWidth: Float,
    ) : DrawCall
    data class Bookmark(val title: String, val level: Int, val y: Float) : DrawCall
    data class NamedDestination(val name: String, val y: Float) : DrawCall
    data class LinkToDestination(
        val name: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) : DrawCall
    data class Rotate(val degrees: Float, val pivotX: Float, val pivotY: Float) : DrawCall
    data class BeginTransparencyGroup(val alpha: Float) : DrawCall
    data object EndTransparencyGroup : DrawCall
}

/** Records draw calls made through it. One instance per page. */
class FakePdfCanvas : PdfCanvas {
    val calls: MutableList<DrawCall> = mutableListOf()

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        calls += DrawCall.Text(text, x, y, style)
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: PdfColor) {
        calls += DrawCall.Rect(x, y, width, height, color)
    }

    override fun drawRoundedRect(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float, color: PdfColor,
    ) {
        calls += DrawCall.RoundedRect(x, y, width, height, cornerRadius, color)
    }

    override fun strokeRect(
        x: Float, y: Float, width: Float, height: Float, color: PdfColor, thickness: Float,
    ) {
        calls += DrawCall.StrokeRect(x, y, width, height, color, thickness)
    }

    override fun strokeRoundedRect(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float, color: PdfColor, thickness: Float,
    ) {
        calls += DrawCall.StrokeRoundedRect(x, y, width, height, cornerRadius, color, thickness)
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: PdfColor, thickness: Float, style: LineStyle) {
        calls += DrawCall.Line(x1, y1, x2, y2, color, thickness, style)
    }

    override fun saveState() { calls += DrawCall.SaveState }
    override fun restoreState() { calls += DrawCall.RestoreState }
    override fun clipRect(x: Float, y: Float, width: Float, height: Float) {
        calls += DrawCall.ClipRect(x, y, width, height)
    }
    override fun clipRoundedRect(x: Float, y: Float, width: Float, height: Float, cornerRadius: Float) {
        calls += DrawCall.ClipRoundedRect(x, y, width, height, cornerRadius)
    }
    override fun clipPath(commands: List<PathCommand>) {
        calls += DrawCall.ClipPath(commands)
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
        calls += DrawCall.Image(
            x = x,
            y = y,
            width = width,
            height = height,
            byteSize = bytes.size,
            contentScale = contentScale,
            sourceTop = sourceTop,
            sourceBottom = sourceBottom,
            allowDownScale = allowDownScale,
            altText = altText,
        )
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
        calls += DrawCall.FormTextField(name, x, y, width, height, value, multiline, fontSizePt)
    }

    override fun formCheckBox(name: String, x: Float, y: Float, size: Float, checked: Boolean) {
        calls += DrawCall.FormCheckBox(name, x, y, size, checked)
    }

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) {
        calls += DrawCall.Path(commands, fill, strokeColor, strokeWidth)
    }

    override fun bookmark(title: String, level: Int, y: Float) {
        calls += DrawCall.Bookmark(title, level, y)
    }

    override fun rotate(degrees: Float, pivotX: Float, pivotY: Float) {
        calls += DrawCall.Rotate(degrees, pivotX, pivotY)
    }

    override fun beginTransparencyGroup(alpha: Float) {
        calls += DrawCall.BeginTransparencyGroup(alpha)
    }

    override fun endTransparencyGroup() {
        calls += DrawCall.EndTransparencyGroup
    }

    override fun namedDestination(name: String, y: Float) {
        calls += DrawCall.NamedDestination(name, y)
    }

    override fun linkToDestination(name: String, x: Float, y: Float, width: Float, height: Float) {
        calls += DrawCall.LinkToDestination(name, x, y, width, height)
    }
}

/**
 * Deterministic [FontMetrics] used by tests: every glyph is exactly one PDF
 * point wide, line height equals the font size. This lets layout tests
 * assert pixel-precise wrapping decisions without depending on a real font.
 */
class FixedWidthFontMetrics(
    private val charWidth: Float = 1f,
) : FontMetrics {
    override fun measure(text: String, style: TextStyle): TextMetrics {
        val width = text.length * charWidth * style.fontSize.value
        val ascent = style.fontSize.value * 0.8f
        val descent = style.fontSize.value * 0.2f
        return TextMetrics(width = width, ascent = ascent, descent = descent, lineGap = 0f)
    }
}

/**
 * In-memory [PdfDriver] that accumulates draw calls into a list of
 * [FakePage]s and returns a deterministic byte sentinel from [finish].
 *
 * Useful in `commonTest` because the entire rendering pipeline can be driven
 * end-to-end without touching Android or iOS APIs.
 */
class FakePdfDriver(
    val metadata: PdfMetadata,
    val customFonts: List<PdfFont.Custom>,
    private val metricsImpl: FontMetrics = FixedWidthFontMetrics(),
) : PdfDriver {

    val pages: MutableList<FakePage> = mutableListOf()
    private var currentPage: FakePage? = null
    var finished: Boolean = false
        private set

    override val fontMetrics: FontMetrics get() = metricsImpl

    override fun beginPage(size: PageSize): PdfCanvas {
        check(currentPage == null) { "endPage() must be called before beginPage()" }
        val page = FakePage(size, FakePdfCanvas())
        currentPage = page
        pages += page
        return page.canvas
    }

    override fun endPage() {
        check(currentPage != null) { "endPage() called without a matching beginPage()" }
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(currentPage == null) { "endPage() must be called before finish()" }
        finished = true
        // Return a deterministic sentinel so tests can verify finish() ran.
        // The bytes spell "PDFK" in ASCII — we never claim this is a valid
        // PDF; tests inspect [pages] rather than the byte output.
        return byteArrayOf(0x50, 0x44, 0x46, 0x4B)
    }
}

/** Pairs a page size with the canvas that received its draws. */
class FakePage(val size: PageSize, val canvas: FakePdfCanvas)

/** Factory that hands out [FakePdfDriver] instances. Useful for `pdf(factory = ...)`. */
class FakePdfDriverFactory(
    private val metricsImpl: FontMetrics = FixedWidthFontMetrics(),
    private val onCreate: (FakePdfDriver) -> Unit = {},
) : PdfDriverFactory {

    val drivers: MutableList<FakePdfDriver> = mutableListOf()

    override fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver {
        val driver = FakePdfDriver(metadata, customFonts, metricsImpl)
        drivers += driver
        onCreate(driver)
        return driver
    }
}
