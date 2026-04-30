package com.conamobile.pdfkmp.node

/**
 * Wraps [child] in a clickable hyperlink annotation pointing at [url].
 *
 * The wrapper does not change layout or visual appearance; it only
 * registers a rectangle on the resulting PDF page that PDF readers
 * dispatch to the URL when clicked. Visual styling (e.g. blue
 * underlined text) is the caller's responsibility — the wrapper exists
 * to attach the URL to whatever content the caller renders inside it.
 *
 * On iOS the link is recorded via `UIGraphicsSetPDFContextURLForRect`
 * which produces a real hyperlink annotation in the PDF stream. On
 * Android the underlying `PdfDocument` API does not expose annotation
 * APIs so the rectangle is currently a no-op there — text styling
 * conveys the link visually but clicks fall through.
 */
public data class LinkNode(
    val url: String,
    val child: PdfNode,
) : PdfNode
