package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight
import java.io.ByteArrayOutputStream

/** Desktop can write annotations through PdfBox's mutable PDF model. */
internal actual val pdfViewerSupportsAnnotationExport: Boolean = true

/**
 * Interior opacity (`/CA`) applied to every burned-in highlight. Matches
 * the resting alpha of the on-screen overlay closely enough that the
 * exported document reads the same as the viewer — a deliberately low value
 * so the underlying glyphs stay legible through the highlight.
 */
private const val HIGHLIGHT_OPACITY: Float = 0.4f

/**
 * JVM / Desktop implementation of [writeAnnotationsIntoPdf], backed by
 * Apache PdfBox.
 *
 * The document is loaded, each [PdfViewerAnnotation] is added to its page as
 * a [PDAnnotationHighlight] (the standard `Highlight` text-markup subtype),
 * and the result is serialised back to bytes. The on-screen coordinate space
 * (PDF points, top-left origin, Y down) is flipped to the PDF's bottom-left
 * origin using each page's media-box height, and the box is also offset by
 * the media box's lower-left corner so pages whose origin isn't (0,0) still
 * land correctly.
 *
 * Runs on [Dispatchers.IO]; never throws — a malformed document or a write
 * failure returns `null` so the caller falls back to the original bytes.
 */
internal actual suspend fun writeAnnotationsIntoPdf(
    pdf: ByteArray,
    annotations: List<PdfViewerAnnotation>,
): ByteArray? = withContext(Dispatchers.IO) {
    if (pdf.isEmpty()) return@withContext null
    runCatching {
        Loader.loadPDF(pdf).use { document ->
            for (annotation in annotations) {
                val page = document.getPage(annotation.pageIndex) ?: continue
                page.getAnnotations().add(buildHighlight(annotation, page))
            }
            ByteArrayOutputStream().also { out -> document.save(out) }.toByteArray()
        }
    }.getOrNull()
}

/** Builds one PdfBox highlight annotation from a viewer annotation. */
private fun buildHighlight(annotation: PdfViewerAnnotation, page: PDPage): PDAnnotationHighlight {
    val media = page.mediaBox
    // The viewer's box is top-left origin / Y-down relative to the page's
    // intrinsic size; the page's own media box can start anywhere, so anchor
    // off its lower-left corner.
    val left = media.lowerLeftX + annotation.x
    val right = left + annotation.width
    // Flip Y: a top-left-origin top edge `y` measured from the page top maps
    // to `mediaTop - y` from the bottom; the box bottom is `mediaTop - (y + h)`.
    val top = media.upperRightY - annotation.y
    val bottom = top - annotation.height

    val rect = PDRectangle().apply {
        lowerLeftX = left
        lowerLeftY = bottom
        upperRightX = right
        upperRightY = top
    }

    // QuadPoints order per the PDF spec for text-markup annotations:
    // (x1 y1) top-left, (x2 y2) top-right, (x3 y3) bottom-left, (x4 y4)
    // bottom-right — all in default (bottom-left origin) user space.
    val quads = floatArrayOf(
        left, top,
        right, top,
        left, bottom,
        right, bottom,
    )

    return PDAnnotationHighlight().apply {
        rectangle = rect
        quadPoints = quads
        color = annotation.color.toPdfBoxColor()
        constantOpacity = HIGHLIGHT_OPACITY
        // Mark printable so the highlight survives a print/export round-trip
        // in downstream readers, matching the visible-everywhere intent.
        isPrinted = true
        // Link the annotation back to its page — PdfBox recommends this so the
        // /P back-reference is set (avoids trouble if the PDF is later signed).
        setPage(page)
    }
}

/** Maps a Compose [Color] onto an sRGB [PDColor]. */
private fun Color.toPdfBoxColor(): PDColor =
    PDColor(floatArrayOf(red, green, blue), PDDeviceRGB.INSTANCE)
