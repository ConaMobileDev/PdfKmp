package com.conamobile.pdfkmp.samples

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.test.Ignore
import kotlin.test.Test
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL

/**
 * Local-only utility: writes the brochure PDF to /tmp so we can convert it
 * to PNG for the README hero image. Not part of CI — flagged `@Ignore`.
 *
 * To regenerate the screenshots, comment out `@Ignore` and run:
 *   ./gradlew :pdfkmp:iosSimulatorArm64Test --tests "*.BrochurePdfDump.dump*"
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class BrochurePdfDump {

    @Test
    @Ignore
    fun dumpBrochureToTmp() {
        val bytes = Samples.brochure().toByteArray()
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val url = NSURL.fileURLWithPath("/tmp/pdfkmp-brochure.pdf")
        nsData.writeToURL(url, atomically = true)
        println("Wrote ${bytes.size} bytes to /tmp/pdfkmp-brochure.pdf")
    }
}
