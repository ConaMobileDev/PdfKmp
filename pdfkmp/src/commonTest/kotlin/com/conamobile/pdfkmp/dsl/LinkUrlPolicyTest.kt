package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.PdfUrls
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Scheme allowlist coverage for [PdfUrls] and its enforcement at the
 * `link(url) { }` DSL boundary: unsafe schemes must degrade to unlinked
 * content (with a [PdfLog] warning) instead of producing a `/URI`
 * annotation a hostile document could weaponize.
 */
class LinkUrlPolicyTest {

    @AfterTest
    fun resetLogger() {
        PdfLog.logger = null
    }

    @Test
    fun safeSchemes_areAccepted() {
        for (url in listOf(
            "https://example.com",
            "http://example.com/insecure-ok",
            "HTTPS://EXAMPLE.COM",
            "mailto:team@example.com",
            "tel:+4215551234",
        )) {
            assertTrue(PdfUrls.isSafeExternalUrl(url), "expected $url to be allowed")
        }
    }

    @Test
    fun dangerousSchemes_areRejected() {
        for (url in listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,<script>alert(1)</script>",
            "content://com.android.providers/contacts",
            "intent://scan/#Intent;scheme=zxing;end",
            "ftp://example.com/file",
            "//example.com/scheme-relative",
            "example.com/no-scheme",
            "https ://space-in-scheme",
            "1http://leading-digit",
            "",
            ":",
            "https://example.com/\nforged-log-line",
            "https://example.com/\u0001control",
        )) {
            assertFalse(PdfUrls.isSafeExternalUrl(url), "expected $url to be rejected")
        }
    }

    @Test
    fun linkWithSafeUrl_producesHyperlinkAnnotation() {
        val factory = FakePdfDriverFactory()
        val doc = pdf(factory = factory) {
            page {
                link("https://example.com") { text("safe") }
            }
        }
        assertEquals("https://example.com", doc.hyperlinks.single().url)
    }

    @Test
    fun linkWithUnsafeUrl_drawsContentWithoutAnnotation_andWarns() {
        val warnings = mutableListOf<String>()
        PdfLog.logger = { warnings += it }
        val factory = FakePdfDriverFactory()
        val doc = pdf(factory = factory) {
            page {
                link("javascript:alert(1)") { text("click me") }
            }
        }
        assertTrue(doc.hyperlinks.isEmpty())
        assertTrue(warnings.any { "javascript:" in it }, "expected a scheme warning, got $warnings")
        val texts = factory.drivers.single().pages
            .flatMap { it.canvas.calls }
            .filterIsInstance<DrawCall.Text>()
        assertTrue(texts.any { it.text == "click me" })
    }

    @Test
    fun linkToAnchor_isUnaffectedByTheExternalUrlPolicy() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                anchor("section1")
                linkToAnchor("section1") { text("go") }
            }
        }
        assertTrue(factory.drivers.single().finished)
    }
}
