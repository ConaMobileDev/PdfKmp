package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.render.JvmPdfDriverFactory
import com.conamobile.pdfkmp.render.PdfDriverFactory

/**
 * Returns the JVM / Desktop implementation of [PdfDriverFactory], backed by
 * Apache PdfBox.
 *
 * The factory is stateless and pure-Java, so the same code path runs
 * identically on macOS, Windows and Linux with no native libraries. Callers
 * almost never invoke this directly — [pdf] uses it as the default.
 */
public actual fun defaultPdfDriverFactory(): PdfDriverFactory = JvmPdfDriverFactory()
