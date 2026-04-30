package com.conamobile.pdfkmp.node

import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Sp

/**
 * One contiguous run of text with a single style. Spans are the basic
 * building block of [RichTextNode] — they let one paragraph mix bold,
 * italic, coloured, or otherwise differently styled segments without
 * breaking out of a single shared line-wrap pass.
 *
 * @property text the literal characters of this span. Whitespace is
 *   significant — leading / trailing spaces survive layout.
 * @property style the [TextStyle] applied to every glyph in [text].
 *   Inherits from the surrounding paragraph defaults; the DSL handles
 *   the cascade.
 */
public data class Span(
    val text: String,
    val style: TextStyle,
)

/**
 * Multi-style paragraph. A flat list of [spans] are word-wrapped together
 * across one paragraph box; line breaks happen at word boundaries the
 * same way as a plain [TextNode], but each line preserves its underlying
 * span styles instead of collapsing them.
 *
 * @property spans the spans in source order. The paragraph reads them as
 *   if they were concatenated.
 * @property align horizontal alignment of wrapped lines inside the
 *   paragraph slot. Same semantics as [TextStyle.align].
 * @property lineHeight optional override for the paragraph's line
 *   height. `0.sp` means the renderer picks the maximum natural line
 *   height across the spans on each line.
 */
public data class RichTextNode(
    val spans: List<Span>,
    val align: TextAlign = TextAlign.Start,
    val lineHeight: Sp = Sp.Zero,
) : PdfNode
