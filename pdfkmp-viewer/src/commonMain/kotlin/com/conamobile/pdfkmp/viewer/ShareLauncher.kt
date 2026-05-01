package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable

/**
 * Action that hands a PDF off to the host platform's native share sheet.
 *
 * The viewer captures one of these via [rememberPdfShareAction] and wires
 * it to the optional share button. Each invocation writes [bytes] to a
 * platform-appropriate scratch location, then surfaces the system share
 * UI — `Intent.ACTION_SEND` via `FileProvider` on Android,
 * `UIActivityViewController` on iOS.
 *
 * @param bytes encoded PDF payload to share. The bytes are copied to the
 *   scratch file every time so callers can keep mutating their own
 *   buffer without affecting the share.
 * @param fileName user-visible filename (must include the `.pdf`
 *   extension). Surfaces in the share sheet, in "Save to Files", and in
 *   the email-attachment / messaging-app filename field.
 */
public fun interface PdfShareAction {
    public operator fun invoke(bytes: ByteArray, fileName: String)
}

/**
 * Returns a remembered [PdfShareAction] bound to the current platform's
 * share machinery. On Android this snapshots the [androidx.compose.ui.platform.LocalContext];
 * on iOS this snapshots the key window's root view controller.
 */
@Composable
internal expect fun rememberPdfShareAction(): PdfShareAction
