package com.conamobile.pdfkmp.tools

import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.zugferd.FacturXInvoice
import com.conamobile.pdfkmp.zugferd.toXml
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for [PdfTools] (PdfBox-backed post-processing) and the
 * common ZUGFeRD / Factur-X helpers.
 *
 * Documents under test are produced through the public `pdf { }` DSL — which
 * resolves the JVM PdfBox driver on this source set — and the post-processed
 * output is re-parsed with PdfBox's [Loader] to assert what actually landed in
 * the bytes.
 */
class PdfToolsTest {

    // --- merge -------------------------------------------------------------

    @Test
    fun mergeSumsPagesAndKeepsTextFromBoth() {
        val first = pdf {
            page { text("ALPHA_DOCUMENT") }
            page { text("ALPHA_SECOND_PAGE") }
        }.toByteArray()
        val second = pdf {
            page { text("BETA_DOCUMENT") }
        }.toByteArray()

        val merged = PdfTools.merge(first, second)

        Loader.loadPDF(merged).use { document ->
            assertEquals(3, document.numberOfPages, "merged page count should be the sum")
            val text = PDFTextStripper().getText(document)
            assertTrue("ALPHA_DOCUMENT" in text, "text from first document missing")
            assertTrue("ALPHA_SECOND_PAGE" in text, "text from first document's 2nd page missing")
            assertTrue("BETA_DOCUMENT" in text, "text from second document missing")
        }
    }

    // --- split -------------------------------------------------------------

    @Test
    fun splitYieldsOneSinglePageDocumentPerPage() {
        val source = pdf {
            page { text("PAGE_ONE") }
            page { text("PAGE_TWO") }
            page { text("PAGE_THREE") }
        }.toByteArray()

        val parts = PdfTools.split(source)

        assertEquals(3, parts.size, "split should produce one PDF per page")
        val recovered = parts.map { part ->
            Loader.loadPDF(part).use { document ->
                assertEquals(1, document.numberOfPages, "each split part must be single-page")
                PDFTextStripper().getText(document).trim()
            }
        }
        assertTrue(recovered.any { "PAGE_ONE" in it })
        assertTrue(recovered.any { "PAGE_TWO" in it })
        assertTrue(recovered.any { "PAGE_THREE" in it })
    }

    // --- extractPages ------------------------------------------------------

    @Test
    fun extractPagesReturnsRequestedInclusiveRange() {
        val source = pdf {
            page { text("EXTRACT_P1") }
            page { text("EXTRACT_P2") }
            page { text("EXTRACT_P3") }
            page { text("EXTRACT_P4") }
        }.toByteArray()

        val extracted = PdfTools.extractPages(source, 2..3)

        Loader.loadPDF(extracted).use { document ->
            assertEquals(2, document.numberOfPages, "should extract exactly 2 pages")
            val text = PDFTextStripper().getText(document)
            assertTrue("EXTRACT_P2" in text, "page 2 content missing")
            assertTrue("EXTRACT_P3" in text, "page 3 content missing")
            assertTrue("EXTRACT_P1" !in text, "page 1 should not be present")
            assertTrue("EXTRACT_P4" !in text, "page 4 should not be present")
        }
    }

    @Test
    fun extractPagesRejectsOutOfBoundsAndEmptyRanges() {
        val source = pdf {
            page { text("only") }
            page { text("two") }
        }.toByteArray()

        assertFailsWith<IllegalArgumentException> { PdfTools.extractPages(source, 0..1) }
        assertFailsWith<IllegalArgumentException> { PdfTools.extractPages(source, 1..3) }
        assertFailsWith<IllegalArgumentException> { PdfTools.extractPages(source, 3..2) }
    }

    // --- watermark ---------------------------------------------------------

    @Test
    fun watermarkGrowsContentAndRenders() {
        val source = pdf {
            page { text("BASE_BODY_TEXT") }
            page { text("SECOND_PAGE_BODY") }
        }.toByteArray()

        val baselineSize = contentStreamSize(source)
        val stamped = PdfTools.addWatermarkText(source, "CONFIDENTIAL")

        // The appended watermark content stream must make every page larger,
        // and the result must still rasterise (no malformed content/state).
        val stampedSize = contentStreamSize(stamped)
        assertTrue(
            stampedSize > baselineSize,
            "watermark should grow page content streams ($stampedSize !> $baselineSize)",
        )

        Loader.loadPDF(stamped).use { document ->
            assertEquals(2, document.numberOfPages)
            val renderer = PDFRenderer(document)
            // Rotated text extraction is unreliable, so prove correctness by
            // rasterising every page instead — throws on any bad content/state.
            for (i in 0 until document.numberOfPages) {
                val image = renderer.renderImageWithDPI(i, 72f)
                assertTrue(image.width > 0 && image.height > 0, "page $i rendered empty")
            }
        }
    }

    // --- overlay -----------------------------------------------------------

    @Test
    fun overlayStampsOverlayContentOntoEveryBasePage() {
        val base = pdf {
            page { text("BASE_PAGE_ONE") }
            page { text("BASE_PAGE_TWO") }
        }.toByteArray()
        val overlay = pdf {
            page { text("OVERLAY_MARK") }
        }.toByteArray()

        val result = PdfTools.overlay(base, overlay)

        Loader.loadPDF(result).use { document ->
            assertEquals(2, document.numberOfPages, "overlay must preserve base page count")
            val text = PDFTextStripper().getText(document)
            assertTrue("BASE_PAGE_ONE" in text, "base content lost after overlay")
            assertTrue("BASE_PAGE_TWO" in text, "base content lost after overlay")
            // The single overlay page is repeated onto every base page, so its
            // mark appears at least twice.
            val overlayHits = Regex("OVERLAY_MARK").findAll(text).count()
            assertTrue(overlayHits >= 2, "overlay mark should appear on every page (found $overlayHits)")
            // And it still renders.
            PDFRenderer(document).renderImageWithDPI(0, 72f)
        }
    }

    // --- Factur-X XML ------------------------------------------------------

    @Test
    fun facturXXmlContainsMandatoryElementsAndEscapesAmpersand() {
        val invoice = FacturXInvoice(
            invoiceNumber = "INV-2026-001 & Co",
            issueDateYyyymmdd = "20260607",
            sellerName = "Müller & Sons GmbH",
            buyerName = "Acme <Buyer>",
            currencyCode = "EUR",
            taxBasisTotal = "100.00",
            taxTotal = "19.00",
            grandTotal = "119.00",
            duePayable = "119.00",
            sellerVatId = "DE123456789",
            sellerCountryCode = "DE",
        )

        val xml = invoice.toXml()

        // Profile + structural elements.
        assertTrue("urn:factur-x.eu:1p0:minimum" in xml, "MINIMUM profile id missing")
        assertTrue("<rsm:CrossIndustryInvoice" in xml, "root element missing")
        assertTrue("<ram:TypeCode>380</ram:TypeCode>" in xml, "default type code 380 missing")
        assertTrue("""<udt:DateTimeString format="102">20260607</udt:DateTimeString>""" in xml)
        assertTrue("<ram:GrandTotalAmount>119.00</ram:GrandTotalAmount>" in xml)
        assertTrue("""<ram:TaxTotalAmount currencyID="EUR">19.00</ram:TaxTotalAmount>""" in xml)
        assertTrue("""<ram:ID schemeID="VA">DE123456789</ram:ID>""" in xml, "VAT id missing")
        assertTrue("<ram:CountryID>DE</ram:CountryID>" in xml, "seller country missing")

        // Escaping: raw & / < / > must not leak into the XML body.
        assertTrue("INV-2026-001 &amp; Co" in xml, "ampersand in invoice number not escaped")
        assertTrue("Müller &amp; Sons GmbH" in xml, "ampersand in seller name not escaped")
        assertTrue("Acme &lt;Buyer&gt;" in xml, "angle brackets in buyer name not escaped")
        assertTrue("& Co" !in xml.replace("&amp;", ""), "a raw ampersand leaked into the XML")
    }

    @Test
    fun facturXXmlOmitsOptionalBlocksWhenAbsent() {
        val xml = FacturXInvoice(
            invoiceNumber = "INV-2",
            issueDateYyyymmdd = "20260101",
            sellerName = "Seller",
            buyerName = "Buyer",
            currencyCode = "USD",
            taxBasisTotal = "10.00",
            taxTotal = "0.00",
            grandTotal = "10.00",
            duePayable = "10.00",
        ).toXml()

        assertTrue("schemeID=\"VA\"" !in xml, "no VAT element expected when id absent")
        assertTrue("<ram:CountryID>" !in xml, "no country element expected when absent")
    }

    // --- attachFacturX -----------------------------------------------------

    @Test
    fun attachFacturXEmbedsNamedXmlWithDataRelationship() {
        val pdfBytes = pdf {
            page { text("HUMAN_READABLE_INVOICE") }
        }.toByteArray()
        val invoice = FacturXInvoice(
            invoiceNumber = "INV-99",
            issueDateYyyymmdd = "20260607",
            sellerName = "Seller GmbH",
            buyerName = "Buyer Ltd",
            currencyCode = "EUR",
            taxBasisTotal = "50.00",
            taxTotal = "9.50",
            grandTotal = "59.50",
            duePayable = "59.50",
        )

        val result = PdfTools.attachFacturX(pdfBytes, invoice)

        Loader.loadPDF(result).use { document ->
            // Base content survives.
            assertTrue("HUMAN_READABLE_INVOICE" in PDFTextStripper().getText(document))

            val names = assertNotNull(
                document.documentCatalog.names?.embeddedFiles,
                "no embedded-files name tree",
            )
            val specs = assertNotNull(names.names, "embedded-files name tree is empty")
            val spec = assertNotNull(specs["factur-x.xml"], "factur-x.xml not embedded under spec name")

            // Filename per spec.
            assertEquals("factur-x.xml", spec.file)
            // AFRelationship /Data on the file-spec dictionary.
            val relationship = spec.cosObject.getNameAsString(
                COSName.getPDFName("AFRelationship"),
            )
            assertEquals("Data", relationship, "AFRelationship should be /Data")

            // Embedded bytes are the invoice XML.
            val embeddedBytes = spec.embeddedFile.toByteArray()
            val embeddedXml = embeddedBytes.decodeToString()
            assertTrue("urn:factur-x.eu:1p0:minimum" in embeddedXml, "embedded XML is not the MINIMUM invoice")
            assertEquals(invoice.toXml(), embeddedXml, "embedded XML should match toXml()")
        }
    }

    // --- validatePdfABasics ------------------------------------------------

    @Test
    fun validatePdfABasicsFlagsPlainDocAndIsQuieterForPdfA() {
        val plain = pdf {
            page { text("plain document") }
        }.toByteArray()
        val pdfACompliant = pdf {
            metadata { title = "Compliant" }
            pdfA(true)
            page { text("compliant document") }
        }.toByteArray()

        val plainFindings = PdfTools.validatePdfABasics(plain)
        val pdfAFindings = PdfTools.validatePdfABasics(pdfACompliant)

        // A plain doc is missing XMP and an output intent — at least two findings.
        assertTrue(
            plainFindings.any { "XMP" in it },
            "plain doc should be flagged for missing XMP: $plainFindings",
        )
        assertTrue(
            plainFindings.any { "output intent" in it },
            "plain doc should be flagged for missing output intent: $plainFindings",
        )
        // The PDF/A doc supplies both, so it is strictly quieter.
        assertTrue(
            pdfAFindings.size < plainFindings.size,
            "PDF/A doc should yield fewer findings ($pdfAFindings vs $plainFindings)",
        )
        assertTrue(pdfAFindings.none { "XMP" in it }, "PDF/A doc should not be flagged for XMP")
        assertTrue(pdfAFindings.none { "output intent" in it }, "PDF/A doc should not be flagged for output intent")
    }

    /**
     * Sums the decoded byte length of every page's content across [pdf]. The
     * watermark appends a new content stream per page, so this grows after
     * stamping regardless of how PdfBox chooses to chunk the streams.
     */
    private fun contentStreamSize(pdf: ByteArray): Int =
        Loader.loadPDF(pdf).use { document ->
            var total = 0
            for (page in document.pages) {
                // getContents() concatenates every content stream on the page.
                page.contents?.use { input -> total += input.readBytes().size }
            }
            total
        }
}
