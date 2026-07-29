package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.metadata.PdfAttachment
import com.conamobile.pdfkmp.metadata.PdfEncryption
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.node.DocumentSpec
import com.conamobile.pdfkmp.node.PageSpec
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextStyle

/**
 * Receiver of `pdf { ... }`. Top-level entry of the DSL.
 *
 * A document is a sequence of pages plus optional metadata, plus document-
 * wide defaults inherited by every page. Pages are added in source order;
 * the renderer respects that order exactly.
 *
 * The document-wide defaults — [defaultTextStyle], [defaultPagePadding],
 * [defaultPageBreakStrategy] — are how you configure typography and frame
 * once and have every subsequent [page] inherit those values. Override them
 * per-page on the [PageScope] when you need a different look for a single
 * page.
 *
 * Custom fonts referenced anywhere in the document are detected
 * automatically from the node tree, but you may also pre-register them with
 * [registerFont] to control the order or to register a font that no current
 * element uses directly.
 */
@PdfDsl
public class DocumentScope internal constructor() {

    /**
     * Default text style inherited by every [page] unless overridden inside
     * the page block. Mutate this to set document-wide typography (default
     * font, color, line height, …).
     */
    public var defaultTextStyle: TextStyle = TextStyle.Default

    /**
     * Default page margins inherited by every [page] unless the page
     * overrides [PageScope.padding]. Defaults to [Padding.Default]
     * (40 dp on every side) which produces a comfortable printed-document
     * look.
     */
    public var defaultPagePadding: Padding = Padding.Default

    /**
     * Default page break strategy inherited by every [page] unless the page
     * overrides [PageScope.pageBreakStrategy]. Defaults to
     * [PageBreakStrategy.MoveToNextPage], which is the safer choice — change
     * to [PageBreakStrategy.Slice] for documents where partial display of
     * children is acceptable.
     */
    public var defaultPageBreakStrategy: PageBreakStrategy = PageBreakStrategy.MoveToNextPage

    private var metadata: PdfMetadata = PdfMetadata.Empty
    private var encryption: PdfEncryption? = null
    private var pdfAOverride: Boolean? = null
    private val attachments: MutableList<PdfAttachment> = mutableListOf()
    private val pages: MutableList<PageSpec> = mutableListOf()
    private val fonts: MutableSet<PdfFont.Custom> = linkedSetOf()

    /**
     * Configures document metadata (title, author, …). Calling this more
     * than once replaces any previous values — fields not set in the latest
     * call become `null`.
     *
     * Encryption and attachments are configured separately via [encryption]
     * and [attachment]; they survive across `metadata { }` calls and are
     * merged into the final metadata at build time.
     */
    public fun metadata(block: MetadataScope.() -> Unit) {
        metadata = MetadataScope().apply(block).build()
    }

    /**
     * Password-protects the document. Calling this more than once replaces the
     * previous configuration. See [PdfEncryption] for the per-platform support
     * matrix (full on JVM/Desktop, partial on iOS, no-op on Android).
     *
     * ```
     * encryption {
     *     ownerPassword = "owner"
     *     userPassword = "user"
     *     allowPrinting = false
     * }
     * ```
     */
    public fun encryption(block: EncryptionScope.() -> Unit) {
        encryption = EncryptionScope().apply(block).build()
    }

    /**
     * Password-protects the document with a pre-built [PdfEncryption]. Useful
     * when the configuration is assembled elsewhere; equivalent to the
     * [encryption] builder overload.
     */
    public fun encryption(encryption: PdfEncryption) {
        this.encryption = encryption
    }

    /**
     * Requests best-effort PDF/A-2b conformance for the document. Equivalent
     * to setting [MetadataScope.pdfACompliance] inside `metadata { }`, but
     * survives across `metadata { }` calls (like [encryption] and
     * [attachment]) and wins over the value set there.
     *
     * Honest scope: best-effort only — XMP id + sRGB output intent +
     * document-info alignment + `MarkInfo /Marked`. Full veraPDF conformance
     * is not guaranteed. JVM/Desktop only; Android and iOS ignore it. See
     * [PdfMetadata.pdfACompliance].
     */
    public fun pdfA(enabled: Boolean) {
        pdfAOverride = enabled
    }

    /**
     * Embeds a file into the document. Repeatable — each call adds another
     * attachment in source order. Only honoured by the JVM/Desktop backend;
     * silently skipped on iOS and Android (see [PdfAttachment]).
     *
     * @param fileName name shown to the user (e.g. `"invoice.xml"`).
     * @param bytes raw file contents.
     * @param mimeType MIME type recorded as the embedded stream subtype.
     * @param description optional human-readable description.
     */
    public fun attachment(
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "application/octet-stream",
        description: String? = null,
    ) {
        attachments += PdfAttachment(
            fileName = fileName,
            bytes = bytes,
            mimeType = mimeType,
            description = description,
        )
    }

    /**
     * Adds a page to the document.
     *
     * @param size physical page size; defaults to [PageSize.A4].
     */
    public fun page(
        size: PageSize = PageSize.A4,
        block: PageScope.() -> Unit,
    ) {
        val scope = PageScope(
            size = size,
            textStyle = defaultTextStyle,
            defaultPadding = defaultPagePadding,
            defaultPageBreakStrategy = defaultPageBreakStrategy,
        ).apply(block)
        pages += scope.build()
    }

    /**
     * Registers a custom TTF/OTF font with the document so that it can be
     * referenced by [PdfFont.Custom]. Fonts referenced through a [TextStyle]
     * are picked up automatically; this method is for the rare case where
     * you want to ensure a font is bundled even if no current element uses
     * it.
     */
    public fun registerFont(font: PdfFont.Custom) {
        fonts += font
    }

    internal fun build(): DocumentSpec {
        require(pages.isNotEmpty()) {
            "A PDF document needs at least one page { } block — an empty page tree is not a valid PDF"
        }
        val collected = linkedSetOf<PdfFont.Custom>()
        collected += fonts
        pages.forEach { page ->
            collectCustomFonts(page.content, collected)
            page.watermark?.let { collectCustomFonts(it, collected) }
        }
        // Fold encryption + attachments into the metadata so they ride the
        // existing factory.create(metadata, …) channel without widening
        // DocumentSpec — drivers read them off PdfMetadata.
        val mergedMetadata = metadata.copy(
            encryption = encryption,
            attachments = attachments.toList(),
            pdfACompliance = pdfAOverride ?: metadata.pdfACompliance,
        )
        return DocumentSpec(
            metadata = mergedMetadata,
            pages = pages.toList(),
            customFonts = collected.toList(),
        )
    }
}
