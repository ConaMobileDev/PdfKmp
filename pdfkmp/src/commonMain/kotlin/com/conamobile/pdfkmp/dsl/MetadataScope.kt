package com.conamobile.pdfkmp.dsl

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

    internal fun build(): PdfMetadata = PdfMetadata(
        title = title,
        author = author,
        subject = subject,
        keywords = keywords,
        creator = creator,
    )
}
