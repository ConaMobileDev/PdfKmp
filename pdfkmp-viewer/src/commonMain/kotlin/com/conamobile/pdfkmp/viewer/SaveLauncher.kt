package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable

/**
 * Action that persists a PDF to a user-visible location on the device.
 *
 * Returned by [rememberPdfSaveAction] and typically wired to a "Save"
 * affordance in the host app's chrome (top app bar, action sheet, …).
 * Each invocation writes [bytes] to a platform-appropriate destination:
 *
 * - **Android** — `Downloads/<fileName>` via `MediaStore.Downloads`
 *   (API 29+) or the legacy `Environment.DIRECTORY_DOWNLOADS` on
 *   older devices, then surfaces a `Toast` confirmation. Visible in
 *   the system Files app under "Downloads" alongside any browser
 *   download.
 * - **iOS** — `<NSDocumentDirectory>/<fileName>` (the app's documents
 *   container, surfaced in the Files app under "On My iPhone /
 *   <AppName>"). iOS doesn't expose a true "Downloads" folder; this
 *   matches the behaviour of system apps that offer "Save".
 *
 * @param bytes encoded PDF payload to persist.
 * @param fileName user-visible filename (must include the `.pdf`
 *   extension). The implementation overwrites any existing file with
 *   the same name in the destination — callers that need uniqueness
 *   should add a timestamp / counter before passing it in.
 */
public fun interface PdfSaveAction {
    public operator fun invoke(bytes: ByteArray, fileName: String)
}

/**
 * Returns a remembered [PdfSaveAction] bound to the current platform's
 * filesystem APIs. Pair it with a `showSaveButton = false`-style
 * disable on any built-in chrome and host the save affordance from a
 * spot in your own UI — typically a top-app-bar [androidx.compose.material3.IconButton].
 */
@Composable
public expect fun rememberPdfSaveAction(): PdfSaveAction
