package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.font.BundledFonts
import com.conamobile.pdfkmp.kmpwriter.KmpPdfDriverFactory
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.unit.sp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end correctness gate for the pure-Kotlin backend's TrueType embedding +
 * Flate-compression features, validated through PdfBox (the same strict parser
 * the wasm target relies on).
 *
 * Covers the two embedding paths and the ToUnicode contract:
 *
 * - **Bundled-Inter Unicode fallback** — Cyrillic text contains code points
 *   WinAnsi can't represent, so the backend embeds a subset of Inter. PdfBox's
 *   `PDFTextStripper` must recover the original "Привет", which only works if the
 *   embedded font carries a correct ToUnicode CMap keyed by the glyph ids the
 *   content stream wrote.
 * - **Custom font** — a [PdfFont.Custom] built from the bundled Inter bytes is
 *   embedded and rasterises.
 * - **Compression switch** — the same document parses + renders with content
 *   streams Flate-compressed and uncompressed.
 */
class KmpWriterFontEmbeddingTest {

    private val factory = KmpPdfDriverFactory()

    @Test
    fun cyrillicTextRoundTripsThroughToUnicode() {
        val doc = pdf(factory = factory) {
            page { text("Привет, мир!") { fontSize = 20.sp } }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val text = PDFTextStripper().getText(loaded)
            assertTrue(
                text.contains("Привет"),
                "ToUnicode failed: expected 'Привет' in extracted text, got: ${text.take(80)}",
            )
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0, "Cyrillic document rendered empty")
        }
    }

    @Test
    fun mixedLatinAndCyrillicRenders() {
        // A Latin run takes the Helvetica path; the Cyrillic run takes the
        // embedded-Inter path. Both must coexist in one page.
        val doc = pdf(factory = factory) {
            page {
                text("Hello (Latin, Helvetica)")
                text("Привет (Cyrillic, embedded Inter)")
                text("Aя mixed in one run")
            }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val text = PDFTextStripper().getText(loaded)
            assertTrue(text.contains("Hello"), "Latin text missing")
            assertTrue(text.contains("Привет"), "Cyrillic text missing")
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0, "mixed-script document rendered empty")
        }
    }

    @Test
    fun customFontEmbedsAndRenders() {
        val customInter = PdfFont.Custom(name = "MyInter", bytes = BundledFonts.interRegular)
        val doc = pdf(factory = factory) {
            page {
                text("Custom font text 123") {
                    font = customInter
                    fontSize = 18.sp
                    color = PdfColor.Blue
                }
            }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            // The page must carry a Type0 font resource (the embedded CIDFontType2).
            val fonts = loaded.getPage(0).resources
            val fontNames = fonts.fontNames.toList()
            assertTrue(fontNames.isNotEmpty(), "no font resource on the custom-font page")
            val hasType0 = fontNames.any { name ->
                fonts.getFont(name)?.subType == "Type0"
            }
            assertTrue(hasType0, "custom font was not embedded as a Type0/CIDFontType2")

            val text = PDFTextStripper().getText(loaded)
            assertTrue(text.contains("Custom font text"), "custom-font text not extractable: ${text.take(80)}")
            val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
            assertTrue(image.width > 0, "custom-font document rendered empty")
        }
    }

    @Test
    fun customFontWithCyrillicExtractsViaToUnicode() {
        val customInter = PdfFont.Custom(name = "CyrInter", bytes = BundledFonts.interRegular)
        val doc = pdf(factory = factory) {
            page { text("Текст шрифтом") { font = customInter; fontSize = 16.sp } }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val text = PDFTextStripper().getText(loaded)
            assertTrue(text.contains("Текст"), "custom-font Cyrillic not extractable: ${text.take(80)}")
        }
    }

    @Test
    fun compressedAndUncompressedBothParse() {
        fun build(compress: Boolean): PdfDocument = pdf(factory = KmpPdfDriverFactory(compressStreams = compress)) {
            page {
                text("Hello, world!") { fontSize = 24.sp; bold = true }
                text("Привет, мир!")
                repeat(20) { i -> text("Body line $i with enough text to make the stream worth compressing.") }
            }
        }

        val compressed = build(true).toByteArray()
        val uncompressed = build(false).toByteArray()

        // Both must parse + render through PdfBox.
        for ((label, bytes) in listOf("compressed" to compressed, "uncompressed" to uncompressed)) {
            Loader.loadPDF(bytes).use { loaded ->
                val text = PDFTextStripper().getText(loaded)
                assertTrue(text.contains("Hello"), "$label: lost 'Hello'")
                assertTrue(text.contains("Привет"), "$label: lost 'Привет'")
                val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
                assertTrue(image.width > 0, "$label: rendered empty")
            }
        }

        // The compressed document should be smaller for this repetitive body.
        assertTrue(
            compressed.size < uncompressed.size,
            "FlateDecode did not shrink output: compressed=${compressed.size}, uncompressed=${uncompressed.size}",
        )
    }
}
