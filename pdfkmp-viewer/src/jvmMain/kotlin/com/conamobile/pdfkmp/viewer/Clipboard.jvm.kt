package com.conamobile.pdfkmp.viewer

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Desktop clipboard write via the AWT system [Toolkit] clipboard. Best-
 * effort — a headless environment (no display, so no system clipboard) is
 * swallowed rather than crashing the viewer.
 */
public actual fun pdfViewerCopyToClipboard(text: String) {
    if (text.isBlank()) return
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard
            .setContents(StringSelection(text), null)
    }
}
