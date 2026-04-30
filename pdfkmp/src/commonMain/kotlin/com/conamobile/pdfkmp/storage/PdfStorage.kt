package com.conamobile.pdfkmp.storage

import com.conamobile.pdfkmp.PdfDocument

/**
 * Platform-specific storage backend for [PdfDocument.save].
 *
 * Library users do not interact with this directly — they call
 * `document.save(StorageLocation.X, "file.pdf")` instead. Each platform
 * supplies an `actual` implementation that maps every [StorageLocation]
 * to the right native directory and writes the bytes accordingly.
 */
internal expect object PdfStorage {

    /**
     * Persists [document]'s bytes to the resolved location and returns
     * the absolute path (and content URI when applicable).
     *
     * @param document the document to write.
     * @param location where to save it — see [StorageLocation] for the
     *   per-platform semantics.
     * @param filename file name including the `.pdf` extension. The
     *   library does not append the extension automatically; pass it
     *   explicitly so the caller controls the final file name.
     */
    fun save(document: PdfDocument, location: StorageLocation, filename: String): SavedPdf
}

/**
 * Saves [this] document to the platform-specific [location] under
 * [filename]. Returns the absolute path and (on Android Q+ public writes)
 * the content URI of the persisted file.
 *
 * Example:
 * ```
 * val saved = pdf {
 *     page { text("Hi") }
 * }.save(StorageLocation.Downloads, "hello.pdf")
 *
 * println("Saved to ${saved.path}")
 * ```
 *
 * For [StorageLocation.Custom] with a full file path the [filename]
 * argument may be omitted — the library writes the bytes directly to the
 * supplied path:
 *
 * ```
 * document.save(StorageLocation.Custom("/storage/.../picked.pdf"))
 * ```
 *
 * @param location target directory; see [StorageLocation] for per-platform
 *   semantics.
 * @param filename file name including the extension; required for every
 *   variant except [StorageLocation.Custom] when the path already includes
 *   the file name.
 * @return [SavedPdf] containing the absolute path and optional content URI.
 */
public fun PdfDocument.save(location: StorageLocation, filename: String = ""): SavedPdf =
    PdfStorage.save(this, location, filename)
