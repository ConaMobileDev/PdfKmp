# Tables

The `table { ... }` DSL builds a header + body of rows, each holding cells. Cells
can wrap arbitrary node trees.

```kotlin
table(
    columns = listOf(
        TableColumn.Fixed(70.dp),     // fixed width
        TableColumn.Weight(2f),       // share of remaining space
        TableColumn.Weight(1f),
    ),
    border = TableBorder(color = PdfColor.fromRgb(0xCFD8DC), width = 1.dp),
    cornerRadius = 10.dp,
    cellPadding = Padding.symmetric(horizontal = 12.dp, vertical = 10.dp),
) {
    header(background = PdfColor.fromRgb(0xECEFF1)) {
        cell("ID")
        cell("Customer")
        cell("Status", horizontalAlignment = HorizontalAlignment.End)
    }

    users.forEachIndexed { i, user ->
        val zebra = if (i % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)
        row(background = zebra) {
            cell(user.id) { color = PdfColor.Gray }
            cell {
                text(user.name) { bold = true }
                text(user.email) { fontSize = 10.sp; color = PdfColor.Gray }
            }
            cell(
                value = user.status,
                horizontalAlignment = HorizontalAlignment.End,
                verticalAlignment = VerticalAlignment.Center,
            )
        }
    }
}
```

## Columns: fixed vs weight

| Column type | Behaviour |
|---|---|
| `TableColumn.Fixed(width)` | A fixed point width. |
| `TableColumn.Weight(n)` | A fractional share of the space remaining after fixed columns. |

!!! note "Over-wide fixed columns shrink"
    Under `Slice`, fixed columns wider than the page shrink proportionally to fit
    instead of spilling past the margin.

## Header

`header { }` declares the header row. Pass a `background` and style each `cell`
like any other:

```kotlin
header { cell("#") { bold = true }; cell("Item") { bold = true }; cell("Price") { bold = true } }
```

## Zebra striping

There's no built-in stripe option — pass a per-row `background` and alternate it
yourself:

```kotlin
row(background = if (i % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)) { … }
```

Pass `null` for no background on a row.

## Cell alignment, padding & content

- `cell(value, horizontalAlignment = …, verticalAlignment = …) { … }` — align
  inside the cell and style the text in the trailing block.
- `cellPadding` on the `table(...)` sets the padding inside every cell.
- Override padding for a single row via `row(cellPadding = …)`, and force a
  minimum row height via `row(minHeight = …)` / `header(minHeight = …)`.
- A `cell { … }` block can stack any node tree — a title + subtitle, an icon,
  nested rows.

```kotlin
cell {
    text(user.name) { bold = true }
    text(user.email) { fontSize = 10.sp; color = PdfColor.Gray }
}
```

## Merged cells — `colSpan` / `rowSpan`

Both `cell` overloads take `colSpan` / `rowSpan` (default `1`) for HTML-style cell
merging. `colSpan > 1` merges the cell across the next `colSpan - 1` columns of the
same row; `rowSpan > 1` extends it downward across the next `rowSpan - 1` rows. The
later cells in those rows shift to fill the columns the merge left free.

```kotlin
table(
    columns = listOf(TableColumn.Weight(1f), TableColumn.Weight(1f), TableColumn.Weight(1f)),
    border = TableBorder(color = PdfColor.fromRgb(0xCFD8DC), width = 1.dp),
    cellPadding = Padding.symmetric(horizontal = 10.dp, vertical = 8.dp),
) {
    row(background = PdfColor.fromRgb(0xECEFF1)) {
        // One banner spanning all three columns.
        cell("Quarterly summary", horizontalAlignment = HorizontalAlignment.Center, colSpan = 3) {
            bold = true
        }
    }
    row {
        // Label spanning the next two body rows.
        cell("H1", verticalAlignment = VerticalAlignment.Center, rowSpan = 2) { bold = true }
        cell("Q1")
        cell("+4%", horizontalAlignment = HorizontalAlignment.End)
    }
    row {
        cell("Q2")               // the H1 label still occupies column 0 here
        cell("+7%", horizontalAlignment = HorizontalAlignment.End)
    }
}
```

The layout tracks merges on an **occupancy grid**:

- **Separators never cross a merged region** — no grid line is drawn through the
  span, so a banner or a tall label reads as one solid cell.
- **Row heights grow to fit** spanning content, and the spanned columns share the
  cell's width.
- **Page slicing keeps a `rowSpan` block atomic** — a set of rows joined by a
  vertical span moves to the next page together rather than tearing mid-merge.

Both spans are clamped to the grid at layout time, and a span of `1` is
bit-for-bit identical to the old single-cell layout.

!!! note "Renders on every platform"
    `colSpan` / `rowSpan` are a layout-engine feature, so they look identical on
    Android, iOS, Desktop, and Web.

## Borders

| Option | Effect |
|---|---|
| `TableBorder(color, width)` | Full grid lines. |
| `TableBorder.None` | No lines at all. |
| `showOutline` / `showHorizontalLines` / `showVerticalLines` | Toggle each independently. |

## Row slicing & repeating headers

Under `PageBreakStrategy.Slice`, a table taller than a page splits between rows,
and the header row repeats at the top of every continuation page
(`repeatHeader = true`, the default).

```kotlin
defaultPageBreakStrategy = PageBreakStrategy.Slice
table(
    columns = listOf(TableColumn.Fixed(50.dp), TableColumn.Weight(2f), TableColumn.Weight(1f)),
    border = TableBorder(color = PdfColor.LightGray, width = 0.5.dp),
    repeatHeader = true,    // re-draw the header on every page the table flows onto
) {
    header { cell("#") { bold = true }; cell("Item") { bold = true }; cell("Price") { bold = true } }
    repeat(80) { i -> row { cell("${i + 1}"); cell("Item ${i + 1}"); cell("$${(i + 1) * 3}.00") } }
}
```

!!! note "Split tables drop their corner radius"
    A table that splits across pages drops its `cornerRadius` for the fragments
    rather than re-rounding every piece.

## See also

- [Layout containers](layout.md) — weighted children and page-break strategies.
- `Samples.tableShowcase()` (also demos the `colSpan` / `rowSpan` merge),
  `Samples.longTable()`, `Samples.alignmentShowcase()`.
