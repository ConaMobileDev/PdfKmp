package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.pdf
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for document encryption and file attachments.
 *
 * These exercise the security/packaging paths the [JvmBackendTest] correctness
 * gate does not touch: each document is produced through the public `pdf { }`
 * DSL (which resolves the JVM PdfBox driver on this source set) and then
 * re-parsed with PdfBox's [Loader] to assert what actually landed in the bytes.
 */
class JvmSecurityTest {

    @Test
    fun encryptedDocumentRequiresPasswordAndEnforcesPermissions() {
        val bytes = pdf {
            encryption {
                ownerPassword = "owner"
                userPassword = "user"
                allowPrinting = true
                allowCopying = false
                allowModification = false
            }
            page { text("secret") }
        }.toByteArray()

        // Opening without any password must fail — the document is encrypted
        // and the user password is non-empty.
        assertFailsWith<InvalidPasswordException> {
            Loader.loadPDF(bytes).close()
        }

        // The user password opens it; permissions reflect the flags set above.
        Loader.loadPDF(bytes, "user").use { loaded ->
            assertTrue(loaded.isEncrypted, "document should report itself encrypted")
            val permissions = loaded.currentAccessPermission
            assertTrue(permissions.canPrint(), "printing should be allowed")
            assertTrue(!permissions.canExtractContent(), "copying should be disallowed")
            assertTrue(!permissions.canModify(), "modification should be disallowed")
        }

        // The owner password also opens it (with full access).
        Loader.loadPDF(bytes, "owner").use { loaded ->
            assertTrue(loaded.isEncrypted, "document should report itself encrypted")
        }
    }

    @Test
    fun attachmentRoundTripsThroughCatalogNames() {
        val payload = "<xml/>".encodeToByteArray()
        val bytes = pdf {
            attachment(
                fileName = "invoice.xml",
                bytes = payload,
                mimeType = "application/xml",
                description = "Structured invoice",
            )
            page { text("invoice") }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            // Navigate catalog → Names → EmbeddedFiles, the standard discovery
            // path a ZUGFeRD/Factur-X consumer follows.
            val names = assertNotNull(
                loaded.documentCatalog.names,
                "document catalog has no Names dictionary",
            )
            val embeddedFiles = assertNotNull(
                names.embeddedFiles,
                "Names dictionary has no EmbeddedFiles tree",
            )
            val files = assertNotNull(
                embeddedFiles.names,
                "EmbeddedFiles name tree is empty",
            )

            assertTrue("invoice.xml" in files.keys, "attachment file name missing")
            val spec = files.getValue("invoice.xml")
            val embedded = assertNotNull(
                spec.embeddedFile,
                "file specification has no embedded stream",
            )
            assertEquals("Structured invoice", spec.fileDescription)
            // Bytes round-trip exactly.
            val roundTripped = embedded.toByteArray()
            assertTrue(
                payload.contentEquals(roundTripped),
                "embedded bytes did not round-trip",
            )
        }
    }
}
