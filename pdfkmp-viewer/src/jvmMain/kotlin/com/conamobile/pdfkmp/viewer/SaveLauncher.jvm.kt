package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

/**
 * Desktop "Save" — writes the PDF to the user's `~/Downloads` folder, the
 * closest desktop analogue of Android's `Downloads` MediaStore entry. The
 * folder is created if missing and an existing file of the same name is
 * overwritten. Failures (read-only home, disk full) are swallowed so a save
 * tap never crashes the viewer.
 */
@Composable
public actual fun rememberPdfSaveAction(): PdfSaveAction = remember {
    PdfSaveAction { bytes, fileName ->
        runCatching {
            val downloads = File(System.getProperty("user.home") ?: ".", "Downloads")
            if (!downloads.exists()) downloads.mkdirs()
            File(downloads, fileName).writeBytes(bytes)
        }
    }
}
