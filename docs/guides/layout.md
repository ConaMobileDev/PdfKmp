# Layout containers

PdfKmp uses Compose-flavoured layout primitives. Container size grows from its
children unless you set an explicit `width` / `height`.

## Column (vertical stack)

```kotlin
column(
    spacing = 8.dp,
    verticalArrangement = VerticalArrangement.Top,    // Top / Center / Bottom / SpaceBetween / ...
    horizontalAlignment = HorizontalAlignment.Start,  // Start / Center / End
) {
    text("First")
    text("Second")
    text("Third")
}
```

## Row (horizontal stack)

```kotlin
row(
    spacing = 12.dp,
    horizontalArrangement = HorizontalArrangement.SpaceBetween,
    verticalAlignment = VerticalAlignment.Center,
) {
    text("Left")
    text("Right")
}
```

## Box (Z-stack)

Layered children — first added is at the bottom, last on top.

```kotlin
box(width = 400.dp, height = 200.dp, cornerRadius = 12.dp) {
    image(bytes = heroBytes, contentScale = ContentScale.Crop)
    aligned(BoxAlignment.BottomStart) {
        text("Hero title") { color = PdfColor.White; fontSize = 28.sp }
    }
}
```

## Card

`card { ... }` is shorthand for a decorated `column` — same decoration vocabulary
(see [Decorations & effects](decorations.md)). With no arguments it defaults to a
**white background, 8.dp corner radius, and `Padding.all(12.dp)`** (no border), so
`card { text("…") }` is already a tidy Material-style surface.

```kotlin
card(
    background = PdfColor.White,
    cornerRadius = 12.dp,
    padding = Padding.all(16.dp),
    border = BorderStroke(1.dp, PdfColor.LightGray),
) {
    text("Material-style card")
}
```

## Weighted children

Inside a `row` or `column`, `weighted(n)` claims a fractional share of the
leftover space along the main axis:

```kotlin
row(spacing = 12.dp) {
    weighted(1f) { text("33%") }
    weighted(2f) { text("66%") }
}
```

!!! note "Intrinsic sizing"
    A `text("foo")` measures to its glyph advance, not the parent's full width.
    Use `weighted(1f)` to claim leftover space, or wrap in a `box(width = …)`.

## Spacer & divider

```kotlin
spacer(height = 24.dp)                                            // explicit gap

divider(thickness = 0.5.dp, color = PdfColor.Gray)               // solid
divider(thickness = 1.dp, color = PdfColor.Gray, style = LineStyle.Dashed)
divider(thickness = 1.5.dp, color = PdfColor.Red, style = LineStyle.Dotted)
```

## Lists

`bulletList` and `numberedList` lay items out one per row, each with a marker in a
fixed-width gutter so wrapped text in an item indents under the first text line,
not under the marker.

```kotlin
bulletList(items = listOf("First point", "Second point", "Third point"))
bulletList(items = listOf("Arrow markers", "Custom bullet"), bullet = "→")

numberedList(items = listOf("Step one", "Step two", "Step three"))
numberedList(items = listOf("Step four", "Step five"), startAt = 4)  // continue a series
```

Both take `markerWidth` (gutter width — `16.dp` for bullets, `20.dp` for numbers
by default) and `spacing` (gap between rows, `4.dp`). Items are plain strings; for
inline emphasis inside a list item, hand-roll a `column { row { … } }` (which is
what the [Markdown renderer](markdown.md) does).

## Multi-column flow (`columns`)

`columns(count, gap)` flows its children into newspaper-style equal-width
columns, height-balanced so the columns end up roughly even.

```kotlin
columns(count = 2, gap = 18.dp, spacing = 8.dp) {
    text("Two columns, one flow") { bold = true }
    text(bodyParagraph) { align = TextAlign.Justify }
    text(bodyParagraph) { align = TextAlign.Justify }
}
```

!!! warning "Single page unit"
    The whole `columns { }` block is laid out as a unit — columns do not continue
    onto the next page. Split very long content into several `columns { }` blocks.

## Uniform grid (`grid`)

`grid(columns)` flows its children row-major into equal-width cells; the last row
pads out with empty cells so every column keeps the same width. Cells can hold
any node:

```kotlin
grid(columns = 3, spacing = 10.dp) {
    repeat(5) { i ->
        card(background = PdfColor(0.95f, 0.96f, 1f), cornerRadius = 6.dp) {
            text("Cell ${i + 1}") { bold = true }
        }
    }
}
```

## Keep together (`keepTogether`)

The `break-inside: avoid` of the DSL — the group moves whole to the next page
under `Slice`:

```kotlin
keepTogether {
    image(bytes = figureBytes, width = 400.dp)
    text("Figure 1 — the caption stays with its figure") { fontSize = 10.sp; color = PdfColor.Gray }
}
```

## Page-break strategies

Long content overflows pages naturally. Two strategies control the split:

```kotlin
page {
    pageBreakStrategy = PageBreakStrategy.MoveToNextPage  // default
    // Whole element moves to a new page if it would not fit.
}

page {
    pageBreakStrategy = PageBreakStrategy.Slice
    // Text splits at line boundaries; tall images cut at the page edge
    // and continue seamlessly on the next page; undecorated columns slice
    // child-by-child; tables split between rows.
}
```

Set the document-wide default via `defaultPageBreakStrategy`, override per page on
`PageScope.pageBreakStrategy`.

Under `Slice`, an **undecorated** `column { }` is the unit that slices: the
renderer recurses into it and moves its children across the page boundary one by
one, so a long stack flows seamlessly without leaving a gap at the bottom of a
page. A **decorated** container (anything with a background / border / corner
radius) is the opposite — it can't be cut mid-decoration, so it moves whole to the
next page if it doesn't fit.

!!! note "Decorated containers move whole"
    A container with a background / border / corner radius is never sliced
    mid-decoration — it moves whole, by design. Only undecorated columns and
    tables slice across pages.

## See also

- [Decorations & effects](decorations.md) — backgrounds, borders, gradients, shadows.
- [Tables](tables.md) — table row slicing and repeating headers.
- [Pages, headers & watermarks](page-setup.md).
- `Samples.rowAndColumn()`, `Samples.columnSpaceBetween()`, `Samples.newsletter()`, `Samples.designExtras()`.
