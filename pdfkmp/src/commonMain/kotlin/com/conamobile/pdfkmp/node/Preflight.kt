package com.conamobile.pdfkmp.node

/**
 * Walks every page's content tree and replaces every [LazyNode] with the
 * node returned by its [LazyNode.resolver].
 *
 * Recurses into containers ([ColumnNode], [RowNode], [BoxNode], [TableNode],
 * [WeightNode], [LinkNode]) so a `LazyNode` nested anywhere inside a page
 * body or watermark is resolved before the layout engine ever sees it.
 *
 * Headers and footers are stored as `(PageContext) -> ColumnNode` factories
 * — they're invoked per page during the render pass. The preflight pass
 * does **not** call those factories, so a `LazyNode` inside a header /
 * footer produces a clear "LazyNode encountered" error when the renderer
 * runs. Keep async-loaded resources in the page body.
 *
 * Implementation note: we recursively resolve the produced node so a
 * resolver is allowed to return a container that itself contains other
 * `LazyNode`s. The recursion terminates because each step strips at
 * least one lazy placeholder and the document tree is finite.
 */
internal suspend fun DocumentSpec.resolveDeferred(): DocumentSpec = copy(
    pages = pages.map { page ->
        page.copy(
            content = page.content.resolve() as ColumnNode,
            watermark = page.watermark?.resolve() as BoxNode?,
        )
    },
)

private suspend fun PdfNode.resolve(): PdfNode = when (this) {
    is LazyNode -> resolver().resolve()

    is ColumnNode -> copy(children = children.map { it.resolve() })

    is RowNode -> copy(children = children.map { it.resolve() })

    is BoxNode -> copy(
        children = children.map { it.copy(node = it.node.resolve()) },
    )

    is TableNode -> copy(
        rows = rows.map { row ->
            row.copy(
                cells = row.cells.map { cell ->
                    cell.copy(content = cell.content.resolve())
                },
            )
        },
        headerRow = headerRow?.let { header ->
            header.copy(
                cells = header.cells.map { cell ->
                    cell.copy(content = cell.content.resolve())
                },
            )
        },
    )

    is WeightNode -> copy(child = child.resolve())

    is LinkNode -> copy(child = child.resolve())

    is TextNode,
    is ImageNode,
    is VectorNode,
    is SpacerNode,
    is DividerNode,
    is RichTextNode,
    is ShapeNode,
    -> this
}
