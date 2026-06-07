package com.conamobile.pdfkmp.sign

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Calendar

/**
 * JVM/Desktop-only digital signing of PdfKmp (or any) PDF bytes.
 *
 * Signing applies an **incremental update** to the input bytes — the original
 * content is left untouched and a signature dictionary plus the appended
 * CMS/PKCS#7 detached signature are added at the end. This is the standard way
 * to keep a PDF signature valid (the signed byte range covers everything
 * except the signature value itself).
 *
 * Two entry points:
 *
 * - [sign] with a `(ByteArray) -> ByteArray` callback — **no BouncyCastle
 *   dependency**. The caller computes the CMS detached signature over the
 *   supplied byte range however they like (an HSM, a remote signing service,
 *   their own BC code) and returns the DER-encoded `SignedData`. This is the
 *   recommended path for production: PdfKmp never touches the private key.
 *
 * - [sign] with a [KeyStore] / alias / password — a convenience that builds
 *   the CMS signature for you using **BouncyCastle**. BouncyCastle is a
 *   `compileOnly` dependency of PdfKmp (the ~9 MB jar is not bundled into the
 *   published artifact), so callers using this overload must add
 *   `org.bouncycastle:bcpkix-jdk18on` to their own runtime classpath.
 *   Calling it without BC on the classpath throws [NoClassDefFoundError].
 *
 * This is a JVM-only API and lives in `jvmMain`; there is no Android/iOS
 * counterpart.
 */
public object PdfSigner {

    /**
     * Signs [pdfBytes] using a caller-supplied CMS signing callback. No
     * BouncyCastle dependency is involved on PdfKmp's side.
     *
     * @param pdfBytes the PDF to sign.
     * @param name optional signer name written into the signature dictionary.
     * @param reason optional human-readable reason for signing.
     * @param location optional signing location.
     * @param cmsSigner produces the DER-encoded CMS/PKCS#7 detached signature
     *   over the bytes it is handed (the document's signed byte range). The
     *   input is the content to sign; the output is the `SignedData`.
     * @return the signed PDF bytes (original content + incremental signature).
     */
    public fun sign(
        pdfBytes: ByteArray,
        name: String? = null,
        reason: String? = null,
        location: String? = null,
        cmsSigner: (ByteArray) -> ByteArray,
    ): ByteArray {
        Loader.loadPDF(pdfBytes).use { document ->
            val signature = buildSignatureDictionary(name, reason, location)
            val signer = SignatureInterface { content: InputStream ->
                cmsSigner(content.readBytes())
            }
            document.addSignature(signature, signer)
            // saveIncremental appends the signature to the existing bytes so the
            // signed byte range stays valid; a full save() would rewrite the
            // file and invalidate the signature.
            return ByteArrayOutputStream().use { out ->
                document.saveIncremental(out)
                out.toByteArray()
            }
        }
    }

    /**
     * Convenience overload that builds the CMS signature with BouncyCastle
     * from a [KeyStore] entry. **Requires `org.bouncycastle:bcpkix-jdk18on`
     * on the runtime classpath** — see the class KDoc. Prefer the
     * callback-based [sign] for production where the key lives in an HSM or a
     * signing service.
     *
     * @param pdfBytes the PDF to sign.
     * @param keyStore key store holding the signing key + certificate chain.
     * @param alias entry alias of the private key.
     * @param password password protecting the key entry.
     * @param reason optional human-readable reason for signing.
     * @param location optional signing location.
     * @return the signed PDF bytes.
     */
    public fun sign(
        pdfBytes: ByteArray,
        keyStore: KeyStore,
        alias: String,
        password: CharArray,
        reason: String? = null,
        location: String? = null,
    ): ByteArray {
        val privateKey = keyStore.getKey(alias, password) as? PrivateKey
            ?: error("Key store entry '$alias' is not a private key")
        val chain = keyStore.getCertificateChain(alias)
            ?: error("Key store entry '$alias' has no certificate chain")
        val signerCert = chain.first() as X509Certificate
        return sign(
            pdfBytes = pdfBytes,
            name = signerCert.subjectX500Principal.name,
            reason = reason,
            location = location,
            cmsSigner = { content -> buildCmsSignature(content, privateKey, chain) },
        )
    }

    /** Assembles the PDF signature dictionary (filter, subfilter, metadata). */
    private fun buildSignatureDictionary(
        name: String?,
        reason: String?,
        location: String?,
    ): PDSignature = PDSignature().apply {
        setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
        // PKCS#7 detached is the broadly-supported CMS subfilter.
        setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
        name?.let { setName(it) }
        reason?.let { setReason(it) }
        location?.let { setLocation(it) }
        signDate = Calendar.getInstance()
    }

    /**
     * Builds a CMS/PKCS#7 detached SHA-256 signature over [content] using
     * BouncyCastle. Isolated in its own method so the BC types only load when
     * the keystore overload is actually called.
     */
    private fun buildCmsSignature(
        content: ByteArray,
        privateKey: PrivateKey,
        chain: Array<Certificate>,
    ): ByteArray {
        val certStore = JcaCertStore(chain.toList())
        val signerCert = chain.first() as X509Certificate
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(
                    JcaDigestCalculatorProviderBuilder().build(),
                ).build(contentSigner, signerCert),
            )
            addCertificates(certStore)
        }
        val typedData = CMSProcessableByteArray(content)
        return generator.generate(typedData, false).encoded
    }
}
