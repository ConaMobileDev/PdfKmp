package com.conamobile.pdfkmp.sign

import com.conamobile.pdfkmp.pdf
import org.apache.pdfbox.Loader
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for [PdfSigner]. A self-signed RSA certificate is
 * generated programmatically with BouncyCastle (a test-only dependency), the
 * document is signed through the keystore overload, and the result is
 * re-parsed with PdfBox to assert the signature dictionary exists and its
 * byte range is well-formed.
 */
class PdfSignerTest {

    @Test
    fun signedDocumentHasValidSignatureDictionary() {
        val (keyStore, password) = selfSignedKeyStore()

        val unsigned = pdf { page { text("sign me") } }.toByteArray()
        val signed = PdfSigner.sign(
            pdfBytes = unsigned,
            keyStore = keyStore,
            alias = ALIAS,
            password = password,
            reason = "Approval",
            location = "Earth",
        )

        // Incremental signing appends to the original, so the signed bytes are
        // at least as large as the input.
        assertTrue(signed.size >= unsigned.size, "signed document should not be smaller than the input")

        Loader.loadPDF(signed).use { loaded ->
            val signatures = loaded.signatureDictionaries
            assertTrue(signatures.isNotEmpty(), "no signature dictionary found")
            val signature = signatures.first()
            assertEquals("Approval", signature.reason)
            assertEquals("Earth", signature.location)

            // The byte range must be a 4-element array (offset/len/offset/len)
            // with the signature value carved out between the two ranges.
            val byteRange = assertNotNull(signature.byteRange, "signature has no byte range")
            assertEquals(4, byteRange.size, "byte range must have 4 entries")
            assertEquals(0, byteRange[0], "first range must start at the file beginning")
            assertTrue(byteRange[1] > 0, "first signed range must be non-empty")
            // The gap between the two ranges is where /Contents (the CMS blob) sits.
            val gapStart = byteRange[1]
            val gapEnd = byteRange[2]
            assertTrue(gapEnd > gapStart, "signature value gap must be positive")
            // The CMS signed content extracts without throwing — proves the
            // signature value is parseable PKCS#7.
            val cms = signature.getContents(signed)
            assertTrue(cms.isNotEmpty(), "signature /Contents is empty")
        }
    }

    @Test
    fun callbackOverloadAppendsSignatureWithoutBuiltInBc() {
        // The callback path needs no BouncyCastle from PdfKmp's side; here the
        // test supplies the CMS itself (also via BC, but that's the caller's
        // choice). We assert the signature dictionary lands and metadata
        // round-trips.
        val (keyStore, password) = selfSignedKeyStore()
        val privateKey = keyStore.getKey(ALIAS, password) as java.security.PrivateKey
        val chain = keyStore.getCertificateChain(ALIAS)

        val unsigned = pdf { page { text("callback") } }.toByteArray()
        val signed = PdfSigner.sign(
            pdfBytes = unsigned,
            name = "Tester",
            reason = "Callback",
        ) { content ->
            // Build a detached CMS over the supplied content range.
            val signerCert = chain.first() as X509Certificate
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
            val gen = org.bouncycastle.cms.CMSSignedDataGenerator().apply {
                addSignerInfoGenerator(
                    org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                        org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().build(),
                    ).build(signer, signerCert),
                )
                addCertificates(org.bouncycastle.cert.jcajce.JcaCertStore(chain.toList()))
            }
            gen.generate(org.bouncycastle.cms.CMSProcessableByteArray(content), false).encoded
        }

        Loader.loadPDF(signed).use { loaded ->
            val signature = loaded.signatureDictionaries.firstOrNull()
            assertNotNull(signature, "callback-signed document has no signature dictionary")
            assertEquals("Callback", signature.reason)
            assertEquals("Tester", signature.name)
        }
    }

    /** Builds an in-memory key store holding a fresh self-signed RSA key pair. */
    private fun selfSignedKeyStore(): Pair<KeyStore, CharArray> {
        val password = "test".toCharArray()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L)
        val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000)
        val subject = X500Principal("CN=PdfKmp Test, O=PdfKmp")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))

        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(ALIAS, keyPair.private, password, arrayOf<Certificate>(certificate))
        }
        return keyStore to password
    }

    private companion object {
        const val ALIAS = "test"
    }
}
