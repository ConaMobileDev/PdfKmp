package com.conamobile.pdfkmp.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder

/**
 * Android clipboard write via the system [ClipboardManager], using the
 * application [Context] surfaced by [ViewerContextHolder] (the same
 * App-Startup-populated handle the share / save launchers use). Best-
 * effort — a missing context or an unavailable clipboard service is
 * swallowed rather than crashing a viewer mid-interaction.
 */
public actual fun pdfViewerCopyToClipboard(text: String) {
    if (text.isBlank()) return
    val context = runCatching { ViewerContextHolder.get() }.getOrNull() ?: return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("PDF text", text))
}
