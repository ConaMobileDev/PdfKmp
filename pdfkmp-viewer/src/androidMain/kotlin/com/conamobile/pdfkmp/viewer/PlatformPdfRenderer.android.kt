package com.conamobile.pdfkmp.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Android implementation that walks the document with the framework's
 * [PdfRenderer]. Each page is drawn into an `ARGB_8888` bitmap with
 * `RENDER_MODE_FOR_DISPLAY` — the engine consumes the original vector
 * geometry and rasterises straight to the bitmap, so quality only
 * depends on [density] and never goes through an intermediate raster.
 *
 * The bytes are first staged on disk inside `cacheDir` because
 * `PdfRenderer` only accepts a [ParcelFileDescriptor] backed by a
 * file. The temp file is deleted immediately after the renderer is
 * closed; pages are decoded eagerly into memory before deletion so the
 * descriptor can be released right away.
 */
internal actual suspend fun renderPdfPages(
    bytes: ByteArray,
    density: Float,
): List<ImageBitmap> = withContext(Dispatchers.IO) {
    if (bytes.isEmpty()) return@withContext emptyList()

    val context = ViewerContextHolder.get()
    val tempFile = File(context.cacheDir, "pdfkmp-viewer-${UUID.randomUUID()}.pdf")
    tempFile.writeBytes(bytes)

    val descriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val safeDensity = max(density, 0.5f)

    try {
        PdfRenderer(descriptor).use { renderer ->
            val pages = ArrayList<ImageBitmap>(renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val width = max(1, (page.width * safeDensity).toInt())
                    val height = max(1, (page.height * safeDensity).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages += bitmap.asImageBitmap()
                }
            }
            pages
        }
    } finally {
        tempFile.delete()
    }
}
