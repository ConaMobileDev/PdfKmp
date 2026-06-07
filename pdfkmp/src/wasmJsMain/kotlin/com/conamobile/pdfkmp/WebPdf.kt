package com.conamobile.pdfkmp

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Opens this document in a new browser tab using the browser's own PDF
 * viewer (PDFium in Chrome/Edge, PDF.js inside Firefox, Apple's viewer
 * in Safari) — zoom, search, print, and download come for free, which is
 * why PdfKmp ships no embedded viewer for the web target.
 *
 * Pop-up blockers may suppress the tab unless this is called from a user
 * gesture (e.g. a click handler). Fall back to
 * [com.conamobile.pdfkmp.storage.save] (a download) when the tab fails
 * to open.
 *
 * @return `true` when the browser opened the tab, `false` when a pop-up
 *   blocker (or a non-browser host) prevented it.
 */
public fun PdfDocument.openInNewTab(): Boolean {
    @OptIn(ExperimentalEncodingApi::class)
    val base64 = Base64.encode(toByteArray())
    return openPdfInNewTab(base64)
}

/**
 * Blob-URL + `window.open` — the object URL is revoked after a minute,
 * long after the viewer tab has taken its own reference to the data.
 */
private fun openPdfInNewTab(base64: String): Boolean = js(
    """{
        try {
            const binary = atob(base64);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            const blob = new Blob([bytes], { type: 'application/pdf' });
            const url = URL.createObjectURL(blob);
            const tab = window.open(url, '_blank');
            setTimeout(() => URL.revokeObjectURL(url), 60000);
            return tab !== null;
        } catch (e) {
            return false;
        }
    }"""
)
