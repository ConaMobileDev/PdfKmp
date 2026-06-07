package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.apache.pdfbox.Loader
import org.apache.pdfbox.printing.PDFPageable
import java.awt.print.PrinterJob

/**
 * Desktop "Print" — drives `java.awt.print.PrinterJob` with PdfBox's
 * [PDFPageable], the same PdfBox stack the Desktop viewer already uses
 * to rasterise pages on screen. [PDFPageable] feeds the embedded PDF to
 * the printer at full vector fidelity (no intermediate rasterisation),
 * so what prints matches the on-screen document exactly.
 *
 * The native print dialog ([PrinterJob.printDialog]) and the
 * potentially-slow spool both run off the Compose / AWT UI thread on a
 * daemon thread so the viewer never stutters while a job is queued.
 * Failures (no printer installed, user cancel, headless environment)
 * are swallowed — a print tap must never crash the host app.
 */
@Composable
public actual fun rememberPdfPrintAction(): PdfPrintAction = remember {
    PdfPrintAction { bytes, fileName ->
        val jobName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"
        Thread {
            runCatching {
                Loader.loadPDF(bytes).use { document ->
                    val job = PrinterJob.getPrinterJob()
                    job.setJobName(jobName)
                    job.setPageable(PDFPageable(document))
                    // printDialog() returns false when the user cancels;
                    // only spool the job once they confirm.
                    if (job.printDialog()) {
                        job.print()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
