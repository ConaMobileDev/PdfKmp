package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File

/**
 * Desktop "Share" — Desktop OSes have no system share sheet equivalent to
 * Android's `ACTION_SEND` or iOS's `UIActivityViewController`, so the closest
 * useful behaviour is to write the PDF to a temp file and hand it to the OS
 * default handler via [Desktop.open] (Preview, Acrobat, Evince, …). From
 * there the user can print, forward, or save-as. No-ops gracefully on
 * headless or unsupported environments.
 */
@Composable
public actual fun rememberPdfShareAction(): PdfShareAction = remember {
    PdfShareAction { bytes, fileName ->
        // The shared sanitizer strips directory components (both separator
        // styles, unlike File(...).name on a Unix JVM) so a caller-supplied
        // name can't escape the temp dir; blank falls back like the
        // Android/iOS launchers do.
        val safeName = sanitizePdfFileName(fileName)
        // Write + launch the external viewer off the Compose/AWT UI thread so
        // the I/O and process spawn don't stutter the UI.
        Thread {
            runCatching {
                val file = File(System.getProperty("java.io.tmpdir") ?: ".", safeName)
                file.writeBytes(bytes)
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file)
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
