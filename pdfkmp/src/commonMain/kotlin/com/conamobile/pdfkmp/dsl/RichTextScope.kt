package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.node.Span
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Sp

/**
 * Receiver of `richText { ... }`.
 *
 * Add a [span] for every contiguous styled run; the renderer wraps them
 * together as one paragraph. Style inheritance follows the same cascade
 * as [text]: every span starts from the `richText` block's own
 * [defaultSpanStyle] (which itself inherits from the enclosing
 * [ContainerScope.textStyle]) and the per-span configuration block then
 * overrides whichever properties differ.
 */
@PdfDsl
public class RichTextScope internal constructor(
    private val baseStyle: TextStyle,
) {
    /** Default style applied to every [span] unless overridden by the per-span block. */
    public var defaultSpanStyle: TextStyle = baseStyle

    /** Horizontal alignment of wrapped lines inside the paragraph slot. */
    public var align: TextAlign = baseStyle.align

    /** Override line height; `0.sp` picks the spans' own metrics. */
    public var lineHeight: Sp = baseStyle.lineHeight

    internal val spans: MutableList<Span> = mutableListOf()

    /**
     * Appends a styled run.
     *
     * Calls without a [block] reuse [defaultSpanStyle] verbatim. Calls
     * with a block enter a [TextScope] (the same receiver as the regular
     * `text(...)` DSL), so any property the user knows from there works
     * here too — `bold`, `italic`, `color`, `underline`, etc.
     *
     * @param text literal characters of this span. Pre-existing whitespace
     *   is preserved; newline characters split into hard lines just like
     *   in [text].
     */
    public fun span(text: String, block: TextScope.() -> Unit = {}) {
        val scope = TextScope(defaultSpanStyle).apply(block)
        spans += Span(text, scope.build())
    }
}
