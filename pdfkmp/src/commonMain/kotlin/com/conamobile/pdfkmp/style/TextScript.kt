package com.conamobile.pdfkmp.style

/**
 * Vertical script position of a rich-text span.
 *
 * Superscript and subscript spans are rendered at a reduced font size and
 * shifted relative to the line's baseline — the classic `x²` / `H₂O`
 * typography. The size reduction and baseline shift are computed by the
 * layout engine so every platform backend renders the same geometry.
 *
 * Only meaningful inside `richText { span(...) { script = ... } }` — a
 * whole paragraph can't be "superscript of itself", so plain `text { }`
 * blocks ignore this property.
 */
public enum class TextScript {
    /** Normal baseline position. The default. */
    None,

    /** Raised and shrunk, like the exponent in `x²`. */
    Superscript,

    /** Lowered and shrunk, like the index in `H₂O`. */
    Subscript,
}
