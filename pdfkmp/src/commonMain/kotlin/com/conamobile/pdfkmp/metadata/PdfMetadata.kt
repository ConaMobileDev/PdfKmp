package com.conamobile.pdfkmp.metadata

/**
 * Document metadata written into the PDF info dictionary. All fields are
 * optional; renderers omit empty values.
 */
public data class PdfMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = "PdfKmp",
) {
    public companion object {
        public val Empty: PdfMetadata = PdfMetadata()
    }
}
