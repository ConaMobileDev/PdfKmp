package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop "Save" — pops the OS-native **Save As** dialog (`java.awt.FileDialog`
 * in `SAVE` mode), pre-filled with the suggested file name and defaulting to
 * the user's `~/Downloads` folder. This is the desktop analogue of Android's
 * `MediaStore` + Toast and iOS's document picker: the dialog itself is the
 * visible confirmation, and the user controls exactly where the file lands.
 *
 * Returns silently if the user cancels. Failures (read-only target, disk
 * full) are swallowed so a save tap never crashes the viewer.
 */
@Composable
public actual fun rememberPdfSaveAction(): PdfSaveAction = remember {
    PdfSaveAction { bytes, fileName ->
        runCatching {
            val downloads = File(System.getProperty("user.home") ?: ".", "Downloads")
            val dialog = FileDialog(null as Frame?, "Save PDF", FileDialog.SAVE)
            // Suggest a bare name (no directory components); blank falls back.
            dialog.file = sanitizePdfFileName(fileName)
            if (downloads.isDirectory) dialog.directory = downloads.absolutePath
            // Modal dialog must run on the EDT (it is — Compose onClick runs there).
            dialog.isVisible = true

            // dialog.file is null when the user cancels.
            val chosenName = dialog.file ?: return@runCatching
            val chosenDir = dialog.directory ?: downloads.absolutePath
            // The disk write can be large; keep it off the UI thread.
            Thread {
                runCatching { File(chosenDir, chosenName).writeBytes(bytes) }
            }.apply { isDaemon = true }.start()
        }
    }
}
