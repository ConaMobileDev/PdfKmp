package com.conamobile.pdfkmp.storage

import com.conamobile.pdfkmp.PdfDocument
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Filename sanitisation at the public `save(...)` boundary. Invalid names
 * must be rejected by the common validator before any platform storage
 * code composes a filesystem path or a `MediaStore` display name.
 */
class SaveFileNameTest {

    private val document = PdfDocument(byteArrayOf(0x25, 0x50, 0x44, 0x46))

    @Test
    fun ordinaryFileNames_passValidation() {
        for (name in listOf("report.pdf", "my report 2026.pdf", "résumé.pdf", "name.with.many.dots.pdf")) {
            validateSaveFileName(name)
        }
    }

    @Test
    fun pathTraversalNames_areRejectedBeforeAnyStorageCall() {
        for (name in listOf(
            "../evil.pdf",
            "..\\evil.pdf",
            "a/b.pdf",
            "a\\b.pdf",
            "..",
            ".",
            "",
            "report.pdf ",
            "report.",
            "CON.pdf",
            "aux.pdf",
            "COM1.pdf",
            "LPT9.pdf",
            "evil" + Char(0) + ".pdf",
            "report:v2.pdf",
            "a*b.pdf",
            "a?b.pdf",
            "a|b.pdf",
            "a<b.pdf",
            "a>b.pdf",
            "a\"b.pdf",
            "tab\tname.pdf",
        )) {
            assertFailsWith<IllegalArgumentException>(
                message = "expected '$name' to be rejected",
            ) {
                document.save(StorageLocation.Cache, name)
            }
        }
    }
}
