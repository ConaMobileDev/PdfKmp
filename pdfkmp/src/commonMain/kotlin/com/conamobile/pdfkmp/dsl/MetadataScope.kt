package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.metadata.PdfEncryption
import com.conamobile.pdfkmp.metadata.PdfMetadata

/**
 * Receiver of `metadata { ... }`.
 *
 * Each property maps directly to a field in the PDF info dictionary. Leaving
 * a property `null` (the default) omits it from the output.
 */
@PdfDsl
public class MetadataScope internal constructor() {

    /** Title of the document. */
    public var title: String? = null

    /** Author or organization that produced the document. */
    public var author: String? = null

    /** Short summary of the document's subject. */
    public var subject: String? = null

    /** Comma-separated keywords for search/indexing. */
    public var keywords: String? = null

    /**
     * Application that created the source content. Defaults to `null`; the
     * library still records itself as the producer.
     */
    public var creator: String? = null

    /**
     * Best-effort PDF/A-2b conformance. See [PdfMetadata.pdfACompliance] for
     * the honest scope and the per-platform support matrix (JVM/Desktop only).
     */
    public var pdfACompliance: Boolean = false

    /**
     * Document language as a BCP-47 tag (e.g. `"en"`). Written to the catalog
     * `/Lang` entry by the JVM/Desktop backend. See [PdfMetadata.language].
     */
    public var language: String? = null

    internal fun build(): PdfMetadata = PdfMetadata(
        title = title,
        author = author,
        subject = subject,
        keywords = keywords,
        creator = creator,
        pdfACompliance = pdfACompliance,
        language = language,
    )
}

/**
 * Receiver of `encryption { ... }`.
 *
 * Mirrors the field-by-field builder style of [MetadataScope]: set the owner
 * password (and optionally a user password) plus the permission flags, and the
 * builder produces a [PdfEncryption]. See [PdfEncryption] for the per-platform
 * support matrix and the meaning of each flag.
 */
@PdfDsl
public class EncryptionScope internal constructor() {

    /**
     * Password granting full, unrestricted access. Must be set to a non-empty
     * value — [build] throws otherwise, because an empty owner password offers
     * no protection.
     */
    public var ownerPassword: String = ""

    /**
     * Password required to open the document. Empty (the default) means the
     * document opens without a prompt while the permission flags still apply.
     */
    public var userPassword: String = ""

    /** Whether the document may be printed. Defaults to `true`. */
    public var allowPrinting: Boolean = true

    /**
     * Whether text/graphics may be extracted (copy/paste, accessibility
     * readers). Defaults to `true`.
     */
    public var allowCopying: Boolean = true

    /**
     * Whether the document content may be modified. Defaults to `false`.
     * Ignored on iOS (no corresponding Core Graphics flag).
     */
    public var allowModification: Boolean = false

    internal fun build(): PdfEncryption {
        require(ownerPassword.isNotEmpty()) {
            "encryption { } requires a non-empty ownerPassword"
        }
        return PdfEncryption(
            ownerPassword = ownerPassword,
            userPassword = userPassword,
            allowPrinting = allowPrinting,
            allowCopying = allowCopying,
            allowModification = allowModification,
        )
    }
}
