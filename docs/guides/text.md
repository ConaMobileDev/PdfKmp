# Text & typography

Text takes a string plus an optional configuration block. Every property starts
at the inherited `textStyle` and can be overridden. Set the document-wide baseline
once with `defaultTextStyle` on the `pdf { }` scope — every page inherits it until
a `text { }` block overrides a property:

```kotlin
pdf {
    defaultTextStyle = TextStyle(fontSize = 12.sp, color = PdfColor.DarkGray)
    page { text("Inherits 12.sp dark grey") }
}
```

```kotlin
text("Heading") {
    fontSize = 28.sp
    fontWeight = FontWeight.Bold      // or `bold = true`
    fontStyle = FontStyle.Italic      // or `italic = true`
    color = PdfColor.Black
    letterSpacing = 1.5.sp
    lineHeight = 32.sp                // 0 keeps the font's natural value
    underline = true
    strikethrough = false
    align = TextAlign.Center          // Start / Center / End / Justify
    font = PdfFont.Default            // Inter (bundled)
}
```

## Colours

```kotlin
PdfColor.Red / Green / Blue / Black / White / Gray / LightGray / DarkGray
PdfColor(r, g, b, a)                       // floats in 0..1
PdfColor.fromRgb(0xFF5722)                 // hex literal as Long
PdfColor.fromHex("#FF5722")                // hex string — RGB / RRGGBB / AARRGGBB
PdfColor.Blue.withAlpha(0.2f)              // copy with new opacity
```

## Justification, line clamping & word breaking

`TextAlign.Justify` distributes inter-word slack on every line except the
paragraph's last (it also stretches the space runs inside `richText`). Clamp a
paragraph with `maxLines` and choose how the cut line ends with `overflow`:

```kotlin
text(longParagraph) { align = TextAlign.Justify }

text(longParagraph) {
    maxLines = 2
    overflow = TextOverflow.Ellipsis   // or TextOverflow.Clip
}
```

Long words wrap gracefully: a **soft hyphen** (`U+00AD`) is an invisible break
opportunity that renders a real `-` only when a wrap lands on it, and any word
still wider than its slot is split into fitting chunks instead of overflowing.

```kotlin
text("Donau­dampf­schiff­fahrts­gesellschaft")  // breaks on the soft hyphens
```

### Automatic hyphenation

Set `hyphenation` to a bundled dictionary to let a too-long word break at legal
points (Liang's algorithm — the same approach TeX uses) before falling back to a
mid-word hard cut. A taken break renders the same visible `-` as a soft hyphen,
and author-supplied soft hyphens are never overridden:

```kotlin
text(longParagraph) {
    align = TextAlign.Justify
    hyphenation = Hyphenators.EnUs
}
```

!!! note "Reduced US-English pattern set"
    `Hyphenators.EnUs` embeds a curated, **genuine subset** of Liang's
    `hyphen.tex` patterns (the canonical TeX list is ~4400 entries). Common
    vocabulary hyphenates correctly (`hy-phen-ation`, `com-put-er`,
    `algo-rithm`), but long-tail or rare words may miss some valid break points —
    the algorithm never invents a wrong break, it simply offers fewer of them. No
    fabricated patterns are included. Words with a literal `-`, digits, or fewer
    than five letters are left untouched.

## Orphan / widow control

Under the `Slice` page-break strategy, keep a minimum number of paragraph lines
together at a page boundary:

```kotlin
text(longParagraph) {
    minLinesBeforeBreak = 2   // keep >=2 lines at the bottom before splitting
    minLinesAfterBreak = 2    // carry >=2 lines onto the next page
}
```

## Rich text (multi-style spans)

When a single paragraph mixes weights, colours, or decorations and must wrap as
one block of prose, use `richText { ... }`:

```kotlin
richText {
    span("This sentence mixes ")
    span("bold") { bold = true }
    span(", ")
    span("italic") { italic = true }
    span(", ")
    span("red") { color = PdfColor.Red }
    span(" runs while wrapping naturally.")
}
```

The wrapper packs every span into one paragraph, wraps at the parent's width, and
respects per-span styling end-to-end. The line height scales up to fit the
largest run.

### Superscript & subscript

Per-span `script` raises or lowers a run and auto-shrinks it. It is only
meaningful on `richText` spans:

```kotlin
richText {
    span("Pythagoras: a")
    span("2") { script = TextScript.Superscript }
    span(" + b")
    span("2") { script = TextScript.Superscript }
    span(" = c")
    span("2") { script = TextScript.Superscript }
    span("   ·   Chemistry: H")
    span("2") { script = TextScript.Subscript }
    span("O")
}
```

## Right-to-left text

Set `direction` on the text style. The default `Auto` detects Hebrew / Arabic
from the first strong character and anchors `Start` / `End` / `Justify` to the
correct edge:

```kotlin
text("שלום עולם — טקסט בעברית") { fontSize = 14.sp }     // Auto detects RTL
text("مرحبا بالعالم") { font = PdfFont.SystemArabic }    // shapes natively on Android/iOS
text("English forced RTL") { direction = TextDirection.Rtl }
```

Android and iOS shape glyphs natively (bidi reorder + Arabic contextual forms);
the Desktop/JVM backend runs its own bidi reorder + Arabic shaping pass
(presentation forms, lam-alef ligatures) because PDFBox does neither.

For justified Arabic, set `kashidaJustify = true` so `TextAlign.Justify` elongates
words at cursive joining points with tatweel (`U+0640`) before widening word gaps —
the way Arabic typography expects:

```kotlin
text(arabicParagraph) {
    direction = TextDirection.Rtl
    align = TextAlign.Justify
    kashidaJustify = true
    font = PdfFont.SystemArabic
}
```

It only takes effect when the line's resolved direction is right-to-left and the
paragraph is justified; it is ignored for left-to-right text. See
[Fonts, i18n & RTL](i18n.md#kashida-justification) for more.

!!! warning "Mixed-style RTL limitation"
    Inside a single `richText { }` paragraph, RTL spans keep their authored
    (source) order rather than being reordered as one visual run. Author a whole
    RTL paragraph as a single `text(...)` (or one span) when visual segment order
    matters.

!!! note "Fonts for non-Latin scripts"
    PdfKmp bundles Inter (Latin). CJK / Arabic / Persian need the `PdfFont.System*`
    references (Android / iOS) or a registered `PdfFont.Custom` (Desktop / Web).
    See [Fonts, i18n & RTL](i18n.md).

## See also

- [Layout containers](layout.md) — arranging text in columns, rows, weighted slots.
- [Fonts, i18n & RTL](i18n.md) — bundled Inter, system fonts, custom fonts.
- [`Samples.typography()` and `Samples.textAdvanced()`](../samples.md).
