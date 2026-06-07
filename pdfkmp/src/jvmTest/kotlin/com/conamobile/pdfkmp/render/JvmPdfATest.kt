package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdfparser.PDFStreamParser
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for the best-effort PDF/A-2b path. These assert that the
 * identifying metadata a PDF/A reader looks for first actually lands in the
 * bytes; they do NOT claim full veraPDF conformance (see
 * [com.conamobile.pdfkmp.metadata.PdfMetadata.pdfACompliance]).
 */
class JvmPdfATest {

    @Test
    fun pdfAEmbedsXmpOutputIntentAndMarkInfo() {
        val bytes = pdf {
            metadata {
                title = "Compliant Report"
                language = "en-US"
            }
            pdfA(true)
            page { text("hello") }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            val catalog = loaded.documentCatalog

            // XMP metadata packet present and declares PDF/A-2b.
            val metadata = assertNotNull(catalog.metadata, "catalog has no XMP metadata")
            val xmp = metadata.toByteArray().decodeToString()
            assertTrue("pdfaid:part" in xmp, "XMP missing pdfaid:part")
            assertTrue("<pdfaid:part>2</pdfaid:part>" in xmp, "XMP should declare part 2")
            assertTrue("<pdfaid:conformance>B</pdfaid:conformance>" in xmp, "XMP should declare conformance B")
            assertTrue("Compliant Report" in xmp, "XMP dc:title should match the document title")

            // Output intent present.
            val intents = catalog.outputIntents
            assertTrue(intents.isNotEmpty(), "document has no output intent")

            // MarkInfo /Marked true.
            val markInfo = assertNotNull(catalog.markInfo, "catalog has no MarkInfo")
            assertTrue(markInfo.isMarked, "MarkInfo should be marked")

            // Language written to /Lang.
            assertEquals("en-US", catalog.language)
        }
    }

    @Test
    fun imageAltTextWritesMarkedContentAlt() {
        val png = onePixelPng()
        val bytes = pdf {
            page {
                image(bytes = png, width = 40.dp, height = 40.dp, altText = "A red pixel")
            }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            val page = loaded.pages.first()
            // Walk the content tokens for a BDC operator tagged /Figure; the
            // preceding operand carries the /Alt either inline or via a
            // Properties resource referenced by name.
            val tokens = PDFStreamParser(page).parse()
            var sawFigureBdc = false
            // BDC operands precede the operator in the token list:
            //   /Figure /MCx BDC   (tag, properties-name, operator)
            for (i in tokens.indices) {
                val tok = tokens[i]
                if (tok is org.apache.pdfbox.contentstream.operator.Operator && tok.name == "BDC") {
                    val tag = tokens.getOrNull(i - 2)
                    if (tag is COSName && tag.name == "Figure") sawFigureBdc = true
                }
            }
            assertTrue(sawFigureBdc, "image draw should be wrapped in a /Figure BDC marked-content sequence")

            // The /Alt string lands in the page's Properties resources.
            val properties = page.resources.cosObject
                .getDictionaryObject(COSName.PROPERTIES) as? COSDictionary
            assertNotNull(properties, "page resources have no /Properties dictionary")
            val altFound = properties.keySet().any { key ->
                val dict = properties.getDictionaryObject(key) as? COSDictionary
                (dict?.getDictionaryObject(COSName.ALT) as? COSString)?.string == "A red pixel"
            }
            assertTrue(altFound, "no marked-content property carried the /Alt text")
        }
    }

    @Test
    fun nonPdfADocumentHasNoXmpOrOutputIntent() {
        val bytes = pdf {
            page { text("plain") }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            val catalog = loaded.documentCatalog
            assertTrue(catalog.metadata == null, "plain document should have no XMP metadata")
            assertTrue(catalog.outputIntents.isEmpty(), "plain document should have no output intent")
        }
    }

    /** Encodes a 1×1 red PNG so the alt-text test has a decodable image. */
    private fun onePixelPng(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFF0000)
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }
}
