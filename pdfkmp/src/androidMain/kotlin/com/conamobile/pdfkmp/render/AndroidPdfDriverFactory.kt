package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.platform.androidApplicationContext
import com.conamobile.pdfkmp.style.PdfFont
import java.io.File

/**
 * Default Android implementation of [PdfDriverFactory].
 *
 * Resolves the application's cache directory through the
 * [androidx.startup.Initializer] hook in [AndroidContextInitializer] so the
 * factory can write temporary font files without the caller having to plumb
 * a [android.content.Context] through the public API.
 *
 * Library users do not instantiate this directly; they reach it through
 * [com.conamobile.pdfkmp.defaultPdfDriverFactory].
 */
internal class AndroidPdfDriverFactory : PdfDriverFactory {

    override fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver {
        val context = androidApplicationContext()
        val cacheDir = File(context.cacheDir, "pdfkmp-fonts")
        return AndroidPdfDriver(metadata, customFonts, cacheDir)
    }
}
