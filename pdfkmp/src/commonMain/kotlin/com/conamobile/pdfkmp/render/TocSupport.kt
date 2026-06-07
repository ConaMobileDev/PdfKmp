package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.node.AnchorNode
import com.conamobile.pdfkmp.node.BookmarkNode
import com.conamobile.pdfkmp.node.BoxChild
import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.DividerNode
import com.conamobile.pdfkmp.node.DocumentSpec
import com.conamobile.pdfkmp.node.InternalLinkNode
import com.conamobile.pdfkmp.node.KeepTogetherNode
import com.conamobile.pdfkmp.node.LinkNode
import com.conamobile.pdfkmp.node.MultiColumnNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.TocNode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.Dp

/**
 * One resolved table-of-contents row.
 *
 * @property index position of the source bookmark in document order —
 *   also the suffix of the synthetic anchor injected next to it.
 * @property pageNumber 1-based physical page the bookmark landed on. `0`
 *   during the placeholder pass, before the dry run resolves pages.
 */
internal data class TocEntry(
    val index: Int,
    val title: String,
    val level: Int,
    val pageNumber: Int,
)

/** Prefix of the synthetic named destinations injected next to bookmarks. */
internal const val TOC_ANCHOR_PREFIX: String = "__pdfkmp_toc_"

/** `true` when any page body contains a [TocNode]. */
internal fun DocumentSpec.containsToc(): Boolean = pages.any { it.content.containsTocNode() }

private fun PdfNode.containsTocNode(): Boolean = when (this) {
    is TocNode -> true
    is ColumnNode -> children.any { it.containsTocNode() }
    is RowNode -> children.any { it.containsTocNode() }
    is BoxNode -> children.any { it.node.containsTocNode() }
    is MultiColumnNode -> children.any { it.containsTocNode() }
    is KeepTogetherNode -> child.containsTocNode()
    is WeightNode -> child.containsTocNode()
    is LinkNode -> child.containsTocNode()
    is InternalLinkNode -> child.containsTocNode()
    is TableNode ->
        rows.any { row -> row.cells.any { it.content.containsTocNode() } } ||
            headerRow?.cells?.any { it.content.containsTocNode() } == true
    else -> false
}

/**
 * Bookmark titles and levels in document order, walking page bodies with
 * the same traversal [expandToc] uses — the index in this list IS the
 * anchor suffix the expansion injects.
 */
internal fun collectBookmarks(spec: DocumentSpec): List<Pair<String, Int>> {
    val out = mutableListOf<Pair<String, Int>>()
    spec.pages.forEach { collectBookmarks(it.content, out) }
    return out
}

private fun collectBookmarks(node: PdfNode, out: MutableList<Pair<String, Int>>) {
    when (node) {
        is BookmarkNode -> out += node.title to node.level
        is ColumnNode -> node.children.forEach { collectBookmarks(it, out) }
        is RowNode -> node.children.forEach { collectBookmarks(it, out) }
        is BoxNode -> node.children.forEach { collectBookmarks(it.node, out) }
        is MultiColumnNode -> node.children.forEach { collectBookmarks(it, out) }
        is KeepTogetherNode -> collectBookmarks(node.child, out)
        is WeightNode -> collectBookmarks(node.child, out)
        is LinkNode -> collectBookmarks(node.child, out)
        is InternalLinkNode -> collectBookmarks(node.child, out)
        is TableNode -> {
            node.headerRow?.cells?.forEach { collectBookmarks(it.content, out) }
            node.rows.forEach { row -> row.cells.forEach { collectBookmarks(it.content, out) } }
        }
        else -> Unit
    }
}

/**
 * Returns a copy of [spec] with every [TocNode] replaced by its rendered
 * entry rows and a synthetic [AnchorNode] injected right after every
 * [BookmarkNode], so TOC rows can link to the bookmark positions.
 *
 * Run twice by the renderer: once with placeholder page numbers to learn
 * the document's pagination (the TOC itself takes space), then with the
 * real numbers from that dry run. Entry heights don't depend on the
 * number text, so pagination is stable across the two passes.
 */
internal fun expandToc(spec: DocumentSpec, entries: List<TocEntry>): DocumentSpec {
    val transformer = TocTransformer(entries)
    return spec.copy(
        pages = spec.pages.map { page ->
            page.copy(content = transformer.transform(page.content) as ColumnNode)
        },
    )
}

/**
 * Stateful tree rewriter behind [expandToc]. The anchor counter advances
 * in document order — matching [collectBookmarks]' traversal — so anchor
 * `N` always lands next to the bookmark that produced entry `N`.
 */
private class TocTransformer(private val entries: List<TocEntry>) {

    private var anchorIndex = 0

    fun transform(node: PdfNode): PdfNode = when (node) {
        is TocNode -> buildTocColumn(entries.filter { it.level <= node.maxLevel }, node)
        is ColumnNode -> node.copy(children = transformChildren(node.children))
        is RowNode -> node.copy(children = transformChildren(node.children))
        is MultiColumnNode -> node.copy(children = transformChildren(node.children))
        is KeepTogetherNode -> node.copy(child = transformSingle(node.child))
        is BoxNode -> node.copy(
            children = node.children.flatMap { boxChild ->
                val inner = boxChild.node
                if (inner is BookmarkNode) {
                    listOf(
                        boxChild,
                        BoxChild(AnchorNode(TOC_ANCHOR_PREFIX + anchorIndex++), BoxAlignment.TopStart),
                    )
                } else {
                    listOf(boxChild.copy(node = transform(inner)))
                }
            },
        )
        is WeightNode -> node.copy(child = transformSingle(node.child))
        is LinkNode -> node.copy(child = transformSingle(node.child))
        is InternalLinkNode -> node.copy(child = transformSingle(node.child))
        is TableNode -> node.copy(
            headerRow = node.headerRow?.let { header ->
                header.copy(cells = header.cells.map { it.copy(content = transformSingle(it.content)) })
            },
            rows = node.rows.map { row ->
                row.copy(cells = row.cells.map { it.copy(content = transformSingle(it.content)) })
            },
        )
        else -> node
    }

    /**
     * Bookmark markers expand to a `[bookmark, anchor]` pair, which only
     * a child *list* can host — containers route through here.
     */
    private fun transformChildren(children: List<PdfNode>): List<PdfNode> = children.flatMap { child ->
        if (child is BookmarkNode) {
            listOf(child, AnchorNode(TOC_ANCHOR_PREFIX + anchorIndex++))
        } else {
            listOf(transform(child))
        }
    }

    /** Single-child slots (weights, links, cells) wrap the pair in a column. */
    private fun transformSingle(node: PdfNode): PdfNode = if (node is BookmarkNode) {
        ColumnNode(children = listOf(node, AnchorNode(TOC_ANCHOR_PREFIX + anchorIndex++)))
    } else {
        transform(node)
    }
}

/**
 * Builds the entry rows: indented title, dotted leader stretching across
 * the leftover width, page number — the whole row wrapped in an internal
 * link to the bookmark's injected anchor.
 */
private fun buildTocColumn(entries: List<TocEntry>, node: TocNode): ColumnNode = ColumnNode(
    children = entries.map { entry ->
        InternalLinkNode(
            anchorId = TOC_ANCHOR_PREFIX + entry.index,
            child = RowNode(
                children = listOf(
                    SpacerNode(width = Dp(node.indentPerLevel.value * entry.level), height = Dp.Zero),
                    TextNode(entry.title, node.style),
                    WeightNode(
                        weight = 1f,
                        child = DividerNode(
                            thickness = Dp(0.75f),
                            color = PdfColor.Gray,
                            style = LineStyle.Dotted,
                        ),
                    ),
                    TextNode(entry.pageNumber.toString(), node.style),
                ),
                spacing = Dp(4f),
                verticalAlignment = VerticalAlignment.Bottom,
            ),
        )
    },
    spacing = node.spacing,
)
