package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
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

    actual suspend fun renderPage(index: Int, density: Float, invert: Boolean): ImageBitmap? {
        if (index !in 0 until pageCount) return null
        // Acquire the lock first, then switch to IO — a coroutine merely
        // waiting for the (single-threaded PdfBox) renderer shouldn't park an
        // IO worker thread. Mirrors the Android backend's ordering.
        return mutex.withLock {
            if (closed) return@withLock null
            withContext(Dispatchers.IO) {
                // PDF points are 72 DPI; density 2f → 144 DPI, matching the
                // Android/iOS convention of pixelSize = points × density.
                val dpi = max(density, 0.5f) * 72f
                runCatching {
                    val raw = renderer.renderImageWithDPI(index, dpi, ImageType.RGB)
                    // Dark-mode: invert the BufferedImage before it crosses
                    // into Skia — see [invertRgb].
                    val image = if (invert) invertRgb(raw) else raw
                    image.toComposeImageBitmap()
                }.getOrNull()
            }
        }
    }

    actual fun close() {
        if (closed) return
        closed = true
        runCatching { document.close() }
    }

    internal companion object {
        suspend fun open(bytes: ByteArray, password: String?): PdfOpenResult =
            withContext(Dispatchers.IO) {
                if (bytes.isEmpty()) return@withContext PdfOpenResult.CannotOpen
                try {
                    // PdfBox accepts an empty string for an unencrypted document
                    // and uses the password to unlock an encrypted one. A wrong
                    // / missing password throws InvalidPasswordException, which we
                    // map to a distinct PasswordRequired result.
                    val document = Loader.loadPDF(bytes, password ?: "")
                    if (document.numberOfPages == 0) {
                        document.close()
                        PdfOpenResult.CannotOpen
                    } else {
                        // For an encrypted document opened with the USER password,
                        // PdfBox enforces the access permissions — which can block
                        // PDFRenderer from extracting content to rasterise. We only
                        // ever render to an on-screen bitmap (never re-save this
                        // in-memory document), so lift the restrictions so the
                        // preview works regardless of the copy/print flags. The
                        // share / save bytes the viewer hands out are the original
                        // encrypted file, untouched.
                        if (document.isEncrypted) {
                            document.setAllSecurityToBeRemoved(true)
                        }
                        PdfOpenResult.Success(PdfPageRenderer(document))
                    }
                } catch (e: InvalidPasswordException) {
                    PdfOpenResult.PasswordRequired
                } catch (t: Throwable) {
                    PdfOpenResult.CannotOpen
                }
            }
    }
}

internal actual suspend fun openPdfRenderer(bytes: ByteArray, password: String?): PdfOpenResult =
    PdfPageRenderer.open(bytes, password)
