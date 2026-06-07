package com.conamobile.pdfkmp.viewer

/**
 * Why a [PdfViewer] / [KmpPdfViewer] could not open a document, surfaced to
 * the host via the `onDocumentError` callback and used internally to pick
 * the inline error message instead of crashing.
 *
 * Distinguishing a password problem from a generic decode failure lets the
 * host prompt for (or correct) a password rather than just reporting "this
 * file is broken".
 */
public enum class PdfViewerError {

    /**
     * The document is encrypted and no password (or the wrong password) was
     * supplied. On Desktop the right password reopens it; on Android, where
     * `android.graphics.pdf.PdfRenderer` cannot open encrypted files at all,
     * this is terminal regardless of the password.
     */
    PasswordRequired,

    /**
     * The bytes are not a PDF this platform can render — empty, truncated,
     * corrupt, or in a format the platform decoder rejects.
     */
    CannotOpen,

    /**
     * Loading the bytes failed before decoding even started (network error,
     * missing file, unreadable content URI, etc.).
     */
    LoadFailed,
}
