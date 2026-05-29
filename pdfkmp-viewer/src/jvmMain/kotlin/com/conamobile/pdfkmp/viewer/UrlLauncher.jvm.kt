package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.net.URI

/**
 * Desktop hyperlink launcher — opens the URL in the user's default browser
 * via [Desktop.browse]. Falls through silently on malformed URLs or headless
 * environments, matching the Android/iOS contract that a tapped link never
 * surfaces a system error mid-render.
 */
@Composable
internal actual fun rememberPdfUrlLauncher(): PdfUrlLauncher = remember {
    PdfUrlLauncher { url ->
        runCatching {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI(url))
                }
            }
        }
    }
}
