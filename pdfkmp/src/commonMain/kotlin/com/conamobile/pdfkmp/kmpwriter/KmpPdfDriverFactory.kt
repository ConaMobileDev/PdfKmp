package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.render.PdfDriver
import com.conamobile.pdfkmp.render.PdfDriverFactory
import com.conamobile.pdfkmp.style.PdfFont

/**
 * [PdfDriverFactory] for the pure-Kotlin PDF writer.
 *
 * Stateless and cheap to create. Pass an instance to
 * [com.conamobile.pdfkmp.pdf]'s `factory` parameter to render through the
 * from-scratch [KmpPdfDriver] instead of the platform default — the path the
 * upcoming wasmJs target will wire up as its default, and the way the JVM tests
 * validate the backend against PdfBox.
 */
internal class KmpPdfDriverFactory : PdfDriverFactory {

    override fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver = KmpPdfDriver(metadata, customFonts)
}
