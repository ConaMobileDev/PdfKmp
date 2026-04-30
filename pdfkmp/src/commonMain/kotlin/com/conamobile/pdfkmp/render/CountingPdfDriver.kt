package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Discard-and-count [PdfDriver] used for the dry-run pass that measures
 * how many physical pages a document will produce. Rendering with this
 * driver runs through the real layout / page-break logic but emits no
 * native draw calls, so a second render pass with a real driver can use
 * the resulting page count for header / footer "Page X of Y" interpolation.
 *
 * Reuses [fontMetrics] from the real driver so layout decisions match
 * the eventual pass exactly.
 */
internal class CountingPdfDriver(
    override val fontMetrics: FontMetrics,
) : PdfDriver {

    var pageCount: Int = 0
        private set

    private val canvas: PdfCanvas = NoOpPdfCanvas

    override fun beginPage(size: PageSize): PdfCanvas {
        pageCount++
        return canvas
    }

    override fun endPage() = Unit

    override fun finish(): ByteArray = ByteArray(0)
}

/** [PdfCanvas] that swallows every draw call. Used by [CountingPdfDriver]. */
private object NoOpPdfCanvas : PdfCanvas {
    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) = Unit
    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: PdfColor) = Unit
    override fun drawRoundedRect(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float, color: PdfColor,
    ) = Unit
    override fun strokeRect(
        x: Float, y: Float, width: Float, height: Float, color: PdfColor, thickness: Float,
    ) = Unit
    override fun strokeRoundedRect(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float, color: PdfColor, thickness: Float,
    ) = Unit
    override fun drawLine(
        x1: Float, y1: Float, x2: Float, y2: Float, color: PdfColor, thickness: Float, style: LineStyle,
    ) = Unit
    override fun saveState() = Unit
    override fun restoreState() = Unit
    override fun clipRect(x: Float, y: Float, width: Float, height: Float) = Unit
    override fun clipRoundedRect(x: Float, y: Float, width: Float, height: Float, cornerRadius: Float) = Unit
    override fun clipPath(commands: List<PathCommand>) = Unit
    override fun drawPath(
        commands: List<PathCommand>, fill: PdfPaint?, strokeColor: PdfColor?, strokeWidth: Float,
    ) = Unit
    override fun drawImage(
        bytes: ByteArray, x: Float, y: Float, width: Float, height: Float,
        contentScale: ContentScale, sourceTop: Float, sourceBottom: Float,
    ) = Unit
}
