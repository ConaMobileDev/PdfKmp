package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.node.BoxChild
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.style.TextStyle

/**
 * Receiver of `box { ... }`.
 *
 * Inherits every child function from [ContainerScope] (`text`, `column`,
 * `row`, `image`, `vector`, ...). Calls added directly take the default
 * [BoxAlignment.TopStart] anchor; wrap a single child in [aligned] to
 * place it elsewhere inside the box.
 *
 * Z-order follows source order: the first added child is at the bottom
 * and each subsequent child is drawn on top. That lets you stack a
 * background image, a darkening overlay, and text labels in three
 * intuitive lines.
 */
@PdfDsl
public class BoxScope internal constructor(textStyle: TextStyle) : ContainerScope(textStyle) {

    /**
     * Per-child alignment overrides keyed by child index. Children added
     * through [aligned] register their slot here; children added via the
     * inherited DSL functions ([text], [image], …) are absent from the
     * map and fall back to [BoxAlignment.TopStart] when [build] runs.
     */
    private val alignments: MutableMap<Int, BoxAlignment> = mutableMapOf()

    /**
     * Anchors the children added inside [block] at [alignment] within
     * the surrounding box. Multiple children inside the block stack
     * vertically (the block has a [ColumnScope] receiver), so put a
     * single logical block of content per call.
     *
     * Example:
     * ```
     * box(width = 300.dp, height = 200.dp) {
     *     image(bytes = heroBytes, contentScale = ContentScale.Crop)
     *     aligned(BoxAlignment.BottomEnd) {
     *         text("Page 1") { color = PdfColor.White }
     *     }
     * }
     * ```
     */
    public fun aligned(alignment: BoxAlignment, block: ColumnScope.() -> Unit) {
        val inner = ColumnScope(textStyle).apply(block)
        children += ColumnNode(children = inner.children.toList())
        alignments[children.lastIndex] = alignment
    }

    internal fun build(): List<BoxChild> = children.mapIndexed { index, node ->
        BoxChild(node = node, alignment = alignments[index] ?: BoxAlignment.TopStart)
    }
}
