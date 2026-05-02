package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.dsl.DocumentScope
import com.conamobile.pdfkmp.dsl.collectCustomFonts
import com.conamobile.pdfkmp.node.resolveDeferred
import com.conamobile.pdfkmp.render.DocumentRenderer
import com.conamobile.pdfkmp.render.PdfDriverFactory
import com.conamobile.pdfkmp.render.RecordingPdfDriver
import com.conamobile.pdfkmp.style.PdfFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val recorder = RecordingPdfDriver(driver)
    val bytes = DocumentRenderer.render(spec, recorder)
    return PdfDocument(
        bytes = bytes,
        textRuns = recorder.textRuns,
        hyperlinks = recorder.hyperlinks,
    )
}

/**
 * Suspend variant of [pdf] that runs a preflight pass before rendering,
 * resolving every [com.conamobile.pdfkmp.node.LazyNode] in the document
 * tree. Use this when the DSL contains nodes whose payload is fetched
 * through a `suspend` API — the typed `Res.drawable.X` overloads in
 * `:pdfkmp-compose-resources` are the canonical example.
 *
 * The DSL block stays synchronous: every container scope (`page`, `column`,
 * `row`, …) is the same shape as in [pdf]. Only the resource-loading
 * extension functions enqueue a `LazyNode` whose suspend resolver is
 * awaited here, before the layout engine ever sees the tree.
 *
 * The whole pipeline — DSL evaluation, preflight, layout, rendering, and
 * encoding — runs on [Dispatchers.Default], so calling this from the main
 * thread does not block UI. Callers that need a different dispatcher (a
 * single-threaded test scope, an existing background pool) can wrap the
 * call in their own `withContext(...)` — the inner `withContext` switch
 * to `Default` is a no-op when the outer context is already `Default`.
 *
 * Calling the synchronous [pdf] with deferred nodes throws at render
 * time — the error message points back to this entry point.
 *
 * Example:
 * ```
 * suspend fun report(): PdfDocument = pdfAsync {
 *     page {
 *         drawable(Res.drawable.logo, width = 64.dp)         // sync DSL,
 *         drawable(Res.drawable.cover_photo, width = 480.dp) // async load
 *     }
 * }
 * ```
 *
 * @param factory backend used to encode the document; same default and
 *   override semantics as [pdf].
 * @param block DSL configuration applied to a fresh [DocumentScope].
 */
public suspend fun pdfAsync(
    factory: PdfDriverFactory = defaultPdfDriverFactory(),
    block: DocumentScope.() -> Unit,
): PdfDocument = withContext(Dispatchers.Default) {
    val scope = DocumentScope().apply(block)
    val rawSpec = scope.build()
    val resolvedSpec = rawSpec.resolveDeferred()
    // Re-collect custom fonts after preflight: any TextNode produced by a
    // LazyNode resolver wasn't visible during the original walk inside
    // DocumentScope.build(), so its font wouldn't be registered with the
    // platform driver and would silently fall back to Inter at draw time.
    val refreshedFonts = linkedSetOf<PdfFont.Custom>().apply {
        addAll(resolvedSpec.customFonts)
        resolvedSpec.pages.forEach { page -> collectCustomFonts(page.content, this) }
    }
    val finalSpec = resolvedSpec.copy(customFonts = refreshedFonts.toList())
    val driver = factory.create(finalSpec.metadata, finalSpec.customFonts)
    val recorder = RecordingPdfDriver(driver)
    val bytes = DocumentRenderer.render(finalSpec, recorder)
    PdfDocument(
        bytes = bytes,
        textRuns = recorder.textRuns,
        hyperlinks = recorder.hyperlinks,
    )
}

/**
 * Library version. The string is generated at build time from the
 * `VERSION_NAME` Gradle property in the root `gradle.properties`, so a
 * release bump cannot drift from what consumers see at runtime.
 */
public object PdfKmp {
    public val VERSION: String get() = PDFKMP_VERSION_NAME
}
