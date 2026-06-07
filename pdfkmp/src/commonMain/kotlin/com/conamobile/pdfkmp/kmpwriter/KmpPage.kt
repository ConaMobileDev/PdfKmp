package com.conamobile.pdfkmp.kmpwriter

/**
 * The accumulated draw output of one page, built by a [KmpPdfCanvas] and
 * serialised by [KmpPdfDriver] at document finish.
 *
 * A page is split into a content stream (the ASCII operator text the canvas
 * appends to as it draws) plus the resources that stream references by name —
 * fonts, alpha graphics states, gradient shadings, and image XObjects. The
 * driver turns each of these into indirect objects and wires a `/Resources`
 * dictionary on the page object. Navigation primitives (links, named
 * destinations, bookmarks) are collected at the document level instead, since
 * they cross page boundaries, so they are not held here.
 *
 * Coordinates inside [content] are already in PDF bottom-left space — the canvas
 * flips Y per operator as it writes, mirroring the JVM backend — so the
 * serialiser treats the content stream as opaque.
 */
internal class KmpPage(
    /** Page width in PDF points. */
    val width: Float,
    /** Page height in PDF points; the canvas flips Y against this. */
    val height: Float,
) {
    /** The page's content-stream operators, appended to as the canvas draws. */
    val content: ByteBuffer = ByteBuffer(initialCapacity = 16 * 1024)

    /** Resources referenced by [content], collected as named objects are needed. */
    val resources: KmpResources = KmpResources()

    /**
     * Link annotations on this page awaiting serialisation. Each carries either
     * a URI target or an internal-destination name resolved at finish. Stored as
     * a small descriptor rather than a serialised dictionary because GoTo links
     * need the destination's page object number, which isn't known until every
     * page has been allocated.
     */
    val annotations: MutableList<KmpAnnotation> = ArrayList()
}

/**
 * A pending link annotation. Exactly one of [url] / [destinationName] is set:
 * a URL produces a `/URI` action, a destination name a `GoTo` action resolved
 * against the document's named-destination table at finish (forward references
 * are fine; an unresolved name leaves the annotation inert).
 *
 * Rectangle coordinates are already flipped into PDF bottom-left space.
 */
internal class KmpAnnotation(
    val llx: Float,
    val lly: Float,
    val urx: Float,
    val ury: Float,
    val url: String? = null,
    val destinationName: String? = null,
)

/**
 * A named jump target registered on a page. [pageIndex] identifies the page and
 * [top] is the destination's vertical position already flipped into PDF
 * bottom-left space, ready to drop into an `/XYZ` destination array.
 */
internal class KmpDestination(
    val pageIndex: Int,
    val top: Float,
)

/**
 * One outline (bookmark sidebar) entry. [level] nests entries — `0` is a
 * top-level chapter, `1` a section under the previous level-0 entry — matching
 * [com.conamobile.pdfkmp.render.PdfCanvas.bookmark]. [pageIndex] and [top]
 * (flipped to PDF space) locate the jump target.
 */
internal class KmpBookmark(
    val title: String,
    val level: Int,
    val pageIndex: Int,
    val top: Float,
)
