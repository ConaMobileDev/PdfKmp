package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
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
    fun oversizedDeclaredImage_isSkippedBeforePixelAllocation() {
        val bytes = png(width = 50_000, height = 50_000) + byteArrayOf(0, 0, 0, 2) +
            byteArrayOf(0x49, 0x44, 0x41, 0x54) + byteArrayOf(0x78, 0x9C.toByte()) +
            byteArrayOf(0, 0, 0, 0) + byteArrayOf(0, 0, 0, 0) + byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        val doc = pdf {
            page {
                image(bytes, width = 100.dp, height = 100.dp)
            }
        }
        assertTrue(doc.size > 0)
        assertTrue(warnings.any { "budget" in it }, "expected a decode-budget warning, got $warnings")
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
