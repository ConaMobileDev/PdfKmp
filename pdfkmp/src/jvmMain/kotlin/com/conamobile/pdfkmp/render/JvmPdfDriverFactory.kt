package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Default JVM / Desktop implementation of [PdfDriverFactory], backed by
 * Apache PdfBox.
 *
 * The factory is stateless and cheap to create; callers reach it through
 * [com.conamobile.pdfkmp.defaultPdfDriverFactory] rather than instantiating
 * it directly.
 */
internal class JvmPdfDriverFactory : PdfDriverFactory {

    override fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver = JvmPdfDriver(metadata, customFonts)
}
