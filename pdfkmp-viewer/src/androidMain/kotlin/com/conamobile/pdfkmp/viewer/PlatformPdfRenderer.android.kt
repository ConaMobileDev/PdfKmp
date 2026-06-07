package com.conamobile.pdfkmp.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Android implementation of [PdfPageRenderer].
 *
 * [PdfRenderer] is single-threaded and only allows one
 * [PdfRenderer.Page] to be open at a time, so [renderPage] funnels
 * every request through a [Mutex]. The descriptor and the cache file
 * stay alive for the lifetime of the handle and are released by
 * [close] once the viewer leaves composition.
 */
internal actual class PdfPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val tempFile: File,
    private val renderer: PdfRenderer,
) {
    private val mutex = Mutex()
    private var closed = false

    actual val pageCount: Int = renderer.pageCount

    actual val pageSizes: List<PageSize> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { page ->
            PageSize(page.width.toFloat(), page.height.toFloat())
        }
    }

    actual suspend fun renderPage(index: Int, density: Float, invert: Boolean): ImageBitmap? {
        if (index !in 0 until pageCount) return null
        return mutex.withLock {
            if (closed) return@withLock null
            withContext(Dispatchers.IO) {
                renderer.openPage(index).use { page ->
                    val safeDensity = max(density, 0.5f)
                    val width = max(1, (page.width * safeDensity).toInt())
                    val height = max(1, (page.height * safeDensity).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val output = if (invert) invertColors(bitmap) else bitmap
                    output.asImageBitmap()
                }
            }
        }
    }

    /**
     * Returns a colour-inverted copy of [source] for the viewer's
     * dark-mode surface, drawing it through a [ColorMatrixColorFilter]
     * that maps each RGB channel `out = −in + 255` while leaving alpha
     * at `1×`. The matrix form keeps the inversion on the GPU-friendly
     * paint path rather than walking every pixel in Kotlin.
     */
    private fun invertColors(source: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(inverted).drawBitmap(source, 0f, 0f, paint)
        return inverted
    }

    actual fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        runCatching { tempFile.delete() }
    }

    internal companion object {
        suspend fun open(bytes: ByteArray, password: String?): PdfOpenResult =
            withContext(Dispatchers.IO) {
                if (bytes.isEmpty()) return@withContext PdfOpenResult.CannotOpen
                val context = ViewerContextHolder.get()
                val tempFile = File(context.cacheDir, "pdfkmp-viewer-${UUID.randomUUID()}.pdf")
                tempFile.writeBytes(bytes)
                try {
                    val descriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(descriptor)
                    PdfOpenResult.Success(PdfPageRenderer(descriptor, tempFile, renderer))
                } catch (e: SecurityException) {
                    // PdfRenderer throws SecurityException for a
                    // password-protected file — it has NO password API, so this
                    // is terminal on Android regardless of [password]. Surface it
                    // as PasswordRequired so the host can explain why (rather than
                    // a generic "broken file") even though no password will help.
                    tempFile.delete()
                    PdfOpenResult.PasswordRequired
                } catch (e: Throwable) {
                    tempFile.delete()
                    PdfOpenResult.CannotOpen
                }
            }
    }
}

internal actual suspend fun openPdfRenderer(bytes: ByteArray, password: String?): PdfOpenResult =
    PdfPageRenderer.open(bytes, password)
