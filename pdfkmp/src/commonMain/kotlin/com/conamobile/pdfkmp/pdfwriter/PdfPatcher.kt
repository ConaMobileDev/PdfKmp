package com.conamobile.pdfkmp.pdfwriter

import com.conamobile.pdfkmp.metadata.PdfMetadata

/**
 * One hyperlink rectangle collected during rendering.
 *
 * Coordinates use PdfKmp's canvas convention — a top-left origin with Y
 * growing downward, in PDF points. The patcher flips Y into the PDF's
 * bottom-left space when it writes the annotation `/Rect`.
 *
 * Exactly one of [url] / [anchor] is set: [url] produces a `/URI` action,
 * [anchor] a `GoTo` action targeting the named destination registered under
 * the same name.
 */
internal data class PdfLink(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val url: String? = null,
    val anchor: String? = null,
)

/**
 * A named jump target registered on a page. Internal links ([PdfLink.anchor])
 * and bookmarks resolve to one of these by [name]. [y] is top-left-origin.
 */
internal data class PdfDestination(
    val name: String,
    val pageIndex: Int,
    val y: Float,
)

/**
 * An outline (bookmark sidebar) entry. [level] nests entries: `0` is a
 * top-level chapter, `1` a section under the previous level-0 entry, and so
 * on — matching [com.conamobile.pdfkmp.render.PdfCanvas.bookmark].
 */
internal data class PdfBookmark(
    val title: String,
    val level: Int,
    val pageIndex: Int,
    val y: Float,
)

/**
 * Everything the Android canvas collects that `android.graphics.pdf.PdfDocument`
 * cannot itself emit — hyperlinks, named destinations, and outline entries.
 *
 * The driver fills this while pages render and hands it, together with the
 * document [PdfMetadata], to [PdfPatcher.apply] once the raw bytes are ready.
 */
internal class PdfNavigation {
    val links: MutableList<PdfLink> = ArrayList()
    val destinations: MutableList<PdfDestination> = ArrayList()
    val bookmarks: MutableList<PdfBookmark> = ArrayList()

    /** True when there is nothing to patch in beyond metadata. */
    fun isEmpty(): Boolean =
        links.isEmpty() && destinations.isEmpty() && bookmarks.isEmpty()
}

/**
 * Appends a PDF 1.7 incremental update (§7.5.6) to an already-finished PDF,
 * adding the document info dictionary, link annotations, named destinations,
 * and an outline tree that the underlying generator could not write itself.
 *
 * This exists because `android.graphics.pdf.PdfDocument` exposes no API for
 * the info dictionary or for any interactive annotation/navigation feature.
 * Rather than swap the whole Android backend for a hand-rolled encoder, we
 * post-process its (simple, predictable) output: an incremental update keeps
 * the original bytes byte-for-byte intact and only *adds* objects, which is
 * exactly what the spec's incremental-update mechanism is for.
 *
 * The parser is deliberately minimal but defensive: any structure it does not
 * recognise causes [apply] to return the original bytes unchanged, so the
 * worst case is today's behaviour (the feature silently no-ops) rather than a
 * corrupted file or a thrown exception.
 */
internal object PdfPatcher {

    /**
     * Returns [original] with an incremental update appended that writes
     * [metadata] into the info dictionary and realises every link, destination,
     * and bookmark in [navigation]. Returns [original] unchanged when there is
     * nothing to add or when the input cannot be parsed safely.
     */
    fun apply(
        original: ByteArray,
        metadata: PdfMetadata,
        navigation: PdfNavigation,
    ): ByteArray {
        if (!hasMetadata(metadata) && navigation.isEmpty()) return original
        return try {
            patch(original, metadata, navigation) ?: original
        } catch (_: Throwable) {
            // The contract is "never make things worse": any parsing or
            // encoding surprise falls back to the unmodified document.
            original
        }
    }

    /** True when at least one info-dictionary field is set. */
    private fun hasMetadata(m: PdfMetadata): Boolean =
        m.title != null || m.author != null || m.subject != null ||
            m.keywords != null || m.creator != null || m.producer != null

    /**
     * Performs the actual incremental update, or returns `null` to signal that
     * the caller should fall back to the original bytes (parse failure).
     */
    private fun patch(
        original: ByteArray,
        metadata: PdfMetadata,
        navigation: PdfNavigation,
    ): ByteArray? {
        val text = original.decodeToString()

        val prevStartXref = lastStartXref(text) ?: return null
        val trailer = parseTrailer(text) ?: return null
        val rootRef = trailer.rootRef
        var nextObj = trailer.size

        val pages = resolvePages(text, rootRef) ?: return null
        if (pages.isEmpty()) return null

        // Group navigation by the page object it attaches to. A page index that
        // points outside the actual page list is dropped rather than fatal.
        val pageObjForIndex: (Int) -> Int? = { idx -> pages.getOrNull(idx)?.objNumber }

        // 1. Allocate object numbers for every new/updated object up front so we
        //    can cross-reference them (annotations reference their page's new id,
        //    GoTo links reference destination pages, etc.).
        val updated = LinkedHashMap<Int, String>() // objNumber -> serialized body

        fun alloc(): Int = nextObj++

        // --- Build annotation objects, grouped per page object number. ---
        val annotsByPage = LinkedHashMap<Int, MutableList<Int>>()
        val newObjectBodies = LinkedHashMap<Int, String>()

        // Resolve a destination name to (page object number, top in bottom-left).
        val destByName = HashMap<String, PdfDestination>()
        for (d in navigation.destinations) destByName[d.name] = d

        for (link in navigation.links) {
            val pageObj = pageObjForIndex(link.pageIndex) ?: continue
            val page = pages[link.pageIndex]
            val rect = annotRect(link.x, link.y, link.width, link.height, page.height)
                ?: continue
            // Resolve the action BEFORE reserving an object number: an internal
            // link whose anchor was never registered (an inert link, today's
            // intended behaviour) must not burn an id, or it would leave a hole
            // below the trailer /Size with no object/xref entry to fill it.
            val action = when {
                link.url != null -> uriAction(link.url)
                link.anchor != null -> {
                    val dest = destByName[link.anchor] ?: continue
                    val destPage = pageObjForIndex(dest.pageIndex) ?: continue
                    val destHeight = pages[dest.pageIndex].height
                    goToAction(destPage, top = destHeight - dest.y)
                }
                else -> null
            } ?: continue
            val annotObj = alloc()
            newObjectBodies[annotObj] = linkAnnotation(rect, pageObj, action)
            annotsByPage.getOrPut(pageObj) { ArrayList() }.add(annotObj)
        }

        // --- Build the outline tree from bookmarks. ---
        var outlineRootObj: Int? = null
        if (navigation.bookmarks.isNotEmpty()) {
            outlineRootObj = buildOutline(
                bookmarks = navigation.bookmarks,
                pages = pages,
                alloc = ::alloc,
                emit = { obj, body -> newObjectBodies[obj] = body },
            )
        }

        // --- Build the info dictionary. ---
        var infoObj: Int? = null
        if (hasMetadata(metadata)) {
            infoObj = alloc()
            newObjectBodies[infoObj] = infoDictionary(metadata)
        }

        // --- Rewrite each touched page object with an /Annots array. ---
        for ((pageObj, annots) in annotsByPage) {
            val page = pages.first { it.objNumber == pageObj }
            updated[pageObj] = rewritePageWithAnnots(page, annots)
        }

        // --- Rewrite the catalog if we added an outline. ---
        if (outlineRootObj != null) {
            updated[rootRef] = rewriteCatalogWithOutline(text, rootRef, outlineRootObj)
        }

        if (newObjectBodies.isEmpty() && updated.isEmpty() && infoObj == null) return null

        // 2. Serialise the incremental section. PDF offsets are absolute from the
        //    start of the file, so new objects begin right after the original
        //    bytes. We append a leading EOL to separate the update from the prior
        //    %%EOF, per the incremental-update convention.
        val out = StringBuilder()
        out.append('\n')
        // The final file is original (N bytes) ++ out, and out is pure ASCII, so
        // the character at out[i] lands at byte index N + i — including the
        // leading EOL at out[0]. Recording an offset as baseOffset + out.length
        // therefore captures the exact byte index when baseOffset == N. (Adding
        // +1 for the EOL here would double-count it: out.length already includes
        // that newline, so every offset would come out one byte too high.)
        val baseOffset = original.size

        val offsets = HashMap<Int, Int>()

        fun appendObject(obj: Int, body: String) {
            offsets[obj] = baseOffset + out.length
            out.append(obj).append(" 0 obj\n")
            out.append(body)
            if (!body.endsWith("\n")) out.append('\n')
            out.append("endobj\n")
        }

        // Order is irrelevant for correctness but keep it stable & readable.
        // The info dictionary is one of newObjectBodies, so it is emitted here too.
        for ((obj, body) in newObjectBodies) appendObject(obj, body)
        for ((obj, body) in updated) appendObject(obj, body)

        // 3. Cross-reference section for exactly the objects we wrote, then the
        //    trailer with /Prev chaining back to the original xref.
        val xrefOffset = baseOffset + out.length
        out.append(buildXref(offsets))
        out.append(
            buildTrailer(
                size = nextObj,
                rootRef = rootRef,
                infoObj = infoObj,
                prevStartXref = prevStartXref,
            ),
        )
        out.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")

        return original + out.toString().encodeToByteArray()
    }

    // ---------------------------------------------------------------------
    // Object body builders
    // ---------------------------------------------------------------------

    /**
     * A `/Link` annotation dictionary covering [rect] on page object [pageObj]
     * with [action] (a fully-serialised `/A << … >>` fragment). The border is
     * zeroed so the visual "this is a link" cue stays the surrounding content,
     * matching the iOS / JVM backends.
     */
    private fun linkAnnotation(rect: PdfRect, pageObj: Int, action: String): String =
        buildString {
            append("<< /Type /Annot /Subtype /Link ")
            append("/Rect [").append(num(rect.x0)).append(' ').append(num(rect.y0))
                .append(' ').append(num(rect.x1)).append(' ').append(num(rect.y1)).append("] ")
            append("/Border [0 0 0] ")
            append("/P ").append(pageObj).append(" 0 R ")
            append(action)
            append(" >>")
        }

    private fun uriAction(url: String): String =
        "/A << /S /URI /URI ${pdfString(url)} >>"

    /** GoTo action targeting [pageObj] at vertical position [top] (bottom-left). */
    private fun goToAction(pageObj: Int, top: Float): String =
        "/A << /S /GoTo /D [$pageObj 0 R /XYZ 0 ${num(top)} null] >>"

    private fun infoDictionary(m: PdfMetadata): String = buildString {
        append("<<")
        m.title?.let { append(" /Title ").append(pdfString(it)) }
        m.author?.let { append(" /Author ").append(pdfString(it)) }
        m.subject?.let { append(" /Subject ").append(pdfString(it)) }
        m.keywords?.let { append(" /Keywords ").append(pdfString(it)) }
        m.creator?.let { append(" /Creator ").append(pdfString(it)) }
        m.producer?.let { append(" /Producer ").append(pdfString(it)) }
        append(" >>")
    }

    /**
     * Builds the outline tree under a new `/Outlines` root object and returns
     * its object number. Entries nest by [PdfBookmark.level] exactly like the
     * JVM backend: each item attaches under the most recent item with a
     * strictly smaller level.
     */
    private fun buildOutline(
        bookmarks: List<PdfBookmark>,
        pages: List<PdfPage>,
        alloc: () -> Int,
        emit: (Int, String) -> Unit,
    ): Int? {
        val rootObj = alloc()
        // Pre-allocate one object number per bookmark so we can wire Parent /
        // Prev / Next / First / Last references before serialising.
        data class Node(
            val obj: Int,
            val bm: PdfBookmark,
            var parent: Int = rootObj,
            var prev: Int? = null,
            var next: Int? = null,
            val children: MutableList<Int> = ArrayList(),
        )

        val nodes = bookmarks.map { Node(alloc(), it) }
        val byObj = nodes.associateBy { it.obj }

        // Build parent/child links via a level stack (mirrors JvmNavigation).
        val topLevel = ArrayList<Int>()
        val stack = ArrayDeque<Node>()
        for (node in nodes) {
            while (stack.isNotEmpty() && stack.last().bm.level >= node.bm.level) {
                stack.removeLast()
            }
            val parent = stack.lastOrNull()
            if (parent == null) {
                node.parent = rootObj
                topLevel.add(node.obj)
            } else {
                node.parent = parent.obj
                parent.children.add(node.obj)
            }
            stack.addLast(node)
        }

        // Sibling chaining within each parent's child list.
        fun chain(siblings: List<Int>) {
            for (i in siblings.indices) {
                val n = byObj.getValue(siblings[i])
                n.prev = siblings.getOrNull(i - 1)
                n.next = siblings.getOrNull(i + 1)
            }
        }
        chain(topLevel)
        for (node in nodes) if (node.children.isNotEmpty()) chain(node.children)

        // Serialise each outline item.
        var count = 0
        for (node in nodes) {
            val page = pages.getOrNull(node.bm.pageIndex) ?: continue
            val top = page.height - node.bm.y
            val pageObj = page.objNumber
            val body = buildString {
                append("<< /Title ").append(pdfString(node.bm.title)).append(' ')
                append("/Parent ").append(node.parent).append(" 0 R ")
                node.prev?.let { append("/Prev ").append(it).append(" 0 R ") }
                node.next?.let { append("/Next ").append(it).append(" 0 R ") }
                if (node.children.isNotEmpty()) {
                    append("/First ").append(node.children.first()).append(" 0 R ")
                    append("/Last ").append(node.children.last()).append(" 0 R ")
                    append("/Count ").append(node.children.size).append(' ')
                }
                append("/Dest [").append(pageObj).append(" 0 R /XYZ 0 ")
                    .append(num(top)).append(" null] >>")
            }
            emit(node.obj, body)
            count++
        }
        if (count == 0) return null

        val rootBody = buildString {
            append("<< /Type /Outlines ")
            if (topLevel.isNotEmpty()) {
                append("/First ").append(topLevel.first()).append(" 0 R ")
                append("/Last ").append(topLevel.last()).append(" 0 R ")
            }
            append("/Count ").append(topLevel.size).append(" >>")
        }
        emit(rootObj, rootBody)
        return rootObj
    }

    /**
     * Returns [page]'s original dictionary text with an `/Annots` array added
     * (or merged with an existing one) referencing [annots].
     */
    private fun rewritePageWithAnnots(page: PdfPage, annots: List<Int>): String {
        val refs = annots.joinToString(" ") { "$it 0 R" }
        val dict = page.rawDict
        val existing = ANNOTS_REGEX.find(dict)
        return if (existing != null) {
            // Append our refs to the existing array. Android's output has no
            // annotations, but merging keeps us correct if that ever changes.
            val inner = existing.groupValues[1].trim()
            val merged = if (inner.isEmpty()) refs else "$inner $refs"
            dict.replaceRange(existing.range, "/Annots [$merged]")
        } else {
            // Insert just before the closing >> of the page dictionary.
            val close = dict.lastIndexOf(">>")
            if (close < 0) dict else dict.substring(0, close) + "/Annots [$refs] " + dict.substring(close)
        }
    }

    /**
     * Returns the catalog dictionary text with `/Outlines <obj> 0 R` added.
     * Reads the catalog body fresh from [text] so we modify the authoritative
     * source rather than a re-serialised copy.
     */
    private fun rewriteCatalogWithOutline(text: String, rootRef: Int, outlineObj: Int): String {
        val dict = readObjectDict(text, rootRef) ?: error("catalog object $rootRef not found")
        // If a (broken) /Outlines already exists we replace it; otherwise insert.
        val existing = OUTLINES_REGEX.find(dict)
        return if (existing != null) {
            dict.replaceRange(existing.range, "/Outlines $outlineObj 0 R")
        } else {
            val close = dict.lastIndexOf(">>")
            if (close < 0) dict else dict.substring(0, close) + "/Outlines $outlineObj 0 R " + dict.substring(close)
        }
    }

    // ---------------------------------------------------------------------
    // Cross-reference + trailer
    // ---------------------------------------------------------------------

    /**
     * A classic (non-stream) xref section listing only the objects we wrote.
     * Each contiguous run of object numbers becomes one subsection so the
     * table stays compact and valid even when ids are sparse.
     */
    private fun buildXref(offsets: Map<Int, Int>): String {
        val sorted = offsets.keys.sorted()
        val sb = StringBuilder("xref\n")
        var i = 0
        while (i < sorted.size) {
            val start = sorted[i]
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            val count = j - i + 1
            sb.append(start).append(' ').append(count).append('\n')
            for (k in i..j) {
                val off = offsets.getValue(sorted[k])
                // 10-digit offset, 5-digit generation, 'n' in-use, 2-byte EOL.
                sb.append(off.toString().padStart(10, '0'))
                    .append(" 00000 n \n")
            }
            i = j + 1
        }
        return sb.toString()
    }

    private fun buildTrailer(
        size: Int,
        rootRef: Int,
        infoObj: Int?,
        prevStartXref: Int,
    ): String = buildString {
        append("trailer\n<< /Size ").append(size)
        append(" /Root ").append(rootRef).append(" 0 R")
        if (infoObj != null) append(" /Info ").append(infoObj).append(" 0 R")
        append(" /Prev ").append(prevStartXref)
        append(" >>\n")
    }

    // ---------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------

    /** Byte offset of the most recent xref, from the trailing `startxref`. */
    private fun lastStartXref(text: String): Int? {
        val idx = text.lastIndexOf("startxref")
        if (idx < 0) return null
        val after = text.substring(idx + "startxref".length)
        val match = INT_REGEX.find(after) ?: return null
        return match.value.toIntOrNull()
    }

    private data class Trailer(val rootRef: Int, val size: Int)

    /**
     * Reads `/Root` and `/Size` from the trailer. Handles both a classic
     * `trailer << … >>` dictionary and a cross-reference stream (`/Type /XRef`),
     * which is enough for Android's output and PdfBox-generated fixtures alike.
     */
    private fun parseTrailer(text: String): Trailer? {
        // Prefer the last classic trailer dictionary.
        val trailerIdx = text.lastIndexOf("trailer")
        val dict = if (trailerIdx >= 0) {
            val open = text.indexOf("<<", trailerIdx)
            if (open >= 0) extractDict(text, open) else null
        } else {
            null
        }
        val source = dict ?: lastXrefStreamDict(text) ?: return null
        val root = ROOT_REGEX.find(source)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val size = SIZE_REGEX.find(source)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return Trailer(root, size)
    }

    /** The dictionary of the last cross-reference stream object, if any. */
    private fun lastXrefStreamDict(text: String): String? {
        var searchFrom = text.length
        while (true) {
            val typeIdx = text.lastIndexOf("/Type /XRef", searchFrom)
            if (typeIdx < 0) return null
            val open = text.lastIndexOf("<<", typeIdx)
            if (open < 0) return null
            val d = extractDict(text, open)
            if (d != null && ROOT_REGEX.containsMatchIn(d)) return d
            searchFrom = open - 1
        }
    }

    private data class PdfPage(
        val objNumber: Int,
        val height: Float,
        val rawDict: String,
    )

    /**
     * Walks `/Root → /Pages → /Kids` and returns every leaf page in document
     * order with its object number and MediaBox height. Handles nested page-tree
     * nodes (a `/Kids` entry that is itself a `/Pages` node). Inherits MediaBox
     * from an ancestor node when a page omits its own.
     */
    private fun resolvePages(text: String, rootRef: Int): List<PdfPage>? {
        val catalog = readObjectDict(text, rootRef) ?: return null
        val pagesRef = REF_REGEX.find(afterKey(catalog, "/Pages") ?: return null)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val result = ArrayList<PdfPage>()
        val seen = HashSet<Int>()

        fun walk(nodeRef: Int, inheritedHeight: Float?) {
            if (!seen.add(nodeRef)) return // guard against cycles
            val dict = readObjectDict(text, nodeRef) ?: return
            val height = mediaBoxHeight(dict) ?: inheritedHeight
            val isPage = TYPE_PAGE_REGEX.containsMatchIn(dict)
            val kids = afterKey(dict, "/Kids")
            if (!isPage && kids != null) {
                val arr = extractArray(kids) ?: return
                for (m in REF_REGEX.findAll(arr)) {
                    m.groupValues[1].toIntOrNull()?.let { walk(it, height) }
                }
            } else {
                // Treat as a leaf page. Height falls back to A4 if absent so a
                // missing MediaBox never breaks Y-flipping (rare for Android).
                result.add(PdfPage(nodeRef, height ?: DEFAULT_PAGE_HEIGHT, dict))
            }
        }
        walk(pagesRef, null)
        return result
    }

    /** MediaBox height = ury - lly from a `/MediaBox [llx lly urx ury]` entry. */
    private fun mediaBoxHeight(dict: String): Float? {
        val box = afterKey(dict, "/MediaBox") ?: return null
        val arr = extractArray(box) ?: return null
        val nums = FLOAT_REGEX.findAll(arr).map { it.value.toFloat() }.toList()
        if (nums.size < 4) return null
        return nums[3] - nums[1]
    }

    /**
     * Returns the dictionary body (`<< … >>`, braces included) of object
     * [objNumber], located via an `N 0 obj` definition. Searches from the end so
     * the latest definition (an incremental update would win) is preferred.
     */
    private fun readObjectDict(text: String, objNumber: Int): String? {
        val marker = "$objNumber 0 obj"
        var idx = text.lastIndexOf(marker)
        while (idx >= 0) {
            // Confirm it's a token boundary (preceded by whitespace/start).
            val before = if (idx == 0) ' ' else text[idx - 1]
            if (before.isWhitespace() || before == '>' || idx == 0) {
                val open = text.indexOf("<<", idx)
                if (open >= 0) {
                    val end = text.indexOf("endobj", idx)
                    if (end < 0 || open < end) {
                        val d = extractDict(text, open)
                        if (d != null) return d
                    }
                }
            }
            idx = text.lastIndexOf(marker, idx - 1)
        }
        return null
    }

    /**
     * Extracts a balanced `<< … >>` dictionary starting at [open] (which must
     * index the opening `<<`). Counts nesting so inner dictionaries don't end
     * the scan early. Returns `null` if the braces never balance.
     */
    private fun extractDict(text: String, open: Int): String? {
        var depth = 0
        var i = open
        while (i < text.length - 1) {
            if (text[i] == '<' && text[i + 1] == '<') {
                depth++
                i += 2
            } else if (text[i] == '>' && text[i + 1] == '>') {
                depth--
                i += 2
                if (depth == 0) return text.substring(open, i)
            } else {
                i++
            }
        }
        return null
    }

    /**
     * The substring of [dict] immediately following [key] up to a reasonable
     * cutoff, used to read a single value (a reference, number, or array)
     * without fully tokenising the dictionary.
     */
    private fun afterKey(dict: String, key: String): String? {
        val idx = dict.indexOf(key)
        if (idx < 0) return null
        return dict.substring(idx + key.length)
    }

    /** Extracts a balanced `[ … ]` array from the start of [s] (skips leading ws). */
    private fun extractArray(s: String): String? {
        val open = s.indexOf('[')
        if (open < 0) return null
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return s.substring(open, i + 1)
                }
            }
            i++
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Geometry + string encoding
    // ---------------------------------------------------------------------

    private data class PdfRect(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    /**
     * Converts a top-left-origin rect (x, y, w, h) into a bottom-left-origin
     * PDF `/Rect` on a page of the given [pageHeight]. Returns `null` for a
     * zero/negative-area rect (nothing clickable).
     */
    private fun annotRect(x: Float, y: Float, w: Float, h: Float, pageHeight: Float): PdfRect? {
        if (w <= 0f || h <= 0f) return null
        val y0 = pageHeight - (y + h)
        val y1 = pageHeight - y
        return PdfRect(x, y0, x + w, y1)
    }

    /** Formats a float without a trailing `.0`, matching typical PDF output. */
    private fun num(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = (v * 100f).toLong()
        // Format the magnitude, then prefix '-' from the sign of `rounded`. Going
        // through the magnitude is what keeps values in (-1, 0) negative: integer
        // division `rounded / 100` is signless there (e.g. -50/100 == 0), so a
        // naive "$whole.$frac" would drop the sign and mirror /Rect and /XYZ
        // coordinates to the wrong side of the axis when they sit just past a
        // page edge (pageHeight - y going slightly negative).
        val abs = if (rounded < 0) -rounded else rounded
        val whole = abs / 100
        val frac = abs % 100
        val magnitude = if (frac == 0L) whole.toString() else "$whole.${frac.toString().padStart(2, '0')}"
        // Only attach the sign to a genuinely non-zero result so -0.00 stays "0".
        return if (rounded < 0 && abs != 0L) "-$magnitude" else magnitude
    }

    /**
     * Encodes [s] as a PDF string literal. ASCII text uses a parenthesised
     * literal with `\`, `(`, `)` escaped; any non-ASCII character forces the
     * UTF-16BE-with-BOM hex form `<FEFF…>` so accents and CJK survive.
     */
    private fun pdfString(s: String): String {
        val ascii = s.all { it.code in 0x20..0x7E || it == '\n' || it == '\t' || it == '\r' }
        return if (ascii) {
            buildString {
                append('(')
                for (c in s) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '(' -> append("\\(")
                        ')' -> append("\\)")
                        '\r' -> append("\\r")
                        '\n' -> append("\\n")
                        '\t' -> append("\\t")
                        else -> append(c)
                    }
                }
                append(')')
            }
        } else {
            buildString {
                append("<FEFF")
                for (c in s) {
                    val code = c.code
                    append(hexByte((code ushr 8) and 0xFF))
                    append(hexByte(code and 0xFF))
                }
                append('>')
            }
        }
    }

    private fun hexByte(b: Int): String {
        val hex = "0123456789ABCDEF"
        return "${hex[(b ushr 4) and 0xF]}${hex[b and 0xF]}"
    }

    private const val DEFAULT_PAGE_HEIGHT = 842f // A4 height in points

    // Regexes are pre-compiled module constants — the patcher runs once per
    // document, but keeping them out of the hot path is tidy regardless.
    private val INT_REGEX = Regex("\\d+")
    private val FLOAT_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    private val REF_REGEX = Regex("(\\d+)\\s+\\d+\\s+R")
    private val ROOT_REGEX = Regex("/Root\\s+(\\d+)\\s+\\d+\\s+R")
    private val SIZE_REGEX = Regex("/Size\\s+(\\d+)")
    private val TYPE_PAGE_REGEX = Regex("/Type\\s*/Page(?![a-zA-Z])")
    private val ANNOTS_REGEX = Regex("/Annots\\s*\\[([^\\]]*)\\]")
    private val OUTLINES_REGEX = Regex("/Outlines\\s+\\d+\\s+\\d+\\s+R")
}
