package com.conamobile.pdfkmp.viewer

/**
 * Reads the bytes behind [uri] on whichever runtime is hosting the
 * library. Implementations support the schemes the platform exposes
 * natively — see the `actual` declarations for the exact list.
 *
 * - **Android** — `content://`, `file://`, `http://`, `https://`,
 *   `asset:///` (resolved through `Context.assets`), and bare paths
 *   (treated as filesystem paths).
 * - **iOS** — `file://`, `http://`, `https://`, and bundle resource
 *   paths (resolved through `Bundle.main`).
 *
 * Network reads run on the platform's default async dispatcher; they
 * are not optimised for large payloads. Apps that need streaming or
 * progress reporting should fetch the bytes themselves and hand them
 * to [PdfViewerScreen]'s `ByteArray` / [PdfSource] overload instead.
 *
 * @throws Exception if the URI can't be opened (unknown scheme,
 *   network error, file not found, etc.). [PdfViewerScreen]'s URI
 *   overload catches these and surfaces an inline error UI.
 */
internal expect suspend fun loadPdfBytesFromUri(uri: String): ByteArray
