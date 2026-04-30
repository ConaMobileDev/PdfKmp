package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
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
    private val pages: MutableList<PageSpec> = mutableListOf()
    private val fonts: MutableSet<PdfFont.Custom> = linkedSetOf()

    /**
     * Configures document metadata (title, author, …). Calling this more
     * than once replaces any previous values — fields not set in the latest
     * call become `null`.
     */
    public fun metadata(block: MetadataScope.() -> Unit) {
        metadata = MetadataScope().apply(block).build()
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
        val collected = linkedSetOf<PdfFont.Custom>()
        collected += fonts
        pages.forEach { page -> collectCustomFonts(page.content, collected) }
        return DocumentSpec(
            metadata = metadata,
            pages = pages.toList(),
            customFonts = collected.toList(),
        )
    }
}
