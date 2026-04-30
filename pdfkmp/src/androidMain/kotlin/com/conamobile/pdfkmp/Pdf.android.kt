package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.render.AndroidPdfDriverFactory
import com.conamobile.pdfkmp.render.PdfDriverFactory

/**
 * Returns the Android implementation of [PdfDriverFactory], backed by
 * [android.graphics.pdf.PdfDocument].
 *
 * The factory needs the application [android.content.Context] to create a
 * cache directory for temporary font files; that context is captured at
 * library start through `AndroidX App Startup` and reused across calls.
 */
public actual fun defaultPdfDriverFactory(): PdfDriverFactory = AndroidPdfDriverFactory()
