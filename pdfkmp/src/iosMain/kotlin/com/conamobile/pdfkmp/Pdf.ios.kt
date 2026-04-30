package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.render.IosPdfDriverFactory
import com.conamobile.pdfkmp.render.PdfDriverFactory

/**
 * Returns the iOS implementation of [PdfDriverFactory], backed by
 * `UIGraphicsBeginPDFContextToData` and Core Graphics.
 *
 * The factory is stateless; the cost of resolving it is negligible, so
 * callers do not need to cache the returned instance.
 */
public actual fun defaultPdfDriverFactory(): PdfDriverFactory = IosPdfDriverFactory()
