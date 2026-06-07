package com.conamobile.pdfkmp.storage

import com.conamobile.pdfkmp.PdfDocument
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Web (browser) storage backend: every [StorageLocation] maps to the one
 * thing a web page may do with a file — trigger a download. The browser
 * decides the final directory (usually the user's Downloads folder), so
 * the returned [SavedPdf.path] is just the file name.
 *
 * Only meaningful in a browser tab; calling [save] from a non-browser
 * Wasm host (e.g. the Node test runner) fails because `document` does
 * not exist there.
 */
internal actual object PdfStorage {

    actual fun save(document: PdfDocument, location: StorageLocation, filename: String): SavedPdf {
        // Custom("dir/name.pdf") with an empty filename carries the name
        // in the path; everything else uses the explicit filename.
        val resolvedName = when {
            filename.isNotEmpty() -> filename
            location is StorageLocation.Custom -> location.path.substringAfterLast('/')
            else -> "document.pdf"
        }
        @OptIn(ExperimentalEncodingApi::class)
        val base64 = Base64.encode(document.toByteArray())
        triggerBrowserDownload(base64, resolvedName)
        return SavedPdf(path = resolvedName, uri = null)
    }
}

/**
 * Builds a Blob from the base64-encoded PDF bytes and clicks a synthetic
 * anchor — the standard browser download dance. Base64 is the bridge
 * because Wasm byte arrays don't cross the JS boundary directly.
 */
private fun triggerBrowserDownload(base64: String, filename: String): Unit = js(
    """{
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const blob = new Blob([bytes], { type: 'application/pdf' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }"""
)
