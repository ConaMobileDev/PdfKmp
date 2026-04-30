package com.conamobile.pdfkmp.dsl

/**
 * Marks the receiver scopes of the PDF DSL so that nested scopes don't
 * leak outer-scope members into inner blocks. Without this annotation a call
 * like `column { text { row { ... } } }` would let `text` and `row` see each
 * other's properties; with it the compiler rejects such cross-scope access.
 */
@DslMarker
public annotation class PdfDsl
