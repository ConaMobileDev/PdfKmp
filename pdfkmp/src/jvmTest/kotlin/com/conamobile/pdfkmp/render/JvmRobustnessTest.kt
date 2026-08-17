package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.image.PdfImagePolicy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Untrusted-input robustness of the JVM/PdfBox backend: hostile image
 * dimension claims must be rejected before pixel memory is allocated, and
 * a corrupt custom font must degrade to the bundled face rather than
 * aborting document generation.
 */
class JvmRobustnessTest {

    private val warnings = mutableListOf<String>()

    @AfterTest
    fun resetLogger() {
        PdfLog.logger = null
        warnings.clear()
    }

    @Test
    fun oversizedDeclaredImage_isSampledDownBeforePixelAllocation() {
        val bytes = png(width = 50_000, height = 50_000) + byteArrayOf(0, 0, 0, 2) +
            byteArrayOf(0x49, 0x44, 0x41, 0x54) + byteArrayOf(0x78, 0x9C.toByte()) +
            byteArrayOf(0, 0, 0, 0) + byteArrayOf(0, 0, 0, 0) + byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        val doc = pdf {
            page {
                image(bytes, width = 100.dp, height = 100.dp)
            }
        }
        assertTrue(doc.size > 0)
        // The bound is applied from the header, before ImageIO is asked for a
        // single pixel — this fixture then fails the decode anyway (its IDAT
        // holds no image data), which is what the second warning reports.
        assertTrue(warnings.any { "budget" in it }, "expected a decode-budget warning, got $warnings")
    }

    /**
     * The budget must *sample* an oversized image rather than drop it: Android
     * has always sub-sampled, so dropping here would make one document render
     * an image on one platform and a blank slot on another.
     */
    @Test
    fun oversizedButValidImage_stillRendersSampledDown() {
        val previous = PdfImagePolicy.maxDecodePixels
        try {
            // 10 000 px of budget against a 160 000 px image — same code path a
            // 139-megapixel A0 scan takes, without allocating one in a test.
            PdfImagePolicy.maxDecodePixels = 10_000L
            val doc = pdf {
                page {
                    image(realPng(width = 400, height = 400), width = 100.dp, height = 100.dp)
                }
            }
            assertTrue(doc.size > 0)
            assertTrue(
                warnings.any { "sampled down" in it },
                "expected a sampling warning rather than a skip, got $warnings",
            )
            assertTrue(
                warnings.none { "not a decodable image" in it },
                "the image must still render, not be dropped: $warnings",
            )
            val rendered = Loader.loadPDF(doc.toByteArray()).use { pdfBox ->
                PDFRenderer(pdfBox).renderImageWithDPI(0, 72f)
            }
            assertTrue(rendered.width > 0 && rendered.height > 0)
        } finally {
            PdfImagePolicy.maxDecodePixels = previous
        }
    }

    /** A genuinely decodable PNG of the requested size, built through ImageIO. */
    private fun realPng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.ORANGE
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun unparseableCustomFont_fallsBackToBundledFaceWithWarning() {
        val corrupt = PdfFont.Custom("CorruptFace", byteArrayOf(1, 2, 3, 4))
        val doc = pdf {
            registerFont(corrupt)
            page {
                text("drawn with fallback") { font = corrupt }
            }
        }
        assertTrue(doc.size > 0)
        assertTrue(warnings.any { "could not be parsed" in it }, "expected a font-parse warning, got $warnings")
    }

    /**
     * Every PDType0Font.load embeds a separate font program. The fallback face
     * therefore has to share the registry's cache entry with the ordinary
     * lookup, or a document that uses Inter *and* trips the fallback carries
     * two copies of the same subset.
     */
    @Test
    fun fallbackFace_sharesOneEmbeddedProgramWithNormalInterUse() {
        val corrupt = PdfFont.Custom("CorruptFace", byteArrayOf(1, 2, 3, 4))
        val withFallback = pdf {
            registerFont(corrupt)
            page {
                text("plain default-font text")
                text("drawn with fallback") { font = corrupt }
            }
        }
        val withoutFallback = pdf {
            page { text("plain default-font text") }
        }

        assertTrue(warnings.any { "could not be parsed" in it }, "fallback did not trigger: $warnings")
        val fontProgramsWith = countEmbeddedFontFiles(withFallback.toByteArray())
        val fontProgramsWithout = countEmbeddedFontFiles(withoutFallback.toByteArray())
        assertEquals(
            fontProgramsWithout,
            fontProgramsWith,
            "the fallback embedded an extra copy of the bundled face",
        )
    }

    /**
     * Distinct embedded font programs in the output, by stream identity — two
     * loads of the same face produce two separate `/FontFile2` streams, which
     * is exactly what this has to catch.
     */
    private fun countEmbeddedFontFiles(bytes: ByteArray): Int =
        Loader.loadPDF(bytes).use { document ->
            val programs = mutableSetOf<COSBase>()
            for (page in document.pages) {
                val resources = page.resources ?: continue
                for (name in resources.fontNames) {
                    val descriptor = resources.getFont(name)?.fontDescriptor ?: continue
                    descriptor.fontFile2?.cosObject?.let { programs += it }
                }
            }
            programs.size
        }

    @Test
    fun honestlySizedImage_doesNotTriggerTheBudgetGuard() {
        val bytes = png(width = 1, height = 1) + byteArrayOf(0, 0, 0, 4) +
            byteArrayOf(0x49, 0x44, 0x41, 0x54) +
            byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60) +
            byteArrayOf(0, 0, 0, 0) + byteArrayOf(0, 0, 0, 0) + byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        val doc = pdf {
            page {
                image(bytes, width = 50.dp, height = 50.dp)
            }
        }
        assertTrue(doc.size > 0)
        assertTrue(warnings.none { "budget" in it }, "small image must not trip the budget guard, got $warnings")
    }

    init {
        PdfLog.logger = { warnings += it }
    }

    private fun png(width: Int, height: Int): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D,
        0x49, 0x48, 0x44, 0x52,
        ((width ushr 24) and 0xFF).toByte(),
        ((width ushr 16) and 0xFF).toByte(),
        ((width ushr 8) and 0xFF).toByte(),
        (width and 0xFF).toByte(),
        ((height ushr 24) and 0xFF).toByte(),
        ((height ushr 16) and 0xFF).toByte(),
        ((height ushr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(),
        0x08, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )
}
