package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable

/**
 * Action that hands a PDF off to the host platform's native print
 * pipeline.
 *
 * The viewer captures one of these via [rememberPdfPrintAction] and
 * wires it to the optional print button. Each invocation streams
 * [bytes] into the platform's print subsystem and surfaces its system
 * UI — `PrintManager` + a `PrintDocumentAdapter` on Android,
 * `UIPrintInteractionController` on iOS, `java.awt.print.PrinterJob`
 * (driving PdfBox's `PDFPageable`) on Desktop.
 *
 * @param bytes encoded PDF payload to print. The bytes are read fresh
 *   on every invocation so callers can keep mutating their own buffer
 *   without affecting an in-flight print job.
 * @param fileName user-visible job name shown in the print dialog /
 *   queue (must include the `.pdf` extension on the platforms that
 *   surface it).
 */
public fun interface PdfPrintAction {
    public operator fun invoke(bytes: ByteArray, fileName: String)
}

/**
 * Returns a remembered [PdfPrintAction] bound to the current
 * platform's print machinery. On Android this snapshots the
 * [androidx.compose.ui.platform.LocalContext]; on iOS it presents the
 * shared [platform.UIKit.UIPrintInteractionController]; on Desktop it
 * shows the native print dialog off the UI thread.
 *
 * Useful when the host app wants its own print affordance — typically
 * a toolbar / app-bar icon — instead of the built-in topbar button
 * exposed by [KmpPdfViewer] (`showPrint = true`).
 */
@Composable
public expect fun rememberPdfPrintAction(): PdfPrintAction
