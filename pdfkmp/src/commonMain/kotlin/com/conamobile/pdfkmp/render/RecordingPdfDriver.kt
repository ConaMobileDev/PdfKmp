package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.text.PdfHyperlink
import com.conamobile.pdfkmp.text.PdfTextRun
import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Transparent [PdfDriver] decorator that watches every [PdfCanvas.drawText]
 * call on the wrapped backend and turns it into a [PdfTextRun]. Every
 * other operation (rectangles, paths, images, state, …) is forwarded
 * verbatim, so the decorator is byte-for-byte equivalent to the inner
 * driver from the renderer's perspective.
 *
 * Used by the top-level `pdf { … }` / `pdfAsync { … }` entry points to
 * snapshot the laid-out text positions while the real PDF is being
 * encoded — feeding `:pdfkmp-viewer`'s text-selection overlay without
 * a second layout pass.
 */
internal class RecordingPdfDriver(
    private val delegate: PdfDriver,
) : PdfDriver {

    override val fontMetrics: FontMetrics get() = delegate.fontMetrics

    private val collectedRuns = mutableListOf<PdfTextRun>()
    private val collectedLinks = mutableListOf<PdfHyperlink>()
    private var pageIndex: Int = -1

    /** Snapshot of every text run captured during the document's render. */
    val textRuns: List<PdfTextRun> get() = collectedRuns.toList()

    /** Snapshot of every hyperlink annotation captured during render. */
    val hyperlinks: List<PdfHyperlink> get() = collectedLinks.toList()

    override fun beginPage(size: PageSize): PdfCanvas {
        pageIndex += 1
        return RecordingPdfCanvas(
            delegate = delegate.beginPage(size),
            metrics = delegate.fontMetrics,
            pageIndex = pageIndex,
            runSink = collectedRuns,
            linkSink = collectedLinks,
        )
    }

    override fun endPage() {
        delegate.endPage()
    }

    override fun finish(): ByteArray = delegate.finish()
}

/**
 * [PdfCanvas] decorator paired with [RecordingPdfDriver]. Records every
 * drawText into [sink] and forwards the draw call to [delegate]
 * unchanged.
 */
private class RecordingPdfCanvas(
    private val delegate: PdfCanvas,
    private val metrics: FontMetrics,
    private val pageIndex: Int,
    private val runSink: MutableList<PdfTextRun>,
    private val linkSink: MutableList<PdfHyperlink>,
) : PdfCanvas {

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        if (text.isNotEmpty()) {
            val measured = metrics.measure(text, style)
            runSink += PdfTextRun(
                pageIndex = pageIndex,
                text = text,
                xPoints = x,
                yPoints = y,
                widthPoints = measured.width,
                // Use ascent + descent (visible glyph footprint) rather
                // than lineHeight, so overlay bounding boxes hug the
                // glyphs the user actually sees.
                heightPoints = measured.ascent + measured.descent,
                fontSizePoints = style.fontSize.value,
            )
        }
        delegate.drawText(text, x, y, style)
    }

    override fun linkAnnotation(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        url: String,
    ) {
        if (url.isNotEmpty()) {
            linkSink += PdfHyperlink(
                pageIndex = pageIndex,
                xPoints = x,
                yPoints = y,
                widthPoints = width,
                heightPoints = height,
                url = url,
            )
        }
        delegate.linkAnnotation(x, y, width, height, url)
    }

    override fun drawRect(
        x: Float, y: Float, width: Float, height: Float, color: PdfColor,
    ) = delegate.drawRect(x, y, width, height, color)

    override fun drawRoundedRect(
        x: Float, y: Float, width: Float, height: Float,
        cornerRadius: Float, color: PdfColor,
    ) = delegate.drawRoundedRect(x, y, width, height, cornerRadius, color)

    override fun strokeRect(
        x: Float, y: Float, width: Float, height: Float,
        color: PdfColor, thickness: Float,
    ) = delegate.strokeRect(x, y, width, height, color, thickness)

    override fun strokeRoundedRect(
        x: Float, y: Float, width: Float, height: Float,
        cornerRadius: Float, color: PdfColor, thickness: Float,
    ) = delegate.strokeRoundedRect(x, y, width, height, cornerRadius, color, thickness)

    override fun drawLine(
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: PdfColor, thickness: Float, style: LineStyle,
    ) = delegate.drawLine(x1, y1, x2, y2, color, thickness, style)

    override fun saveState() = delegate.saveState()

    override fun restoreState() = delegate.restoreState()

    override fun clipRect(x: Float, y: Float, width: Float, height: Float) =
        delegate.clipRect(x, y, width, height)

    override fun clipRoundedRect(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float,
    ) = delegate.clipRoundedRect(x, y, width, height, cornerRadius)

    override fun clipPath(commands: List<PathCommand>) = delegate.clipPath(commands)

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) = delegate.drawPath(commands, fill, strokeColor, strokeWidth)

    override fun drawImage(
        bytes: ByteArray, x: Float, y: Float, width: Float, height: Float,
        contentScale: ContentScale, sourceTop: Float, sourceBottom: Float,
        allowDownScale: Boolean,
    ) = delegate.drawImage(
        bytes, x, y, width, height, contentScale, sourceTop, sourceBottom, allowDownScale,
    )
}
