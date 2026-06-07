package com.conamobile.pdfkmp.tools

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.zugferd.FacturXInvoice
import com.conamobile.pdfkmp.zugferd.toXml
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.Overlay
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.multipdf.PageExtractor
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.pdmodel.PDDocumentCatalog
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar

/**
 * JVM/Desktop-only post-processing utilities that operate on **already-encoded
 * PDF bytes** (from PdfKmp or any other producer), backed by Apache PdfBox.
 *
 * These live in `jvmMain` and have **no Android, iOS, or Web counterpart**:
 * Android's `android.graphics.pdf` and iOS's Core Graphics only expose
 * PDF-*writing* APIs, and the Web target has no PDF engine at all. Merging,
 * splitting, stamping, and overlaying existing documents all require a full
 * PDF *parser/manipulator*, which on the JVM is PdfBox (a pure-Java engine, so
 * no native libraries are pulled in). If you need these operations on
 * mobile/web, run them on a JVM/Desktop backend (or a server) instead.
 *
 * Every method reads the input into an in-memory [PDDocument], operates on it,
 * and serialises the result to a fresh `ByteArray`; inputs are never mutated.
 */
public object PdfTools {

    /**
     * Merges [documents] into a single PDF, concatenating their pages in
     * argument order, via PdfBox's [PDFMergerUtility].
     *
     * Passing no documents returns an empty (zero-page) PDF; passing one
     * returns a re-saved copy of it.
     *
     * @param documents the source PDFs, each as encoded bytes.
     * @return the merged PDF bytes.
     */
    public fun merge(vararg documents: ByteArray): ByteArray {
        val merger = PDFMergerUtility()
        documents.forEach { merger.addSource(RandomAccessReadBuffer(it)) }
        return ByteArrayOutputStream().use { out ->
            merger.destinationStream = out
            // null stream-cache → keep everything in memory (no temp files).
            merger.mergeDocuments(null)
            out.toByteArray()
        }
    }

    /**
     * Splits [pdf] into one single-page document per page, preserving page
     * order, via PdfBox's [Splitter].
     *
     * @param pdf the source PDF bytes.
     * @return a list of single-page PDFs; empty when the input has no pages.
     */
    public fun split(pdf: ByteArray): List<ByteArray> =
        Loader.loadPDF(pdf).use { document ->
            Splitter().split(document).map { part ->
                part.use { single ->
                    ByteArrayOutputStream().use { out ->
                        single.save(out)
                        out.toByteArray()
                    }
                }
            }
        }

    /**
     * Extracts the pages in [range] (1-based, inclusive on both ends) from
     * [pdf] into a new document, via PdfBox's [PageExtractor].
     *
     * @param pdf the source PDF bytes.
     * @param range 1-based inclusive page range, e.g. `2..4` for pages 2, 3, 4.
     * @return the extracted pages as a new PDF.
     * @throws IllegalArgumentException when the range is empty or falls outside
     *   the document's `1..pageCount` bounds.
     */
    public fun extractPages(pdf: ByteArray, range: IntRange): ByteArray =
        Loader.loadPDF(pdf).use { document ->
            val pageCount = document.numberOfPages
            require(!range.isEmpty()) { "Page range must be non-empty (got $range)" }
            require(range.first >= 1) { "Page range start must be >= 1 (got ${range.first})" }
            require(range.last <= pageCount) {
                "Page range end ${range.last} exceeds document page count $pageCount"
            }
            PageExtractor(document, range.first, range.last).extract().use { extracted ->
                ByteArrayOutputStream().use { out ->
                    extracted.save(out)
                    out.toByteArray()
                }
            }
        }

    /**
     * Stamps diagonal watermark [text] across **every page** of [pdf],
     * centered, semi-transparent, and rotated.
     *
     * The text is drawn in a Standard-14 Helvetica-Bold font (so nothing extra
     * is embedded) into a content stream appended to each page, with an
     * [PDExtendedGraphicsState] applying the constant alpha so the watermark
     * sits over — but does not obscure — the existing content.
     *
     * @param pdf the source PDF bytes.
     * @param text the watermark text.
     * @param fontSizePt watermark font size in PDF points; defaults to `96`.
     * @param color watermark fill color; defaults to a light gray.
     * @param opacityFraction stroke/fill alpha in `0f..1f`; defaults to `0.3`.
     * @param rotationDegrees counter-clockwise rotation of the text; defaults
     *   to `45` (bottom-left to top-right diagonal).
     * @return the watermarked PDF bytes.
     */
    public fun addWatermarkText(
        pdf: ByteArray,
        text: String,
        fontSizePt: Float = 96f,
        color: PdfColor = PdfColor.LightGray,
        opacityFraction: Float = 0.3f,
        rotationDegrees: Float = 45f,
    ): ByteArray = Loader.loadPDF(pdf).use { document ->
        val font: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        // One shared ExtGState carrying the constant alpha; reused per page.
        val graphicsState = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = opacityFraction
            strokingAlphaConstant = opacityFraction
        }
        val radians = Math.toRadians(rotationDegrees.toDouble())

        for (page in document.pages) {
            val box = page.mediaBox
            // Center of the visible page; the rotation pivots about here.
            val centerX = box.lowerLeftX + box.width / 2f
            val centerY = box.lowerLeftY + box.height / 2f
            // Width of the text at the chosen size, so we can shift left by
            // half of it and land the visual midpoint on the page center.
            val textWidth = font.getStringWidth(text) / 1000f * fontSizePt

            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                // resetContext = true so our graphics state can't leak from /
                // into the page's own content.
                true,
                true,
            ).use { stream ->
                stream.saveGraphicsState()
                stream.setGraphicsStateParameters(graphicsState)
                stream.setNonStrokingColor(color.red, color.green, color.blue)
                stream.beginText()
                stream.setFont(font, fontSizePt)
                // Rotate about the page center, then offset so the text's own
                // midpoint sits at the origin of the rotated frame.
                val transform = Matrix.getRotateInstance(radians, centerX, centerY)
                transform.concatenate(Matrix.getTranslateInstance(-textWidth / 2f, -fontSizePt / 4f))
                stream.setTextMatrix(transform)
                stream.showText(text)
                stream.endText()
                stream.restoreGraphicsState()
            }
        }
        ByteArrayOutputStream().use { out ->
            document.save(out)
            out.toByteArray()
        }
    }

    /**
     * Overlays [overlayPdf] onto [pdf] (the overlay drawn **in front of** each
     * page's existing content) via PdfBox's [Overlay].
     *
     * The overlay's first page is reused for every input page (the common
     * "stamp a letterhead / background frame onto each page" case), so a
     * single-page overlay applies uniformly to a multi-page base document.
     *
     * @param pdf the base PDF bytes.
     * @param overlayPdf the PDF whose first page is stamped over each base page.
     * @return the overlaid PDF bytes.
     */
    public fun overlay(pdf: ByteArray, overlayPdf: ByteArray): ByteArray =
        Loader.loadPDF(pdf).use { base ->
            Loader.loadPDF(overlayPdf).use { over ->
                Overlay().use { overlay ->
                    // These are write-only setters (no matching getter), so use
                    // the explicit method form rather than property assignment.
                    overlay.setInputPDF(base)
                    overlay.setDefaultOverlayPDF(over)
                    overlay.setOverlayPosition(Overlay.Position.FOREGROUND)
                    // Empty map → no per-page overrides; the default overlay
                    // (over's first page) is applied to every base page.
                    overlay.overlay(emptyMap()).use { result ->
                        ByteArrayOutputStream().use { out ->
                            result.save(out)
                            out.toByteArray()
                        }
                    }
                }
            }
        }

    /**
     * Embeds [invoice]'s `factur-x.xml` into [pdf] as a ZUGFeRD / Factur-X
     * attachment and returns the new bytes.
     *
     * The XML is built via [FacturXInvoice.toXml] (MINIMUM profile) and embedded
     * under the catalog's embedded-files name tree with the spec-mandated file
     * name `factur-x.xml`, MIME type `text/xml`, and an `/AFRelationship` of
     * `/Data` (per the Factur-X specification). This mirrors the core driver's
     * attachment path but operates standalone over existing bytes, so you can
     * Factur-X-enable a PDF produced by any tool.
     *
     * **Scope:** this only attaches the structured XML — it does **not** add or
     * verify the PDF/A conformance Factur-X also requires. For a fully
     * conformant document, produce the base PDF with PDF/A enabled and validate
     * the result. See [FacturXInvoice] for the data-model caveats.
     *
     * @param pdf the human-readable invoice PDF bytes.
     * @param invoice the structured invoice to attach.
     * @return the PDF bytes with `factur-x.xml` embedded.
     */
    public fun attachFacturX(pdf: ByteArray, invoice: FacturXInvoice): ByteArray =
        Loader.loadPDF(pdf).use { document ->
            val xmlBytes = invoice.toXml().encodeToByteArray()
            val now = Calendar.getInstance()
            val embedded = PDEmbeddedFile(document, ByteArrayInputStream(xmlBytes)).apply {
                subtype = FACTUR_X_MIME
                size = xmlBytes.size
                creationDate = now
                modDate = now
            }
            val spec = PDComplexFileSpecification().apply {
                file = FACTUR_X_FILE_NAME
                setFileUnicode(FACTUR_X_FILE_NAME)
                setEmbeddedFile(embedded)
                setEmbeddedFileUnicode(embedded)
                fileDescription = "Factur-X invoice"
                // /AFRelationship /Data — the relationship Factur-X mandates for
                // the structured invoice payload. PDComplexFileSpecification has
                // no typed setter, so write it onto the COS dictionary directly.
                cosObject.setName(COSName.getPDFName("AFRelationship"), "Data")
            }

            val catalog: PDDocumentCatalog = document.documentCatalog
            val embeddedFiles = PDEmbeddedFilesNameTreeNode().apply {
                names = mapOf(FACTUR_X_FILE_NAME to spec)
            }
            val nameDictionary = catalog.names ?: PDDocumentNameDictionary(catalog)
            nameDictionary.embeddedFiles = embeddedFiles
            catalog.names = nameDictionary
            // /AF on the catalog lets PDF/A-3 / Factur-X readers discover the
            // associated file without walking the whole name tree.
            catalog.cosObject.setItem(
                COSName.getPDFName("AF"),
                COSArray().apply { add(spec.cosObject) },
            )

            ByteArrayOutputStream().use { out ->
                document.save(out)
                out.toByteArray()
            }
        }

    /**
     * Runs a quick, dependency-free self-check of [pdf] for the entries a
     * PDF/A reader expects, returning human-readable findings (one per issue).
     * An empty list means none of the checked problems were found.
     *
     * Checks performed:
     * - missing XMP metadata packet on the catalog,
     * - missing output intent (PDF/A requires a defined output colour space),
     * - any non-embedded font found across the pages' resources
     *   ([PDFont.isEmbedded]),
     * - the document being encrypted (PDF/A forbids encryption).
     *
     * **This is a quick heuristic, not a validator.** It does not parse the XMP
     * for the correct `pdfaid` part/conformance, check colour spaces, verify
     * the structure tree, or anything else veraPDF does — a clean result here
     * does **not** imply PDF/A conformance. Use [veraPDF](https://verapdf.org)
     * for an authoritative verdict.
     *
     * @param pdf the PDF bytes to inspect.
     * @return a list of findings; empty when no checked issue was detected.
     */
    public fun validatePdfABasics(pdf: ByteArray): List<String> {
        val findings = mutableListOf<String>()
        Loader.loadPDF(pdf).use { document ->
            val catalog = document.documentCatalog
            if (catalog.metadata == null) {
                findings += "Missing XMP metadata packet (catalog has no /Metadata)."
            }
            if (catalog.outputIntents.isEmpty()) {
                findings += "Missing output intent (PDF/A requires a defined output colour space)."
            }
            if (document.isEncrypted) {
                findings += "Document is encrypted (PDF/A forbids encryption)."
            }
            val nonEmbedded = linkedSetOf<String>()
            for (page in document.pages) {
                val resources = page.resources ?: continue
                for (fontName in resources.fontNames) {
                    val font = runCatching { resources.getFont(fontName) }.getOrNull() ?: continue
                    if (!font.isEmbedded) {
                        nonEmbedded += font.name ?: fontName.name
                    }
                }
            }
            if (nonEmbedded.isNotEmpty()) {
                findings += "Non-embedded font(s) found: ${nonEmbedded.joinToString(", ")}."
            }
        }
        return findings
    }

    /** Spec-mandated embedded-file name for the structured Factur-X invoice. */
    private const val FACTUR_X_FILE_NAME = "factur-x.xml"

    /** MIME type the Factur-X spec requires on the embedded XML stream. */
    private const val FACTUR_X_MIME = "text/xml"
}
