package com.conamobile.pdfkmp.pdfwriter

import com.conamobile.pdfkmp.metadata.PdfMetadata
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises [PdfPatcher] — the pure-Kotlin incremental-update post-processor
 * that backfills the info dictionary, link annotations, and outline that
 * Android's `PdfDocument` cannot write.
 *
 * The fixtures are hand-rolled **classic, uncompressed** PDFs (plain `N 0 obj`
 * definitions, a classic `xref` table, a `trailer` dictionary) because that is
 * exactly the shape `android.graphics.pdf.PdfDocument` emits — the format the
 * patcher's text-based parser is built for. (PdfBox's own `save()` writes
 * compressed object streams + a cross-reference *stream* instead, which is a
 * different beast and not what runs on Android.) After patching, every fixture
 * is re-parsed with PdfBox, which reads classic PDFs natively, to prove the new
 * structure is well-formed and discoverable by a real reader.
 */
class PdfPatcherTest {

    @Test
    fun addsMetadataLinkAndOutline() {
        val original = classicPdf(pageCount = 1)
        val navigation = PdfNavigation().apply {
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 40f, width = 120f, height = 16f,
                url = "https://example.com",
            )
            bookmarks += PdfBookmark(title = "Intro", level = 0, pageIndex = 0, y = 40f)
        }
        val metadata = PdfMetadata(title = "Patched Title", author = "Tester")

        val patched = PdfPatcher.apply(original, metadata, navigation)
        assertTrue(patched.size > original.size, "incremental update should grow the file")

        Loader.loadPDF(patched).use { doc ->
            val info = doc.documentInformation
            assertEquals("Patched Title", info.title, "title not patched in")
            assertEquals("Tester", info.author, "author not patched in")

            val annots = doc.getPage(0).annotations
                .filterIsInstance<PDAnnotationLink>()
            assertTrue(annots.isNotEmpty(), "no link annotation on page 0")
            val uriAction = annots.mapNotNull { it.action as? PDActionURI }.firstOrNull()
            assertNotNull(uriAction, "link has no URI action")
            assertEquals("https://example.com", uriAction.uri, "wrong URI")

            val outline = doc.documentCatalog.documentOutline
            assertNotNull(outline, "outline missing")
            assertEquals("Intro", outline.firstChild?.title, "outline entry title")
        }
    }

    @Test
    fun internalLinkResolvesToGoToAcrossPages() {
        // A two-page fixture so a forward anchor (declared on a later page) must
        // be resolved by the patcher — the TOC-links-forward case.
        val original = classicPdf(pageCount = 2)
        val navigation = PdfNavigation().apply {
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 40f, width = 100f, height = 14f,
                anchor = "ch2",
            )
            destinations += PdfDestination(name = "ch2", pageIndex = 1, y = 100f)
        }

        val patched = PdfPatcher.apply(original, PdfMetadata.Empty, navigation)
        Loader.loadPDF(patched).use { doc ->
            assertEquals(2, doc.numberOfPages, "fixture should keep both pages")
            val goTo = doc.getPage(0).annotations
                .filterIsInstance<PDAnnotationLink>()
                .mapNotNull { it.action as? PDActionGoTo }
                .firstOrNull()
            assertNotNull(goTo, "internal link did not resolve to a GoTo action")
        }
    }

    @Test
    fun nestedBookmarksBuildTree() {
        val original = classicPdf(pageCount = 1)
        val navigation = PdfNavigation().apply {
            bookmarks += PdfBookmark("Chapter 1", level = 0, pageIndex = 0, y = 40f)
            bookmarks += PdfBookmark("Section 1.1", level = 1, pageIndex = 0, y = 80f)
            bookmarks += PdfBookmark("Chapter 2", level = 0, pageIndex = 0, y = 120f)
        }

        val patched = PdfPatcher.apply(original, PdfMetadata.Empty, navigation)
        Loader.loadPDF(patched).use { doc ->
            val outline = doc.documentCatalog.documentOutline
            assertNotNull(outline, "outline missing")
            val first = outline.firstChild
            assertEquals("Chapter 1", first?.title)
            assertEquals("Section 1.1", first?.firstChild?.title, "nested entry missing")
            assertEquals("Chapter 2", first?.nextSibling?.title, "sibling entry missing")
        }
    }

    @Test
    fun nonAsciiMetadataSurvivesAsUtf16() {
        val original = classicPdf(pageCount = 1)
        val metadata = PdfMetadata(title = "Café — résumé 日本語", author = "Zoë")
        val patched = PdfPatcher.apply(original, metadata, PdfNavigation())
        Loader.loadPDF(patched).use { doc ->
            assertEquals("Café — résumé 日本語", doc.documentInformation.title)
            assertEquals("Zoë", doc.documentInformation.author)
        }
    }

    @Test
    fun garbageInputReturnedUnchanged() {
        val garbage = ByteArray(512) { (it * 31 + 7).toByte() }
        val navigation = PdfNavigation().apply {
            links += PdfLink(0, 0f, 0f, 10f, 10f, url = "https://x")
        }
        val result = PdfPatcher.apply(garbage, PdfMetadata(title = "x"), navigation)
        assertTrue(result.contentEquals(garbage), "garbage input must be returned unchanged")
    }

    @Test
    fun noNavigationOrMetadataReturnsOriginal() {
        val original = classicPdf(pageCount = 1)
        // With no metadata fields set and no navigation, the patcher has nothing
        // to add and must return the exact same byte array (identity short-cut).
        val result = PdfPatcher.apply(original, PdfMetadata(producer = null), PdfNavigation())
        assertTrue(result === original, "no-op patch must return the exact input array")
    }

    @Test
    fun appendedXrefOffsetsMatchActualByteIndices() {
        // BUG 1 regression: every appended xref offset must equal the true byte
        // index of its object, and startxref must equal the byte index of the
        // appended `xref` keyword. PdfBox tolerates wrong offsets (it rebuilds
        // the table), so this asserts the raw bytes directly instead of trusting
        // a successful re-parse.
        val original = classicPdf(pageCount = 1)
        val navigation = PdfNavigation().apply {
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 40f, width = 120f, height = 16f,
                url = "https://example.com",
            )
            bookmarks += PdfBookmark(title = "Intro", level = 0, pageIndex = 0, y = 40f)
        }
        val metadata = PdfMetadata(title = "Patched Title", author = "Tester")

        val patched = PdfPatcher.apply(original, metadata, navigation)
        val ascii = patched.decodeToString()

        // Parse the appended xref table (the last one) into objNum -> 10-digit
        // offset, then confirm each offset points at that object's `N 0 obj`.
        // The keyword is preceded by a newline (the prior endobj's EOL); matching
        // "\nxref\n" avoids the substring inside "startxref\n".
        val lastXref = ascii.lastIndexOf("\nxref\n") + 1
        assertTrue(lastXref > 0, "appended xref keyword not found")

        // startxref must name the exact byte index of that appended xref keyword.
        val startXrefIdx = ascii.lastIndexOf("startxref")
        val startXrefValue = Regex("\\d+")
            .find(ascii.substring(startXrefIdx + "startxref".length))!!
            .value.toInt()
        // The whole document here is ASCII, so the char index of the keyword is
        // also its byte index — assert startxref names exactly that.
        assertEquals(
            lastXref,
            startXrefValue,
            "startxref does not point at the appended xref keyword's byte index",
        )

        // Walk the subsections of the appended xref table.
        val xrefBody = ascii.substring(lastXref, ascii.indexOf("trailer", lastXref))
        val lines = xrefBody.lines()
        var li = 1 // skip the leading "xref" line
        var checkedObjects = 0
        while (li < lines.size) {
            val header = lines[li].trim()
            if (header.isEmpty()) { li++; continue }
            val parts = header.split(Regex("\\s+"))
            if (parts.size != 2) break // reached trailer/garbage
            val start = parts[0].toInt()
            val count = parts[1].toInt()
            li++
            for (k in 0 until count) {
                val entry = lines[li].trim().split(Regex("\\s+"))
                val recordedOffset = entry[0].toInt()
                val objNum = start + k
                val actual = byteIndexOfMarker(patched, "$objNum 0 obj", original.size)
                assertTrue(actual >= 0, "object $objNum not present in appended tail")
                assertEquals(
                    actual,
                    recordedOffset,
                    "xref offset for object $objNum does not match its byte index",
                )
                checkedObjects++
                li++
            }
        }
        assertTrue(checkedObjects > 0, "no appended objects were verified")
    }

    @Test
    fun negativeDestinationTopKeepsItsSign() {
        // BUG 2 regression: a destination whose computed top (pageHeight - y)
        // lands just past the bottom edge — here -0.5 — must serialise as a
        // negative number in the GoTo /XYZ, not be mirrored to +0.5.
        val pageHeight = 842
        val original = classicPdf(pageCount = 1, height = pageHeight)
        val navigation = PdfNavigation().apply {
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 40f, width = 100f, height = 14f,
                anchor = "below",
            )
            // top = pageHeight - y = -0.5
            destinations += PdfDestination(name = "below", pageIndex = 0, y = pageHeight + 0.5f)
        }

        val patched = PdfPatcher.apply(original, PdfMetadata.Empty, navigation)
        val ascii = patched.decodeToString()
        // num() emits two fractional digits, so -0.5 serialises as -0.50.
        assertTrue(
            ascii.contains("/XYZ 0 -0.50 null"),
            "negative destination top lost its sign; expected '/XYZ 0 -0.50 null' in output",
        )
    }

    @Test
    fun unresolvedAnchorLeavesNoObjectNumberGap() {
        // BUG 3 regression: an internal link whose anchor was never registered is
        // inert (no annotation/action), and crucially must NOT reserve an object
        // number — that would leave a hole below the trailer /Size with no xref
        // entry. Mix one resolved and one unresolved anchor link.
        val original = classicPdf(pageCount = 2)
        val navigation = PdfNavigation().apply {
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 40f, width = 100f, height = 14f,
                anchor = "ch2", // resolved below
            )
            links += PdfLink(
                pageIndex = 0,
                x = 40f, y = 80f, width = 100f, height = 14f,
                anchor = "missing", // never registered -> inert, must not allocate
            )
            destinations += PdfDestination(name = "ch2", pageIndex = 1, y = 100f)
        }

        val patched = PdfPatcher.apply(original, PdfMetadata.Empty, navigation)
        val ascii = patched.decodeToString()

        // Highest object number actually written in the appended tail.
        val tail = ascii.substring(original.size)
        val maxWritten = Regex("(\\d+) 0 obj").findAll(tail)
            .map { it.groupValues[1].toInt() }
            .maxOrNull()
        assertNotNull(maxWritten, "no objects were appended")

        Loader.loadPDF(patched).use { doc ->
            assertEquals(2, doc.numberOfPages, "fixture should keep both pages")

            // /Size must be exactly highest-written-object + 1: no gap.
            val size = (doc.document.trailer.getDictionaryObject(COSName.SIZE) as COSNumber).intValue()
            assertEquals(maxWritten + 1, size, "trailer /Size leaves an object-number gap")

            // Exactly one resolved GoTo link should survive; the inert one carries
            // no action (today's intended behaviour for a missing anchor).
            val linkActions = doc.getPage(0).annotations
                .filterIsInstance<PDAnnotationLink>()
                .map { it.action }
            val goTos = linkActions.filterIsInstance<PDActionGoTo>()
            assertEquals(1, goTos.size, "expected exactly one resolved GoTo link")
        }
    }

    /**
     * Byte index of [needle] within [bytes] at or after [from], comparing the
     * needle as raw ASCII bytes (the patched tail is pure ASCII).
     */
    private fun byteIndexOf(bytes: ByteArray, needle: String, from: Int): Int {
        val n = needle.encodeToByteArray()
        var i = from
        outer@ while (i + n.size <= bytes.size) {
            for (j in n.indices) if (bytes[i + j] != n[j]) { i++; continue@outer }
            return i
        }
        return -1
    }

    /**
     * Byte index of an `N 0 obj` definition at or after [from], requiring a
     * token boundary before the object number so e.g. searching for "1 0 obj"
     * does not match the "1" inside "11 0 obj".
     */
    private fun byteIndexOfMarker(bytes: ByteArray, marker: String, from: Int): Int {
        var search = from
        while (true) {
            val at = byteIndexOf(bytes, marker, search)
            if (at < 0) return -1
            val before = if (at == 0) ' '.code.toByte() else bytes[at - 1]
            // A digit before the marker means we matched a longer object number.
            if (before < '0'.code.toByte() || before > '9'.code.toByte()) return at
            search = at + 1
        }
    }

    // -----------------------------------------------------------------------
    // Classic-PDF fixture builder
    //
    // Mirrors android.graphics.pdf.PdfDocument's output shape: every object is a
    // plain `N 0 obj … endobj`, the cross-reference is a classic `xref` table,
    // and the trailer is a `trailer << … >>` dictionary. Offsets are computed by
    // serialising sequentially and recording each object's byte position.
    // -----------------------------------------------------------------------

    private fun classicPdf(pageCount: Int, width: Int = 595, height: Int = 842): ByteArray {
        val sb = StringBuilder()
        val offsets = HashMap<Int, Int>()

        fun emit(obj: Int, body: String) {
            offsets[obj] = sb.length
            sb.append(obj).append(" 0 obj\n").append(body).append("\nendobj\n")
        }

        sb.append("%PDF-1.4\n")

        // Object numbering: 1 = catalog, 2 = pages node, then for each page a
        // page object and a content stream object.
        val catalog = 1
        val pagesNode = 2
        val pageObjs = IntArray(pageCount)
        val contentObjs = IntArray(pageCount)
        var next = 3
        for (i in 0 until pageCount) {
            pageObjs[i] = next++
            contentObjs[i] = next++
        }

        emit(catalog, "<< /Type /Catalog /Pages $pagesNode 0 R >>")
        val kids = pageObjs.joinToString(" ") { "$it 0 R" }
        emit(pagesNode, "<< /Type /Pages /Kids [$kids] /Count $pageCount >>")

        for (i in 0 until pageCount) {
            emit(
                pageObjs[i],
                "<< /Type /Page /Parent $pagesNode 0 R " +
                    "/MediaBox [0 0 $width $height] " +
                    "/Contents ${contentObjs[i]} 0 R " +
                    "/Resources << >> >>",
            )
            val stream = "BT /F1 12 Tf 50 ${height - 50} Td (page ${i + 1}) Tj ET"
            emit(contentObjs[i], "<< /Length ${stream.length} >>\nstream\n$stream\nendstream")
        }

        val size = next
        val xrefOffset = sb.length
        sb.append("xref\n0 $size\n")
        sb.append("0000000000 65535 f \n")
        for (obj in 1 until size) {
            val off = offsets.getValue(obj)
            sb.append(off.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        sb.append("trailer\n<< /Size $size /Root $catalog 0 R >>\n")
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")

        return sb.toString().encodeToByteArray()
    }
}
