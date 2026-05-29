package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.samples.Samples
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM-backend integration tests.
 *
 * The common [com.conamobile.pdfkmp.samples.SamplesSmokeTest] only checks the
 * `%PDF-` header. These tests go further: every sample is re-parsed and its
 * first page rendered to a raster, which forces PdfBox to walk the embedded
 * fonts, shadings, and image XObjects the JVM canvas wrote. A malformed
 * gradient function or font subset that still serialises cleanly would throw
 * here, so this is the real correctness gate for the Desktop backend.
 */
class JvmBackendTest {

    private val pngFixture = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
        0x78, 0x9C.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
        0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    @Test
    fun samplesReParseAndRender() {
        val documents = listOf(
            "helloWorld" to Samples.helloWorld(),
            "typography" to Samples.typography(),
            "tableShowcase" to Samples.tableShowcase(),
            "vectorShowcase" to Samples.vectorShowcase(),
            "vectorAdvanced" to Samples.vectorAdvanced(),
            "brochure" to Samples.brochure(),
            "showcase" to Samples.showcase(),
            "customDesigns" to Samples.customDesigns(pngFixture),
        )
        for ((name, doc) in documents) {
            Loader.loadPDF(doc.toByteArray()).use { loaded ->
                assertTrue(loaded.numberOfPages >= 1, "$name produced no pages")
                // Rasterise the first page at screen DPI; throws on any
                // malformed font / shading / image content stream.
                val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
                assertTrue(image.width > 0 && image.height > 0, "$name rendered an empty raster")
            }
        }
    }

    @Test
    fun imageBackedSamplesRenderWithRealPng() {
        // A genuine multi-pixel PNG (not the 1×1 fixture) exercises the
        // decode → slice → downscale → embed path the Desktop sample hits
        // when you open the image-backed samples.
        val png = generatePng(640, 480)
        val documents = listOf(
            "withImage" to Samples.withImage(png),
            "slicedImage" to Samples.slicedImage(png),
            "imageDownscale" to Samples.imageDownscale(png),
            "customDesigns" to Samples.customDesigns(png),
        )
        for ((name, doc) in documents) {
            Loader.loadPDF(doc.toByteArray()).use { loaded ->
                assertTrue(loaded.numberOfPages >= 1, "$name produced no pages")
                val image = PDFRenderer(loaded).renderImageWithDPI(0, 72f)
                assertTrue(image.width > 0 && image.height > 0, "$name rendered empty")
            }
        }
    }

    private fun generatePng(width: Int, height: Int): ByteArray {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = java.awt.GradientPaint(
            0f, 0f, java.awt.Color(0x4F46E5),
            width.toFloat(), height.toFloat(), java.awt.Color(0xEC4899),
        )
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun metadataIsWritten() {
        val doc = com.conamobile.pdfkmp.pdf {
            metadata {
                title = "Unit Test Title"
                author = "PdfKmp"
            }
            page { text("hi") }
        }
        Loader.loadPDF(doc.toByteArray()).use { loaded ->
            val info = loaded.documentInformation
            assertTrue(info.title == "Unit Test Title", "title not written: ${info.title}")
            assertTrue(info.author == "PdfKmp", "author not written: ${info.author}")
        }
    }
}
