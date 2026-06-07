package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfAttachment
import com.conamobile.pdfkmp.metadata.PdfEncryption
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.awt.color.ColorSpace
import java.awt.color.ICC_Profile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar

/**
 * [PdfDriver] backed by Apache PdfBox.
 *
 * PdfBox builds the PDF entirely in memory: each [beginPage] adds a [PDPage]
 * and opens a [PDPageContentStream] that [JvmPdfCanvas] draws into, and
 * [finish] serialises the whole [PDDocument] to bytes. Fonts are embedded as
 * subset-enabled `Type0` fonts, so the only glyphs written are those actually
 * drawn — and every glyph and shape stays vector.
 *
 * Unlike the Android backend, PdfBox exposes the document info dictionary, so
 * the user-supplied [PdfMetadata] (title, author, subject, …) is written
 * verbatim.
 *
 * The driver is single-use and not thread-safe: pair every [beginPage] with
 * an [endPage] and call [finish] exactly once.
 */
internal class JvmPdfDriver(
    private val metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
) : PdfDriver {

    private val document = PDDocument()
    private val registry = JvmFontRegistry(document)
    private val metrics = JvmFontMetrics(registry)
    private val navigation = JvmNavigation()
    private val forms = JvmAcroForm(document)

    private var currentPage: PDPage? = null
    private var currentStream: PDPageContentStream? = null
    private var open = true

    init {
        applyMetadata(metadata)
        if (metadata.pdfACompliance) applyPdfA(metadata)
        registry.preregister(customFonts)
    }

    override val fontMetrics: FontMetrics get() = metrics

    override fun beginPage(size: PageSize): PdfCanvas {
        check(open) { "Driver has been finished" }
        check(currentPage == null) { "endPage() must be called before beginPage()" }
        val width = size.width.value
        val height = size.height.value
        val page = PDPage(PDRectangle(width, height))
        document.addPage(page)
        val stream = PDPageContentStream(document, page)
        currentPage = page
        currentStream = stream
        return JvmPdfCanvas(document, page, stream, height, registry, navigation, forms)
    }

    override fun endPage() {
        val stream = currentStream ?: error("endPage() called without a matching beginPage()")
        stream.close()
        currentStream = null
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(open) { "Driver already finished" }
        check(currentStream == null) { "endPage() must be called before finish()" }
        return try {
            // Internal-link actions and the outline can only be resolved
            // once every page has rendered — destinations may be declared
            // after the links that point at them (a TOC links forward).
            navigation.applyTo(document)
            // Install the AcroForm (if any fields were added) onto the catalog.
            forms.applyTo(document)
            embedAttachments(metadata.attachments)
            // protect() must run last: it installs the security handler that
            // encrypts every stream — including the embedded files added just
            // above — when the document is saved.
            metadata.encryption?.let { applyEncryption(it) }
            ByteArrayOutputStream().use { out ->
                // Font subsetting happens here, based on the glyphs drawn.
                document.save(out)
                out.toByteArray()
            }
        } finally {
            document.close()
            open = false
        }
    }

    /**
     * Releases the [PDDocument] (and any open content stream) without
     * producing output — used by the renderer when a draw call throws before
     * [finish]. Idempotent and safe to call after [finish].
     */
    override fun close() {
        if (!open) return
        open = false
        currentStream?.let { runCatching { it.close() } }
        currentStream = null
        currentPage = null
        runCatching { document.close() }
    }

    private fun applyMetadata(metadata: PdfMetadata) {
        val info = document.documentInformation
        metadata.title?.let { info.title = it }
        metadata.author?.let { info.author = it }
        metadata.subject?.let { info.subject = it }
        metadata.keywords?.let { info.keywords = it }
        metadata.creator?.let { info.creator = it }
        metadata.producer?.let { info.producer = it }
        // Document language drives screen-reader pronunciation; required for
        // tagged-PDF / PDF/A accessibility. Written whenever supplied.
        metadata.language?.let { document.documentCatalog.language = it }
    }

    /**
     * Best-effort PDF/A-2b setup. Embeds an XMP packet declaring
     * `pdfaid:part=2 conformance=B` (plus a `dc:title` aligned with the info
     * dictionary), attaches an sRGB output intent, and marks the document as
     * tagged (`MarkInfo /Marked true`).
     *
     * This is intentionally not a full validator-clean PDF/A pipeline — see
     * [PdfMetadata.pdfACompliance] for the honest scope. It gives consumers
     * the identifying metadata and colour-management entries a PDF/A reader
     * looks for first, without pulling in extra dependencies.
     */
    private fun applyPdfA(metadata: PdfMetadata) {
        val catalog = document.documentCatalog

        // XMP metadata packet, hand-built (no xmpbox dependency).
        val xmp = buildPdfAXmp(title = metadata.title, producer = metadata.producer)
        val pdMetadata = PDMetadata(document)
        pdMetadata.importXMPMetadata(xmp.encodeToByteArray())
        catalog.metadata = pdMetadata

        // sRGB output intent — PDF/A requires a defined output colour space.
        // PdfBox ships no sRGB ICC profile, so generate one from the JVM's
        // built-in sRGB ColorSpace.
        val iccBytes = ICC_Profile.getInstance(ColorSpace.CS_sRGB).data
        val outputIntent = PDOutputIntent(document, ByteArrayInputStream(iccBytes)).apply {
            info = "sRGB IEC61966-2.1"
            outputCondition = "sRGB IEC61966-2.1"
            outputConditionIdentifier = "sRGB IEC61966-2.1"
            registryName = "http://www.color.org"
        }
        catalog.addOutputIntent(outputIntent)

        // Mark the document as tagged so accessibility consumers look for the
        // structure tree.
        catalog.markInfo = PDMarkInfo().apply { isMarked = true }
    }

    /** Builds the XMP packet declaring PDF/A-2b identity, with [title] aligned. */
    private fun buildPdfAXmp(title: String?, producer: String?): String {
        val titleXml = title?.let {
            """
            <dc:title>
              <rdf:Alt>
                <rdf:li xml:lang="x-default">${xmlEscape(it)}</rdf:li>
              </rdf:Alt>
            </dc:title>
            """.trimIndent()
        } ?: ""
        val producerXml = producer?.let {
            "<pdf:Producer>${xmlEscape(it)}</pdf:Producer>"
        } ?: ""
        return """
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                  <pdfaid:part>2</pdfaid:part>
                  <pdfaid:conformance>B</pdfaid:conformance>
                </rdf:Description>
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  $titleXml
                </rdf:Description>
                <rdf:Description rdf:about=""
                    xmlns:pdf="http://ns.adobe.com/pdf/1.3/">
                  $producerXml
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * Installs PdfBox's standard security handler with AES‑256 encryption,
     * mapping the [PdfEncryption] flags onto the PDF access-permission bits.
     */
    private fun applyEncryption(encryption: PdfEncryption) {
        val permissions = AccessPermission().apply {
            setCanPrint(encryption.allowPrinting)
            setCanExtractContent(encryption.allowCopying)
            setCanModify(encryption.allowModification)
        }
        val policy = StandardProtectionPolicy(
            encryption.ownerPassword,
            encryption.userPassword,
            permissions,
        ).apply {
            // PdfBox 3 implements AES‑256 (PDF 2.0) natively; 256-bit keys are
            // the strongest the standard security handler offers.
            encryptionKeyLength = 256
        }
        document.protect(policy)
    }

    /**
     * Embeds each [PdfAttachment] into the document catalog's embedded-files
     * name tree. A consumer (or an XML-invoice validator) finds them through
     * the catalog `Names` dictionary, which is the standard ZUGFeRD/Factur‑X
     * discovery path. No-op when there is nothing to embed.
     */
    private fun embedAttachments(attachments: List<PdfAttachment>) {
        if (attachments.isEmpty()) return

        val now = Calendar.getInstance()
        val specs = LinkedHashMap<String, PDComplexFileSpecification>()
        for (attachment in attachments) {
            val embedded = PDEmbeddedFile(
                document,
                ByteArrayInputStream(attachment.bytes),
            ).apply {
                subtype = attachment.mimeType
                size = attachment.bytes.size
                creationDate = now
                modDate = now
            }
            val spec = PDComplexFileSpecification().apply {
                file = attachment.fileName
                // Unicode variants ensure non-ASCII file names survive; both
                // the legacy /F and /UF entries point at the same stream.
                setFileUnicode(attachment.fileName)
                setEmbeddedFile(embedded)
                setEmbeddedFileUnicode(embedded)
                attachment.description?.let { fileDescription = it }
            }
            // Name-tree keys must be unique; collisions would drop an entry, so
            // disambiguate duplicate file names with a numeric suffix.
            var key = attachment.fileName
            var counter = 1
            while (specs.containsKey(key)) {
                key = "${attachment.fileName} ($counter)"
                counter++
            }
            specs[key] = spec
        }

        val embeddedFiles = PDEmbeddedFilesNameTreeNode().apply { names = specs }
        val catalog = document.documentCatalog
        // Reuse an existing Names dictionary if the navigation pass created one;
        // otherwise install a fresh one so we don't clobber other name trees.
        val nameDictionary = catalog.names ?: PDDocumentNameDictionary(catalog)
        nameDictionary.embeddedFiles = embeddedFiles
        catalog.names = nameDictionary
    }
}

/**
 * Collects the document's navigation structure — named destinations,
 * internal-link annotations awaiting their target, and outline entries —
 * while pages render, and resolves everything in one pass at
 * [JvmPdfDriver.finish].
 *
 * Deferral is what makes forward references work: a table of contents on
 * page 1 links to destinations that only get registered when later pages
 * render. Links whose destination never appears stay visually present but
 * inert (no action) — the renderer treats a missing anchor as a no-op
 * rather than an error.
 */
internal class JvmNavigation {

    internal data class Bookmark(
        val title: String,
        val level: Int,
        val destination: PDPageXYZDestination,
    )

    val destinations: MutableMap<String, PDPageXYZDestination> = HashMap()
    val pendingLinks: MutableList<Pair<String, PDAnnotationLink>> = ArrayList()
    val bookmarks: MutableList<Bookmark> = ArrayList()

    fun applyTo(document: PDDocument) {
        for ((name, link) in pendingLinks) {
            val destination = destinations[name] ?: continue
            link.action = PDActionGoTo().apply { setDestination(destination) }
        }

        if (bookmarks.isEmpty()) return
        val outline = PDDocumentOutline()
        document.documentCatalog.documentOutline = outline
        // Nest by level: each entry attaches under the most recent entry
        // with a smaller level, like markdown headings build a tree.
        val stack = ArrayDeque<Pair<Int, PDOutlineItem>>()
        for (bookmark in bookmarks) {
            val item = PDOutlineItem().apply {
                title = bookmark.title
                destination = bookmark.destination
            }
            while (stack.isNotEmpty() && stack.last().first >= bookmark.level) {
                stack.removeLast()
            }
            val parent = stack.lastOrNull()?.second
            if (parent != null) parent.addLast(item) else outline.addLast(item)
            stack.addLast(bookmark.level to item)
        }
        outline.openNode()
    }
}

/**
 * Builds the document-level [PDAcroForm] lazily, the first time a form field
 * is added, and installs it onto the catalog at [JvmPdfDriver.finish].
 *
 * One instance is shared across every page's [JvmPdfCanvas] (like
 * [JvmNavigation]) so that all fields land in the same form dictionary. The
 * form carries a default-appearance (`/DA`) string referencing an embedded
 * Helvetica resource so viewers know which font to draw field values in, and
 * `NeedAppearances` is set so the viewer regenerates each field's appearance
 * stream on open — the simplest robust route to correct rendering without
 * hand-building appearance XObjects per field.
 *
 * Field-name collisions are resolved by appending a `-2`, `-3`, … suffix: a
 * duplicate partial name would otherwise merge two widgets into one logical
 * field and lose one of their values.
 */
internal class JvmAcroForm(private val document: PDDocument) {

    /** The default-appearance font resource name referenced by the `/DA` string. */
    private val daFontName = COSName.getPDFName("Helv")

    private var acroForm: PDAcroForm? = null
    private val usedNames = HashSet<String>()

    /**
     * Returns the document's [PDAcroForm], creating + configuring it on first
     * use. Fields call this so they all share one form dictionary.
     */
    fun acroForm(): PDAcroForm = acroForm ?: PDAcroForm(document).apply {
        val resources = PDResources()
        resources.put(daFontName, PDType1Font(Standard14Fonts.FontName.HELVETICA))
        defaultResources = resources
        // 0 = auto-size font to the field height; black non-stroking color.
        defaultAppearance = "/Helv 0 Tf 0 g"
        // Let viewers generate appearance streams — robust across readers
        // without us hand-rolling per-field XObjects.
        setNeedAppearances(true)
        acroForm = this
    }

    /**
     * Returns a unique partial name for [requested], appending `-2`, `-3`, …
     * on collision so two same-named fields don't merge.
     */
    fun uniqueName(requested: String): String {
        if (usedNames.add(requested)) return requested
        var counter = 2
        while (!usedNames.add("$requested-$counter")) counter++
        return "$requested-$counter"
    }

    /** Adds a fully-built field to the form's top-level field list. */
    fun addField(field: PDField) {
        acroForm().fields.add(field)
    }

    /** Installs the form onto the catalog. No-op when no field was added. */
    fun applyTo(document: PDDocument) {
        val form = acroForm ?: return
        document.documentCatalog.acroForm = form
    }
}
