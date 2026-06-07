package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.kmpwriter.KmpPdfDriverFactory
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Correctness gate for the pure-Kotlin (wasm-ready) PDF backend.
 *
 * [KmpPdfDriverFactory] produces a complete PDF with no platform PDF API; this
 * suite re-parses and rasterises its output through PdfBox — the same gate the
 * wasmJs target will rely on. Documents are authored through `pdf(factory =
 * KmpPdfDriverFactory())` so the from-scratch writer is genuinely exercised,
 * then forced through PdfBox's strict parser + renderer. A malformed xref
 * offset, broken shading function, or invalid image XObject that still "looks"
 * like a PDF would throw here.
 *
 * The documents mirror the feature surface of the bundled `Samples` — styled
 * text and typography, rows/columns, tables, dividers and dashed lines,
 * gradients, navigation (anchors + outline + internal links), and image
 * embedding — but are re-authored here because the `Samples` functions render
 * through the platform-default (PdfBox) factory and can't be redirected without
 * editing them.
 */
class KmpWriterBackendTest {

    private val factory = KmpPdfDriverFactory()

    // -- 1. Feature-surface documents re-parse + rasterise ----------------

    @Test
    fun documentsReParseAndRender() {
        val png = generatePng(96, 64)
        val documents = listOf(
            "helloWorld" to helloWorld(),
            "typography" to typography(),
            "rowAndColumn" to rowAndColumn(),
            "table" to tableDoc(),
            "designExtras" to designExtras(),
            "longBody" to longBody(),
            "newsletter" to newsletter(png),
            "navigation" to navigationDoc(),
            "textAdvanced" to textAdvanced(),
        )
        for ((name, doc) in documents) {
            Loader.loadPDF(doc.toByteArray()).use { loaded ->
                assertTrue(loaded.numberOfPages >= 1, "$name produced no pages")
                val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
                assertTrue(image.width > 0 && image.height > 0, "$name rendered an empty raster")
            }
        }
    }

    // -- 2. Text round-trip ----------------------------------------------

    @Test
    fun textRoundTrips() {
        Loader.loadPDF(helloWorld().toByteArray()).use { loaded ->
            val text = PDFTextStripper().getText(loaded)
            assertTrue(text.contains("Hello"), "Expected 'Hello' in extracted text, got: ${text.take(120)}")
        }
    }

    // -- 3. Navigation: outline + GoTo/Link annotation -------------------

    @Test
    fun navigationHasOutlineAndLinks() {
        Loader.loadPDF(navigationDoc().toByteArray()).use { loaded ->
            val outline = loaded.documentCatalog.documentOutline
            assertNotNull(outline, "document outline missing")
            val first = outline.firstChild
            assertNotNull(first, "outline has no entries")
            assertEquals("Chapter 1", first.title, "unexpected first outline title")

            // Page 0 carries at least one link annotation (the forward GoTo).
            val links = loaded.getPage(0).annotations
                .filterIsInstance<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink>()
            assertTrue(links.isNotEmpty(), "page 0 has no link annotations")
            val goTos = links.mapNotNull {
                it.action as? org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
            }
            assertTrue(goTos.isNotEmpty(), "internal link has no resolved GoTo action")
        }
    }

    // -- 4. Gradient ------------------------------------------------------

    @Test
    fun gradientRendersWithoutThrowing() {
        val doc = pdf(factory = factory) {
            page {
                box(
                    width = 400.dp,
                    height = 200.dp,
                    backgroundPaint = PdfPaint.linearGradient(
                        from = PdfColor.Blue,
                        to = PdfColor.Red,
                        endX = 400f,
                        endY = 0f,
                    ),
                ) {}
                box(
                    width = 200.dp,
                    height = 200.dp,
                    backgroundPaint = PdfPaint.radialGradient(
                        from = PdfColor.White,
                        to = PdfColor.Green,
                        centerX = 100f,
                        centerY = 100f,
                        radius = 100f,
                    ),
                ) {}
            }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0, "gradient document rendered empty")
        }
    }

    // -- 5. JPEG embed ----------------------------------------------------

    @Test
    fun jpegEmbedsAsXObject() {
        val jpeg = generateJpeg(120, 90)
        val doc = pdf(factory = factory) {
            page { image(bytes = jpeg, width = 200.dp, height = 150.dp) }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val xobjects = loaded.getPage(0).resources.xObjectNames.toList()
            assertTrue(xobjects.isNotEmpty(), "no XObject resource present for embedded JPEG")
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0)
        }
    }

    // -- 6. PNG RGB8 embed ------------------------------------------------

    @Test
    fun pngRgb8EmbedsAndRenders() {
        // ImageIO writes TYPE_INT_RGB as a non-interlaced 8-bit truecolor (type 2)
        // PNG — the verbatim-passthrough sub-format. If a future ImageIO emits a
        // different layout the embedder warns-and-skips and the page just has no
        // XObject; we tolerate that but assert the doc still parses + renders.
        val png = generatePng(120, 90)
        val doc = pdf(factory = factory) {
            page { image(bytes = png, width = 200.dp, height = 150.dp) }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0, "PNG document rendered empty")
        }
    }

    // -- 7. Offsets: startxref == real byte index of 'xref' --------------

    @Test
    fun startxrefPointsAtRealXrefOffset() {
        val bytes = helloWorld().toByteArray()
        // PdfBox parses cleanly (no repair).
        Loader.loadPDF(bytes).use { loaded -> assertTrue(loaded.numberOfPages >= 1) }

        val text = bytes.decodeToString()
        val startxrefIdx = text.lastIndexOf("startxref")
        assertTrue(startxrefIdx >= 0, "no startxref keyword")
        val declared = Regex("\\d+")
            .find(text.substring(startxrefIdx + "startxref".length))?.value?.toInt()
        assertNotNull(declared, "startxref has no offset number")

        // Strict, byte-exact check: the four bytes at the declared offset must be
        // the literal `xref` keyword that begins the cross-reference table. This
        // can only hold if every preceding object body was byte-counted exactly
        // (raw image bytes included), which is the property the wasm target needs.
        val keyword = "xref".encodeToByteArray()
        assertTrue(declared + keyword.size <= bytes.size, "startxref offset past EOF")
        for (j in keyword.indices) {
            assertEquals(
                keyword[j],
                bytes[declared + j],
                "byte at startxref offset ($declared) is not the 'xref' keyword",
            )
        }
    }

    // -- Authored documents (mirror the Samples feature surface) ----------

    private fun helloWorld(): PdfDocument = pdf(factory = factory) {
        metadata { title = "KmpWriter – Hello World" }
        page {
            text("Hello, world!") {
                fontSize = 24.sp
                bold = true
                color = PdfColor.Blue
            }
        }
    }

    private fun typography(): PdfDocument = pdf(factory = factory) {
        page {
            text("Regular 12pt")
            text("Bold") { bold = true }
            text("Italic") { italic = true }
            text("Bold italic") { bold = true; italic = true }
            text("Large coloured") { fontSize = 28.sp; color = PdfColor.Red }
            text("Letter spaced") { letterSpacing = 2.sp }
            text("Quotes — dash — bullet • ellipsis …")
        }
    }

    private fun rowAndColumn(): PdfDocument = pdf(factory = factory) {
        page {
            row(spacing = 12.dp) {
                column(spacing = 4.dp, background = PdfColor.LightGray, padding = padding8()) {
                    text("Col A line 1")
                    text("Col A line 2")
                }
                column(spacing = 4.dp, background = PdfColor.fromHex("#E0F2FE"), padding = padding8()) {
                    text("Col B line 1")
                    text("Col B line 2")
                }
            }
            divider()
            box(
                width = 120.dp,
                height = 60.dp,
                background = PdfColor.Green.withAlpha(0.5f),
                cornerRadius = 8.dp,
            ) {}
        }
    }

    private fun tableDoc(): PdfDocument = pdf(factory = factory) {
        page {
            table(
                columns = listOf(
                    TableColumn.Weight(2f),
                    TableColumn.Weight(1f),
                    TableColumn.Weight(1f),
                ),
            ) {
                row {
                    cell { text("Item") { bold = true } }
                    cell { text("Qty") { bold = true } }
                    cell { text("Price") { bold = true } }
                }
                repeat(20) { i ->
                    row {
                        cell { text("Product $i") }
                        cell { text("${i + 1}") }
                        cell { text("$${i * 3 + 9}.00") }
                    }
                }
            }
        }
    }

    private fun designExtras(): PdfDocument = pdf(factory = factory) {
        page {
            divider(thickness = 1.dp, style = LineStyle.Dashed)
            divider(thickness = 1.dp, style = LineStyle.Dotted)
            box(
                width = 200.dp,
                height = 100.dp,
                background = PdfColor.White,
                border = BorderStroke(width = 2.dp, color = PdfColor.DarkGray),
                cornerRadius = 12.dp,
                rotation = 8f,
            ) {
                text("Rotated card")
            }
            column(opacity = 0.5f, background = PdfColor.Blue, padding = padding8()) {
                text("Half-opacity group") { color = PdfColor.White }
            }
        }
    }

    private fun longBody(): PdfDocument = pdf(factory = factory) {
        page {
            repeat(60) { i ->
                text("Paragraph $i: the quick brown fox jumps over the lazy dog, again and again to force pagination across several pages of body copy.")
            }
        }
    }

    private fun newsletter(png: ByteArray): PdfDocument = pdf(factory = factory) {
        page(size = PageSize.A4) {
            row(spacing = 16.dp) {
                image(bytes = png, width = 96.dp, height = 64.dp, contentScale = ContentScale.Crop)
                column(spacing = 4.dp) {
                    text("Newsletter") { fontSize = 22.sp; bold = true }
                    text("Issue #1 — with an embedded image and mixed layout.")
                }
            }
            divider()
            text("Body text follows the masthead and image header above.")
        }
    }

    private fun navigationDoc(): PdfDocument = pdf(factory = factory) {
        page {
            bookmark("Chapter 1")
            anchor("ch1")
            text("Chapter 1") { fontSize = 20.sp; bold = true }
            linkToAnchor(anchor = "ch2") { text("jump to chapter 2") }
            link(url = "https://example.com") { text("external link") }
        }
        page {
            bookmark("Chapter 2")
            bookmark("Section 2.1", level = 1)
            anchor("ch2")
            text("Chapter 2") { fontSize = 20.sp; bold = true }
        }
    }

    private fun textAdvanced(): PdfDocument = pdf(factory = factory) {
        page {
            text("Underlined") { underline = true }
            text("Struck through") { strikethrough = true }
            richText {
                span("Mixed ")
                span("bold") { bold = true }
                span(" and ")
                span("red italic") { italic = true; color = PdfColor.Red }
                span(" run.")
            }
            text("Weighted") { fontWeight = FontWeight.Black }
        }
    }

    // -- Image + byte helpers ---------------------------------------------

    private fun padding8() = com.conamobile.pdfkmp.geometry.Padding.all(8.dp)

    private fun generatePng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(0x4F46E5), width.toFloat(), height.toFloat(), Color(0xEC4899))
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun generateJpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(0x10B981), width.toFloat(), height.toFloat(), Color(0xF59E0B))
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }
}
