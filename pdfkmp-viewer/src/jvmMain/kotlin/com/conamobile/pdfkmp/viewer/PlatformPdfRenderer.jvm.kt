package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.math.max

/**
 * JVM / Desktop implementation of [PdfPageRenderer], backed by Apache
 * PdfBox's [PDFRenderer].
 *
 * PdfBox's [PDDocument] / [PDFRenderer] are not thread-safe, so every render
 * is serialised through a [Mutex] — the same shape as the Android backend,
 * which serialises around `android.graphics.pdf.PdfRenderer`. Rendering runs
 * on [Dispatchers.IO] since rasterisation is CPU/heap heavy and must stay off
 * the UI thread.
 */
internal actual class PdfPageRenderer private constructor(
    private val document: PDDocument,
) {

    private val renderer = PDFRenderer(document)
    private val mutex = Mutex()
    private var closed = false

    actual val pageCount: Int = document.numberOfPages

    actual val pageSizes: List<PageSize> = (0 until pageCount).map { i ->
        val box = document.getPage(i).mediaBox
        PageSize(box.width, box.height)
    }

    actual suspend fun renderPage(index: Int, density: Float): ImageBitmap? = withContext(Dispatchers.IO) {
        if (index !in 0 until pageCount) return@withContext null
        mutex.withLock {
            if (closed) return@withLock null
            // PDF points are 72 DPI; density 2f → 144 DPI, matching the
            // Android/iOS convention of pixelSize = points × density.
            val dpi = max(density, 0.5f) * 72f
            runCatching {
                renderer.renderImageWithDPI(index, dpi, ImageType.RGB).toComposeImageBitmap()
            }.getOrNull()
        }
    }

    actual fun close() {
        if (closed) return
        closed = true
        runCatching { document.close() }
    }

    internal companion object {
        suspend fun open(bytes: ByteArray): PdfPageRenderer? = withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null
            runCatching {
                val document = Loader.loadPDF(bytes)
                if (document.numberOfPages == 0) {
                    document.close()
                    null
                } else {
                    PdfPageRenderer(document)
                }
            }.getOrNull()
        }
    }
}

internal actual suspend fun openPdfRenderer(bytes: ByteArray): PdfPageRenderer? =
    PdfPageRenderer.open(bytes)
