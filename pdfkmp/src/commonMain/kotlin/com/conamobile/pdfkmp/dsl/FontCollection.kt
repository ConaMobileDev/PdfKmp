package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.DividerNode
import com.conamobile.pdfkmp.node.ImageNode
import com.conamobile.pdfkmp.node.LinkNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.RichTextNode
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.ShapeNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.VectorNode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Walks the node tree and accumulates every [PdfFont.Custom] referenced by a
 * descendant text style into [sink]. Used at document build time so the
 * renderer can pre-register every custom font on the platform once, before
 * any draw call needs it.
 *
 * Insertion order in the underlying [LinkedHashSet] is preserved so that
 * registration is deterministic across builds.
 */
internal fun collectCustomFonts(node: PdfNode, sink: MutableSet<PdfFont.Custom>) {
    when (node) {
        is TextNode -> {
            val font = node.style.font
            if (font is PdfFont.Custom) sink += font
        }
        is RichTextNode -> node.spans.forEach { span ->
            val font = span.style.font
            if (font is PdfFont.Custom) sink += font
        }
        is ColumnNode -> node.children.forEach { collectCustomFonts(it, sink) }
        is RowNode -> node.children.forEach { collectCustomFonts(it, sink) }
        is WeightNode -> collectCustomFonts(node.child, sink)
        is TableNode -> {
            node.headerRow?.cells?.forEach { collectCustomFonts(it.content, sink) }
            node.rows.forEach { row ->
                row.cells.forEach { cell -> collectCustomFonts(cell.content, sink) }
            }
        }
        is BoxNode -> node.children.forEach { collectCustomFonts(it.node, sink) }
        is LinkNode -> collectCustomFonts(node.child, sink)
        is SpacerNode, is ImageNode, is VectorNode, is DividerNode, is ShapeNode -> Unit
    }
}
