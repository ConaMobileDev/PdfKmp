package com.conamobile.pdfkmp.metadata

/**
 * Document metadata written into the PDF info dictionary. All fields are
 * optional; renderers omit empty values.
 *
 * Two security/packaging concerns ride alongside the info-dictionary fields
 * so that the existing driver factory signature (`create(metadata, …)`) keeps
 * carrying everything a backend needs without widening `DocumentSpec`:
 *
 * - [encryption] requests standard-security password protection.
 * - [attachments] embeds files into the document (e.g. a ZUGFeRD invoice XML).
 *
 * Both are backend-dependent — see their own KDoc for the per-platform support
 * matrix. Backends that cannot honour a request skip it silently rather than
 * failing the render.
 */
public data class PdfMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = "PdfKmp",
    /**
     * Optional document encryption. `null` (the default) produces an
     * unencrypted document. See [PdfEncryption] for the platform support
     * matrix.
     */
    val encryption: PdfEncryption? = null,
    /**
     * Files embedded into the document. Empty (the default) embeds nothing.
     * See [PdfAttachment] for the platform support matrix.
     */
    val attachments: List<PdfAttachment> = emptyList(),
    /**
     * Best-effort PDF/A-2b conformance. `false` (the default) produces an
     * ordinary PDF. When `true` the JVM/Desktop backend embeds an XMP
     * metadata packet (`pdfaid:part=2`, `conformance=B`), an sRGB output
     * intent, sets the document `MarkInfo /Marked true`, and aligns the
     * document info with the XMP.
     *
     * Honest scope: this is **best-effort PDF/A-2b** — XMP id + output
     * intent + document-info alignment. Full veraPDF conformance (every
     * font fully embedded in all edge cases, all colour spaces, a complete
     * structure/tag tree) is **not** guaranteed. Only the JVM/Desktop
     * backend acts on this flag; Android and iOS ignore it.
     */
    val pdfACompliance: Boolean = false,
    /**
     * Document language as a BCP-47 tag (e.g. `"en"`, `"en-US"`). Written to
     * the catalog `/Lang` entry by the JVM/Desktop backend — required for
     * tagged-PDF / PDF/A accessibility. `null` (the default) writes no
     * language. Ignored on Android and iOS.
     */
    val language: String? = null,
) {
    public companion object {
        public val Empty: PdfMetadata = PdfMetadata()
    }
}

/**
 * Standard-security password protection applied to a document.
 *
 * The owner password unlocks full access; the user password (which may be
 * empty) unlocks the document under the permission flags below. When the user
 * password is empty the document opens without a prompt but still enforces the
 * permission flags.
 *
 * Platform support:
 * - **JVM / Desktop (PdfBox)** — full support. Encrypted with AES‑256, the
 *   permission flags mapped onto the PDF access-permission bits.
 * - **iOS (Core Graphics)** — owner/user passwords plus printing and copying
 *   are honoured via `kCGPDFContext*` keys. There is no Core Graphics flag for
 *   "allow modification", so [allowModification] is ignored on iOS.
 * - **Android** — `android.graphics.pdf.PdfDocument` exposes no encryption
 *   API, so requesting encryption on Android is a no-op (the document is
 *   produced unencrypted). Encrypt out-of-band if you target Android.
 *
 * @property ownerPassword password granting full, unrestricted access. Must be
 *   non-empty for protection to be meaningful.
 * @property userPassword password required to open the document; empty (the
 *   default) means no open prompt while the permission flags still apply.
 * @property allowPrinting whether the document may be printed.
 * @property allowCopying whether text/graphics may be extracted (copy/paste,
 *   accessibility readers).
 * @property allowModification whether the document content may be modified.
 *   Ignored on iOS (no corresponding Core Graphics flag).
 */
public data class PdfEncryption(
    val ownerPassword: String,
    val userPassword: String = "",
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowModification: Boolean = false,
)

/**
 * A file embedded into the produced PDF.
 *
 * Embedded files travel inside the document so a single PDF can carry a
 * machine-readable companion — the canonical use is a ZUGFeRD / Factur-X
 * invoice that embeds the structured `factur-x.xml` next to the human-readable
 * pages.
 *
 * Platform support:
 * - **JVM / Desktop (PdfBox)** — full support. The file is registered in the
 *   document catalog's embedded-files name tree.
 * - **iOS / Android** — the underlying PDF generators expose no embedded-file
 *   API, so attachments are silently skipped on those platforms.
 *
 * @property fileName name shown to the user and stored in the file
 *   specification (e.g. `"invoice.xml"`).
 * @property bytes raw file contents.
 * @property mimeType MIME type recorded as the embedded stream subtype;
 *   defaults to `"application/octet-stream"`.
 * @property description optional human-readable description of the attachment.
 */
public class PdfAttachment(
    public val fileName: String,
    public val bytes: ByteArray,
    public val mimeType: String = "application/octet-stream",
    public val description: String? = null,
) {
    // Hand-rolled equals/hashCode: ByteArray uses identity equality by
    // default, so a data class would make two attachments with the same bytes
    // unequal. Compare the payload by content, mirroring ImageNode.
    override fun equals(other: Any?): Boolean =
        other is PdfAttachment &&
            other.fileName == fileName &&
            other.mimeType == mimeType &&
            other.description == description &&
            other.bytes.contentEquals(bytes)

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PdfAttachment(fileName=$fileName, mimeType=$mimeType, " +
            "size=${bytes.size}, description=$description)"
}
