package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Default iOS implementation of [PdfDriverFactory].
 *
 * Each call returns a fresh [IosPdfDriver] backed by a new in-memory
 * `UIGraphicsPDF` context. The factory itself is stateless and safe to
 * cache, but the drivers it returns are single-use.
 */
internal class IosPdfDriverFactory : PdfDriverFactory {

    override fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver = IosPdfDriver(metadata, customFonts)
}
