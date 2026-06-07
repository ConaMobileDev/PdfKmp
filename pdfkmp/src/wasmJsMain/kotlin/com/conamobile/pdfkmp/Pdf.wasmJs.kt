package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.kmpwriter.KmpPdfDriverFactory
import com.conamobile.pdfkmp.render.PdfDriverFactory

/**
 * Web (Kotlin/Wasm) default backend.
 *
 * Browsers expose no PDF-writing API to Wasm, so documents render through
 * the pure-Kotlin `kmpwriter` backend: Standard-14 Helvetica text
 * (WinAnsi/Latin coverage), every vector feature (shapes, gradients, QR
 * codes, barcodes, charts, freeDraw), JPEG and 8-bit RGB/gray PNG images,
 * links, named destinations, the outline, and the info dictionary.
 *
 * Not yet supported on this backend (warned through
 * [com.conamobile.pdfkmp.PdfLog] and skipped): custom font embedding
 * (text falls back to Helvetica), characters outside WinAnsi,
 * palette/alpha PNGs, interactive AcroForm widgets (the static visuals
 * still draw), encryption, and attachments.
 */
public actual fun defaultPdfDriverFactory(): PdfDriverFactory = KmpPdfDriverFactory()
