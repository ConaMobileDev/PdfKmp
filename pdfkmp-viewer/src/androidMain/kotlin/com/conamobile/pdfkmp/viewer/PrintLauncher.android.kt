package com.conamobile.pdfkmp.viewer

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.FileOutputStream

/**
 * Captures the (Activity) [Context] once and returns a
 * [PdfPrintAction] that streams the PDF bytes through Android's
 * [PrintManager] + a [PrintDocumentAdapter].
 *
 * Unlike the share / save actions, printing deliberately holds the
 * *Activity* context rather than `applicationContext`: [PrintManager]
 * attaches its system print UI to the hosting activity's task, and the
 * generic print dialog refuses to surface from a bare application
 * context. Inside a [KmpPdfViewer] composition the
 * [LocalContext] is exactly that activity.
 *
 * The adapter is fully in-memory: [onLayout] reports a single document
 * of [PrintDocumentInfo.CONTENT_TYPE_DOCUMENT] with an unknown page
 * count (the framework / printer driver re-paginates the PDF itself),
 * and [onWrite] copies the byte payload straight into the destination
 * [ParcelFileDescriptor]. No temp file is touched.
 */
@Composable
public actual fun rememberPdfPrintAction(): PdfPrintAction {
    val context = LocalContext.current
    return remember(context) { AndroidPrintAction(context) }
}

private class AndroidPrintAction(private val context: Context) : PdfPrintAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Log.w("PdfKmpViewer", "Print service unavailable; ignoring print request")
            return
        }
        val jobName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"
        printManager.print(
            jobName,
            BytesPrintDocumentAdapter(jobName, bytes),
            PrintAttributes.Builder().build(),
        )
    }
}

/**
 * Streams an in-memory PDF payload to the print framework. The bytes
 * are already a fully laid-out PDF, so [onLayout] does no work beyond
 * advertising the document, and [onWrite] simply copies the buffer to
 * the descriptor the framework hands us.
 */
private class BytesPrintDocumentAdapter(
    private val jobName: String,
    private val bytes: ByteArray,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            // The framework / printer driver re-paginates the embedded
            // PDF, so we don't pre-count pages here.
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        // Second arg flags whether the layout changed; the content is
        // immutable per job, so `true` is the safe, simple answer.
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        if (destination == null) {
            callback?.onWriteFailed("No destination descriptor")
            return
        }
        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                out.write(bytes)
                out.flush()
            }
            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
            } else {
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (t: Throwable) {
            Log.w("PdfKmpViewer", "Failed to stream PDF to printer", t)
            callback?.onWriteFailed(t.message)
        }
    }
}
