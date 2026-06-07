package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.metadata.PdfMetadata

/**
 * Turns the collected [KmpPage]s and document [KmpNavigation] into a finished
 * PDF byte stream.
 *
 * The assembler runs in two phases against a [PdfObjectWriter]: first it
 * *allocates* an object number for every object it will write (pages, content
 * streams, shared fonts and graphics states, per-page shadings / images /
 * annotations, the outline tree, the destinations name tree, and the info
 * dictionary) so cross-references can be wired before any body is known; then it
 * serialises each body referencing those numbers. Two-phase allocation is what
 * lets a page reference its annotations, an annotation's GoTo action reference
 * its (possibly later) destination page, and the catalog reference an outline
 * whose items reference their pages — all without forward-declaration gymnastics.
 *
 * Fonts and constant-alpha graphics states are pooled document-wide (a given
 * Helvetica face or alpha value is one object referenced from every page that
 * uses it); shadings and image XObjects are per page since each is referenced
 * exactly once.
 */
internal class KmpDocumentAssembler(
    private val metadata: PdfMetadata,
    private val pages: List<KmpPage>,
    private val navigation: KmpNavigation,
    /**
     * When `true` (the default) page content streams are written with
     * `/FlateDecode`; when `false` they are written verbatim. The switch exists
     * so tests can compare the compressed and uncompressed paths and assert the
     * deflated output round-trips to the same operators.
     */
    private val compressStreams: Boolean = true,
) {

    private val writer = PdfObjectWriter()

    /**
     * Shared font objects, keyed by font reference → the object number the page
     * `/Font` dictionary points at. For a Helvetica face this is the Type1 font
     * dict; for an embedded face it is the Type0 font dict (the head of its
     * five-object CIDFontType2 set).
     */
    private val fontObjects = HashMap<KmpFontRef, Int>()

    /** Embedded-font writers paired with their Type0 base object number, for phase 2. */
    private val embeddedWriters = ArrayList<Pair<KmpEmbeddedFontWriter, Int>>()

    /** Shared ExtGState objects, keyed by rounded alpha → object number. */
    private val alphaObjects = HashMap<Float, Int>()

    /** Page object numbers, by page index — needed to resolve GoTo destinations. */
    private lateinit var pageObjects: IntArray

    fun assemble(): ByteArray {
        val catalogObj = writer.allocate()
        val pagesObj = writer.allocate()

        pageObjects = IntArray(pages.size) { writer.allocate() }
        val contentObjects = IntArray(pages.size) { writer.allocate() }

        // Pool shared font + alpha objects across all pages up front.
        allocateSharedResources()

        // Per-page shading / image / annotation objects.
        val shadingObjects = Array(pages.size) { IntArray(pages[it].resources.shadings.size) { writer.allocate() } }
        val imageObjects = Array(pages.size) { IntArray(pages[it].resources.images.size) { writer.allocate() } }
        val annotObjects = Array(pages.size) { IntArray(pages[it].annotations.size) { writer.allocate() } }

        // Outline + destinations name tree + info dictionary.
        val outlineRootObj = if (navigation.bookmarks.isNotEmpty()) writer.allocate() else null
        val outlineItemObjects = if (outlineRootObj != null) {
            IntArray(navigation.bookmarks.size) { writer.allocate() }
        } else {
            IntArray(0)
        }
        val destsObj = if (navigation.destinations.isNotEmpty()) writer.allocate() else null
        val namesObj = if (destsObj != null) writer.allocate() else null
        val infoObj = if (hasMetadata()) writer.allocate() else null

        // ---- Phase 2: write bodies. ----
        writeSharedResources()
        for (pageIndex in pages.indices) {
            writePage(pageIndex, pagesObj, contentObjects[pageIndex], shadingObjects[pageIndex], imageObjects[pageIndex], annotObjects[pageIndex])
            writeContentStream(pageIndex, contentObjects[pageIndex])
            writeShadings(pageIndex, shadingObjects[pageIndex])
            writeImages(pageIndex, imageObjects[pageIndex])
            writeAnnotations(pageIndex, annotObjects[pageIndex])
        }
        writePagesTree(pagesObj, catalogObj)
        if (outlineRootObj != null) writeOutline(outlineRootObj, outlineItemObjects)
        if (destsObj != null && namesObj != null) writeDestinations(destsObj, namesObj)
        if (infoObj != null) writer.writeObject(infoObj, infoDictionary())
        writeCatalog(catalogObj, pagesObj, outlineRootObj, namesObj)

        return writer.build(rootObject = catalogObj, infoObject = infoObj)
    }

    // -- Shared resources -------------------------------------------------

    private fun allocateSharedResources() {
        for (page in pages) {
            for (ref in page.resources.fonts) {
                fontObjects.getOrPut(ref) {
                    when (ref) {
                        // A Helvetica face is a single Type1 font object.
                        is KmpFontRef.Helvetica -> writer.allocate()
                        // An embedded face needs five consecutive objects; the
                        // first (Type0) is what the page references, the rest are
                        // its descendant/descriptor/file/ToUnicode set.
                        is KmpFontRef.Embedded -> {
                            val embeddedWriter = KmpEmbeddedFontWriter(ref.font)
                            val base = writer.allocate()
                            repeat(embeddedWriter.objectCount - 1) { writer.allocate() }
                            embeddedWriters.add(embeddedWriter to base)
                            base
                        }
                    }
                }
            }
            for (alpha in page.resources.alphaStates) {
                alphaObjects.getOrPut(alpha) { writer.allocate() }
            }
        }
    }

    private fun writeSharedResources() {
        for ((ref, obj) in fontObjects) {
            if (ref is KmpFontRef.Helvetica) {
                // A Standard-14 base font: no embedding, WinAnsi encoding. The
                // viewer supplies the outlines and the AFM metrics we measured.
                writer.writeObject(
                    obj,
                    "<< /Type /Font /Subtype /Type1 /BaseFont /${ref.face.baseFont} /Encoding /WinAnsiEncoding >>",
                )
            }
        }
        // Embedded fonts: subset, compress, and emit the five-object set each.
        for ((embeddedWriter, base) in embeddedWriters) {
            embeddedWriter.write(writer, base)
        }
        for ((alpha, obj) in alphaObjects) {
            val a = PdfSyntax.formatNumber(alpha)
            writer.writeObject(obj, "<< /Type /ExtGState /CA $a /ca $a >>")
        }
    }

    // -- Pages ------------------------------------------------------------

    private fun writePage(
        pageIndex: Int,
        pagesObj: Int,
        contentObj: Int,
        shadingObjs: IntArray,
        imageObjs: IntArray,
        annotObjs: IntArray,
    ) {
        val page = pages[pageIndex]
        val resources = buildResourcesDict(page, shadingObjs, imageObjs)
        val sb = StringBuilder()
        sb.append("<< /Type /Page /Parent $pagesObj 0 R ")
        sb.append("/MediaBox [0 0 ${PdfSyntax.formatNumber(page.width)} ${PdfSyntax.formatNumber(page.height)}] ")
        sb.append("/Contents $contentObj 0 R ")
        sb.append("/Resources $resources")
        if (annotObjs.isNotEmpty()) {
            sb.append(" /Annots [")
            for (i in annotObjs.indices) {
                if (i > 0) sb.append(' ')
                sb.append("${annotObjs[i]} 0 R")
            }
            sb.append("]")
        }
        sb.append(" >>")
        writer.writeObject(pageObjects[pageIndex], sb.toString())
    }

    private fun buildResourcesDict(page: KmpPage, shadingObjs: IntArray, imageObjs: IntArray): String {
        val res = page.resources
        val sb = StringBuilder()
        sb.append("<< /ProcSet [/PDF /Text /ImageB /ImageC /ImageI]")

        if (res.fonts.isNotEmpty()) {
            sb.append(" /Font <<")
            for (i in res.fonts.indices) {
                val obj = fontObjects.getValue(res.fonts[i])
                sb.append(" /F$i $obj 0 R")
            }
            sb.append(" >>")
        }
        if (res.alphaStates.isNotEmpty()) {
            sb.append(" /ExtGState <<")
            for (i in res.alphaStates.indices) {
                val obj = alphaObjects.getValue(res.alphaStates[i])
                sb.append(" /GS$i $obj 0 R")
            }
            sb.append(" >>")
        }
        if (res.shadings.isNotEmpty()) {
            sb.append(" /Shading <<")
            for (i in res.shadings.indices) sb.append(" /Sh$i ${shadingObjs[i]} 0 R")
            sb.append(" >>")
        }
        if (res.images.isNotEmpty()) {
            sb.append(" /XObject <<")
            for (i in res.images.indices) sb.append(" /Im$i ${imageObjs[i]} 0 R")
            sb.append(" >>")
        }
        sb.append(" >>")
        return sb.toString()
    }

    private fun writeContentStream(pageIndex: Int, contentObj: Int) {
        val raw = pages[pageIndex].content.toByteArray()
        if (compressStreams) {
            writer.writeStreamObject(contentObj, "<< /Filter /FlateDecode >>", Deflate.zlibCompress(raw))
        } else {
            writer.writeStreamObject(contentObj, "<< >>", raw)
        }
    }

    private fun writePagesTree(pagesObj: Int, catalogObj: Int) {
        val sb = StringBuilder()
        sb.append("<< /Type /Pages /Kids [")
        for (i in pages.indices) {
            if (i > 0) sb.append(' ')
            sb.append("${pageObjects[i]} 0 R")
        }
        sb.append("] /Count ${pages.size} >>")
        writer.writeObject(pagesObj, sb.toString())
    }

    // -- Shadings + images ------------------------------------------------

    private fun writeShadings(pageIndex: Int, shadingObjs: IntArray) {
        val shadings = pages[pageIndex].resources.shadings
        for (i in shadings.indices) {
            writer.writeObject(shadingObjs[i], KmpShadingWriter.serialize(shadings[i]))
        }
    }

    private fun writeImages(pageIndex: Int, imageObjs: IntArray) {
        val images = pages[pageIndex].resources.images
        for (i in images.indices) {
            writer.writeStreamObject(imageObjs[i], images[i].dictionaryEntries, images[i].stream)
        }
    }

    // -- Annotations ------------------------------------------------------

    private fun writeAnnotations(pageIndex: Int, annotObjs: IntArray) {
        val annots = pages[pageIndex].annotations
        for (i in annots.indices) {
            val a = annots[i]
            val rect = "[${PdfSyntax.formatNumber(a.llx)} ${PdfSyntax.formatNumber(a.lly)} " +
                "${PdfSyntax.formatNumber(a.urx)} ${PdfSyntax.formatNumber(a.ury)}]"
            val action = resolveAction(a)
            val body = buildString {
                append("<< /Type /Annot /Subtype /Link /Rect $rect /Border [0 0 0]")
                append(" /P ${pageObjects[pageIndex]} 0 R")
                if (action != null) append(" $action")
                append(" >>")
            }
            writer.writeObject(annotObjs[i], body)
        }
    }

    /**
     * The `/A` action fragment for an annotation, or `null` for an inert link.
     * A URL produces a `/URI` action; an internal destination name produces a
     * `GoTo` resolved against the document destination table — an unregistered
     * name yields `null` so the link stays present but does nothing.
     */
    private fun resolveAction(a: KmpAnnotation): String? = when {
        a.url != null -> "/A << /S /URI /URI ${PdfSyntax.pdfString(a.url)} >>"
        a.destinationName != null -> {
            val dest = navigation.destinations[a.destinationName]
            if (dest == null) {
                null
            } else {
                val pageObj = pageObjects[dest.pageIndex]
                "/A << /S /GoTo /D [$pageObj 0 R /XYZ 0 ${PdfSyntax.formatNumber(dest.top)} null] >>"
            }
        }
        else -> null
    }

    // -- Outline ----------------------------------------------------------

    /**
     * Writes the outline tree. Entries nest by [KmpBookmark.level] exactly like
     * the other backends: each item attaches under the most recent item with a
     * strictly smaller level, and siblings are chained Prev/Next.
     */
    private fun writeOutline(rootObj: Int, itemObjs: IntArray) {
        val bookmarks = navigation.bookmarks

        // Build parent/child structure via a level stack.
        val parents = IntArray(bookmarks.size) { -1 } // index into bookmarks, -1 = top-level
        val children = Array(bookmarks.size) { ArrayList<Int>() }
        val topLevel = ArrayList<Int>()
        val stack = ArrayDeque<Int>() // indices into bookmarks
        for (i in bookmarks.indices) {
            while (stack.isNotEmpty() && bookmarks[stack.last()].level >= bookmarks[i].level) {
                stack.removeLast()
            }
            val parent = stack.lastOrNull()
            if (parent == null) {
                topLevel.add(i)
            } else {
                parents[i] = parent
                children[parent].add(i)
            }
            stack.addLast(i)
        }

        fun siblingPrev(siblings: List<Int>, pos: Int): Int? = siblings.getOrNull(pos - 1)
        fun siblingNext(siblings: List<Int>, pos: Int): Int? = siblings.getOrNull(pos + 1)

        // Map each bookmark index to its sibling list + position for chaining.
        val siblingListOf = HashMap<Int, Pair<List<Int>, Int>>()
        topLevel.forEachIndexed { pos, idx -> siblingListOf[idx] = topLevel to pos }
        for (i in bookmarks.indices) {
            children[i].forEachIndexed { pos, idx -> siblingListOf[idx] = children[i] to pos }
        }

        for (i in bookmarks.indices) {
            val bm = bookmarks[i]
            val pageObj = pageObjects[bm.pageIndex]
            val (siblings, pos) = siblingListOf.getValue(i)
            val parentObj = if (parents[i] >= 0) itemObjs[parents[i]] else rootObj
            val body = buildString {
                append("<< /Title ${PdfSyntax.pdfString(bm.title)}")
                append(" /Parent $parentObj 0 R")
                siblingPrev(siblings, pos)?.let { append(" /Prev ${itemObjs[it]} 0 R") }
                siblingNext(siblings, pos)?.let { append(" /Next ${itemObjs[it]} 0 R") }
                if (children[i].isNotEmpty()) {
                    append(" /First ${itemObjs[children[i].first()]} 0 R")
                    append(" /Last ${itemObjs[children[i].last()]} 0 R")
                    append(" /Count ${children[i].size}")
                }
                append(" /Dest [$pageObj 0 R /XYZ 0 ${PdfSyntax.formatNumber(bm.top)} null] >>")
            }
            writer.writeObject(itemObjs[i], body)
        }

        val rootBody = buildString {
            append("<< /Type /Outlines")
            if (topLevel.isNotEmpty()) {
                append(" /First ${itemObjs[topLevel.first()]} 0 R")
                append(" /Last ${itemObjs[topLevel.last()]} 0 R")
            }
            append(" /Count ${topLevel.size} >>")
        }
        writer.writeObject(rootObj, rootBody)
    }

    // -- Destinations name tree -------------------------------------------

    /**
     * Writes the `/Dests` name tree (sorted by name, as the spec requires) and
     * the `/Names` dictionary that points at it. Named destinations referenced
     * by GoTo links use direct `/D` arrays, but a `/Dests` tree additionally lets
     * external tools and `#nameddest=` URLs resolve them.
     */
    private fun writeDestinations(destsObj: Int, namesObj: Int) {
        val sorted = navigation.destinations.entries.sortedBy { it.key }
        val names = StringBuilder()
        names.append("<< /Names [")
        for ((index, entry) in sorted.withIndex()) {
            if (index > 0) names.append(' ')
            val dest = entry.value
            val pageObj = pageObjects[dest.pageIndex]
            names.append(PdfSyntax.pdfString(entry.key))
            names.append(" [$pageObj 0 R /XYZ 0 ${PdfSyntax.formatNumber(dest.top)} null]")
        }
        names.append("] >>")
        writer.writeObject(destsObj, names.toString())
        writer.writeObject(namesObj, "<< /Dests $destsObj 0 R >>")
    }

    // -- Catalog + info ---------------------------------------------------

    private fun writeCatalog(catalogObj: Int, pagesObj: Int, outlineObj: Int?, namesObj: Int?) {
        val sb = StringBuilder()
        sb.append("<< /Type /Catalog /Pages $pagesObj 0 R")
        if (outlineObj != null) sb.append(" /Outlines $outlineObj 0 R")
        if (namesObj != null) sb.append(" /Names $namesObj 0 R")
        metadata.language?.let { sb.append(" /Lang ${PdfSyntax.pdfString(it)}") }
        sb.append(" >>")
        writer.writeObject(catalogObj, sb.toString())
    }

    private fun hasMetadata(): Boolean = metadata.title != null || metadata.author != null ||
        metadata.subject != null || metadata.keywords != null ||
        metadata.creator != null || metadata.producer != null

    private fun infoDictionary(): String = buildString {
        append("<<")
        metadata.title?.let { append(" /Title ${PdfSyntax.pdfString(it)}") }
        metadata.author?.let { append(" /Author ${PdfSyntax.pdfString(it)}") }
        metadata.subject?.let { append(" /Subject ${PdfSyntax.pdfString(it)}") }
        metadata.keywords?.let { append(" /Keywords ${PdfSyntax.pdfString(it)}") }
        metadata.creator?.let { append(" /Creator ${PdfSyntax.pdfString(it)}") }
        metadata.producer?.let { append(" /Producer ${PdfSyntax.pdfString(it)}") }
        append(" >>")
    }
}
