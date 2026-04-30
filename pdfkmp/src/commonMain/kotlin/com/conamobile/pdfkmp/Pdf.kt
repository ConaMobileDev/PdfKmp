package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.dsl.DocumentScope
import com.conamobile.pdfkmp.render.DocumentRenderer
import com.conamobile.pdfkmp.render.PdfDriverFactory

/**
 * Returns the platform's default [PdfDriverFactory].
 *
 * - Android: a factory backed by [`android.graphics.pdf.PdfDocument`][android.graphics.pdf.PdfDocument]
 *   and [`android.graphics.Canvas`][android.graphics.Canvas].
 * - iOS: a factory backed by `UIGraphicsPDFRenderer` and Core Graphics.
 *
 * Library users almost never call this directly — [pdf] uses it as the
 * default. Tests and advanced consumers may pass a different factory through
 * [pdf]'s `factory` parameter to swap in a stub or an alternative backend.
 */
public expect fun defaultPdfDriverFactory(): PdfDriverFactory

/**
 * Top-level entry point of the PdfKmp DSL. Builds a PDF document and returns
 * it ready for [PdfDocument.save] or [PdfDocument.toByteArray].
 *
 * Example:
 * ```
 * val document = pdf {
 *     metadata { title = "Invoice" }
 *     page {
 *         padding = Padding.all(40.dp)
 *         text("Hello, world!") {
 *             fontSize = 18.sp
 *             bold = true
 *         }
 *     }
 * }
 * document.save("/tmp/hello.pdf")
 * ```
 *
 * @param factory backend used to encode the document; defaults to the
 *   platform's [defaultPdfDriverFactory]. Override for tests or to use an
 *   alternative PDF backend.
 * @param block DSL configuration applied to a fresh [DocumentScope].
 */
public fun pdf(
    factory: PdfDriverFactory = defaultPdfDriverFactory(),
    block: DocumentScope.() -> Unit,
): PdfDocument {
    val scope = DocumentScope().apply(block)
    val spec = scope.build()
    val driver = factory.create(spec.metadata, spec.customFonts)
    val bytes = DocumentRenderer.render(spec, driver)
    return PdfDocument(bytes)
}

/** Library version. Bumped on each public release. */
public object PdfKmp {
    public const val VERSION: String = "0.1.0-SNAPSHOT"
}
