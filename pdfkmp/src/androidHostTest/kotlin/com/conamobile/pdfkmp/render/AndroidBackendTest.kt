package com.conamobile.pdfkmp.render

import android.graphics.Bitmap
import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.image.PdfImagePolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Decode-budget coverage for the Android backend, which the JVM and iOS suites
 * cannot reach. Robolectric supplies working `android.graphics` classes, so
 * `BitmapFactory` behaves like the real thing on the host JVM.
 *
 * Scope note: `AndroidPdfDriver` is deliberately *not* exercised here.
 * `android.graphics.pdf.PdfDocument` is native-backed — Robolectric leaves its
 * `mNativeDocument` at 0, so every call throws `document is closed!`. Covering
 * the driver (and `AndroidFontRegistry`'s `Typeface.Builder` path) needs an
 * instrumented test on a device or emulator, which this repo has no CI job for.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBackendTest {

    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        PdfLog.logger = { warnings += it }
    }

    @After
    fun tearDown() {
        PdfLog.logger = null
        warnings.clear()
        PdfImagePolicy.maxDecodePixels = PdfImagePolicy.DEFAULT_MAX_DECODE_PIXELS
    }

    /**
     * The branch that used to skip the budget entirely: when the declared
     * dimensions already fall under the 200-DPI target, `decodeBitmap` returned
     * an unsampled full decode without consulting the ceiling, so a large
     * enough requested draw size let any header through.
     */
    @Test
    fun sourceUnderTheDpiTarget_isStillBoundedByTheBudget() {
        PdfImagePolicy.maxDecodePixels = 10_000L
        val bytes = realPng(400, 400)

        // A draw size this large puts the 200-DPI target well above the source,
        // so DPI asks for no reduction — only the budget can bound this decode.
        val decoded = decodeBitmap(bytes, 4_000f, 4_000f, allowDownScale = true)

        assertNotNull(decoded, "the image must still render, sampled")
        assertTrue(
            decoded.width.toLong() * decoded.height.toLong() <= 10_000L,
            "decoded ${decoded.width}x${decoded.height} exceeds the 10 000-pixel budget",
        )
        assertTrue(warnings.any { "decode budget" in it }, "expected a budget warning, got $warnings")
    }

    /** `allowDownScale = false` opts out of DPI sampling, never out of the budget. */
    @Test
    fun budgetApplies_evenWhenDownScaleIsDisabled() {
        PdfImagePolicy.maxDecodePixels = 10_000L
        val decoded = decodeBitmap(realPng(400, 400), 100f, 100f, allowDownScale = false)

        assertNotNull(decoded)
        assertTrue(
            decoded.width.toLong() * decoded.height.toLong() <= 10_000L,
            "decoded ${decoded.width}x${decoded.height} exceeds the budget with down-scale off",
        )
    }

    @Test
    fun sourceWithinTheBudget_isDecodedUnsampled() {
        val decoded = decodeBitmap(realPng(64, 64), 64f, 64f, allowDownScale = true)

        assertNotNull(decoded)
        assertTrue(decoded.width == 64 && decoded.height == 64, "got ${decoded.width}x${decoded.height}")
        assertTrue(warnings.none { "decode budget" in it }, "small image tripped the budget: $warnings")
    }

    @Test
    fun emptyPayload_decodesToNothingRatherThanThrowing() {
        assertTrue(decodeBitmap(ByteArray(0), 10f, 10f, allowDownScale = true) == null)
    }

    private fun realPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFFF8800.toInt())
        return ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
    }
}
