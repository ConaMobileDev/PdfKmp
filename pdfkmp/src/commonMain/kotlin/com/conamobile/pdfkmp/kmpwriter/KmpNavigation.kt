package com.conamobile.pdfkmp.kmpwriter

/**
 * Document-level navigation state — named destinations and outline bookmarks —
 * collected across pages while they draw and resolved in one pass at document
 * finish.
 *
 * Destinations and link targets are deferred because they cross page boundaries:
 * a table of contents on page 1 links forward to destinations only registered
 * when later pages render, and outline entries reference whatever page they
 * point at. Holding everything here until every page (and so every page object
 * number) is known is what makes forward references work; a link whose
 * destination name never appears stays present but inert.
 *
 * Link *annotations* themselves live on their owning [KmpPage] (they attach to a
 * specific page object); only the cross-page lookup tables live here.
 */
internal class KmpNavigation {

    /** Named destinations keyed by name, each carrying its page index + flipped top. */
    val destinations: MutableMap<String, KmpDestination> = HashMap()

    /** Outline entries in document order; nested by [KmpBookmark.level] at finish. */
    val bookmarks: MutableList<KmpBookmark> = ArrayList()
}
