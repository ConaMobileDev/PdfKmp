package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.layout.VerticalArrangement
import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.PageContext
import com.conamobile.pdfkmp.node.PageSpec
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp

/**
 * Receiver of `page { ... }`.
 *
 * The page body is implicitly a vertical column: content added via [text],
 * [column], [row], [spacer], [image], or [weighted] stacks top-to-bottom.
 * Nest an explicit [row] for horizontal sections.
 *
 * Properties on this scope control the page-level frame: physical [size],
 * [padding] inset around the content area, [spacing] between children,
 * cross-axis [horizontalAlignment], main-axis [verticalArrangement], and
 * the [pageBreakStrategy] used when content overflows. Each defaults to a
 * value inherited from the enclosing [DocumentScope] — override here for a
 * single page or there for the whole document.
 */
@PdfDsl
public class PageScope internal constructor(
    /** Physical page dimensions in PDF points. Set via `page(size = ...)`. */
    public val size: PageSize,
    textStyle: TextStyle,
    defaultPadding: Padding,
    defaultPageBreakStrategy: PageBreakStrategy,
) : ContainerScope(textStyle) {

    /**
     * Page margins applied as an inset inside the page bounds. Defaults to
     * the document-wide value supplied by [DocumentScope.defaultPagePadding].
     */
    public var padding: Padding = defaultPadding

    /** Vertical spacing inserted between consecutive top-level children. */
    public var spacing: Dp = Dp.Zero

    /**
     * How the top-level children are distributed along the page's vertical
     * axis. Defaults to [VerticalArrangement.Top]; pick a `Space*` value to
     * spread content across the page height.
     */
    public var verticalArrangement: VerticalArrangement = VerticalArrangement.Top

    /**
     * Cross-axis alignment for children that are narrower than the page
     * content area. Defaults to [HorizontalAlignment.Start]; switch to
     * [HorizontalAlignment.Center] for centred prose layouts.
     */
    public var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start

    /**
     * Strategy used when a child does not fit in the remaining vertical
     * space. See [PageBreakStrategy] for the trade-offs.
     */
    public var pageBreakStrategy: PageBreakStrategy = defaultPageBreakStrategy

    private var headerBuilder: ((PageContext) -> ColumnNode)? = null
    private var footerBuilder: ((PageContext) -> ColumnNode)? = null
    private var watermarkNode: BoxNode? = null

    /**
     * Configures a header rendered at the top of every physical page
     * produced by this logical page. The [block] is invoked once per
     * physical page with a [PageContext] carrying the current page
     * number and the total page count, so callers can build dynamic
     * content like `Page X of Y`.
     *
     * Calling [header] more than once replaces the previous builder.
     */
    public fun header(block: ColumnScope.(PageContext) -> Unit) {
        val capturedTextStyle = textStyle
        headerBuilder = { ctx ->
            val scope = ColumnScope(capturedTextStyle).apply { block(ctx) }
            ColumnNode(children = scope.children.toList())
        }
    }

    /**
     * Configures a footer rendered at the bottom of every physical
     * page. Mirror of [header]; the same [PageContext] semantics apply.
     */
    public fun footer(block: ColumnScope.(PageContext) -> Unit) {
        val capturedTextStyle = textStyle
        footerBuilder = { ctx ->
            val scope = ColumnScope(capturedTextStyle).apply { block(ctx) }
            ColumnNode(children = scope.children.toList())
        }
    }

    /**
     * Configures a watermark drawn behind every physical page's body
     * content. Useful for "DRAFT" / "CONFIDENTIAL" stamps, brand
     * marks, or background lattices that should appear under each page
     * regardless of content overflow.
     *
     * The block runs against a [BoxScope] so children can be anchored to
     * any of the nine [com.conamobile.pdfkmp.layout.BoxAlignment]
     * positions. The watermark spans the entire page (not the content
     * frame) so corner-anchored marks land flush with the page edge.
     *
     * Calling [watermark] more than once replaces the previous content.
     */
    public fun watermark(block: BoxScope.() -> Unit) {
        val scope = BoxScope(textStyle).apply(block)
        watermarkNode = BoxNode(
            children = scope.build(),
            width = size.width,
            height = size.height,
        )
    }

    internal fun build(): PageSpec = PageSpec(
        size = size,
        padding = padding,
        content = ColumnNode(
            children = children.toList(),
            spacing = spacing,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ),
        pageBreakStrategy = pageBreakStrategy,
        header = headerBuilder,
        footer = footerBuilder,
        watermark = watermarkNode,
    )
}
