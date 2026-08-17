package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.pdf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetCurrentContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abort-path cleanup for the iOS driver.
 *
 * [DocumentRenderer] calls `close()` when a draw call throws before `finish()`.
 * The resource that leaks on iOS is process-global: an unterminated
 * `UIGraphicsBeginPDFContextToData` stays on the thread's UIKit context stack
 * for the rest of the process, so every later `UIGraphicsGetCurrentContext()`
 * caller — PdfKmp or not — inherits a context nobody owns.
 *
 * The stack is what these tests assert against. A leaked context does *not*
 * corrupt the next PdfKmp document (each driver writes to its own `NSMutableData`,
 * so a document generated afterwards still comes back as a well-formed PDF);
 * checking the output would pass either way and prove nothing.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDriverLifecycleTest {

    @AfterTest
    fun resetLogger() {
        PdfLog.logger = null
        // Never leave a context behind for the next test in this process — the
        // very failure mode under test.
        while (UIGraphicsGetCurrentContext() != null) UIGraphicsEndPDFContext()
    }

    @Test
    fun close_popsThePdfContextOffTheUiKitStack() {
        assertNull(UIGraphicsGetCurrentContext(), "a previous test leaked a context")

        val aborted = IosPdfDriver(PdfMetadata.Empty, emptyList())
        aborted.beginPage(PageSize.A4)
        aborted.endPage()
        assertNotNull(UIGraphicsGetCurrentContext(), "the driver should hold an open PDF context here")

        aborted.close()
        assertNull(
            UIGraphicsGetCurrentContext(),
            "close() must end the PDF context; leaving it current strands it on the " +
                "thread's UIKit stack for the rest of the process",
        )
    }

    @Test
    fun close_afterFinish_doesNotPopSomebodyElsesContext() {
        assertNull(UIGraphicsGetCurrentContext(), "a previous test leaked a context")

        val finished = IosPdfDriver(PdfMetadata.Empty, emptyList())
        finished.beginPage(PageSize.A4)
        finished.endPage()
        assertTrue(finished.finish().isNotEmpty())
        assertNull(UIGraphicsGetCurrentContext())

        // The renderer's cleanup is best-effort and can run after a successful
        // finish(); an unguarded second UIGraphicsEndPDFContext would pop a
        // context this driver never opened.
        val bystander = IosPdfDriver(PdfMetadata.Empty, emptyList())
        val bystanderContext = UIGraphicsGetCurrentContext()
        finished.close()
        finished.close()
        assertEquals(
            bystanderContext,
            UIGraphicsGetCurrentContext(),
            "close() after finish() must be a no-op, not another pop",
        )
        bystander.close()
    }

    @Test
    fun close_onANeverDrawnDriver_stillReleasesTheContextItOpenedInInit() {
        assertNull(UIGraphicsGetCurrentContext(), "a previous test leaked a context")

        // The context opens in init { }, so a driver that fails before its
        // first page still owns one.
        IosPdfDriver(PdfMetadata.Empty, emptyList()).close()
        assertNull(UIGraphicsGetCurrentContext())

        assertEquals("%PDF-", pdf { page { text("after abort") } }.toByteArray().decodeToString(0, 5))
        assertNull(UIGraphicsGetCurrentContext(), "a completed document must leave no context behind")
    }
}
