# Pages, headers & watermarks

## Document & pages

Every document opens with `pdf { ... }`. Inside, configure metadata,
document-wide defaults, and add one or more pages:

```kotlin
pdf {
    metadata {
        title = "Quarterly Report"
        author = "PdfKmp"
        subject = "Sales summary for Q1"
        keywords = "sales, q1, report"   // comma-separated single string
    }

    // Defaults inherited by every page until overridden inside the page block.
    defaultTextStyle = TextStyle(fontSize = 12.sp, color = PdfColor.DarkGray)
    defaultPagePadding = Padding.symmetric(horizontal = 40.dp, vertical = 56.dp)
    defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage

    page(size = PageSize.A4) {
        spacing = 12.dp
        text("First page")
    }

    page(size = PageSize.Letter) {
        padding = Padding.all(20.dp) // override default for this page
        text("Second page — Letter format")
    }
}
```

## Page sizes & orientation

Available sizes: `PageSize.A3`, `A4`, `A5`, `Letter`, `Legal`, plus
`PageSize.custom(widthPt, heightPt)`. `PageSize.landscape` / `.portrait` flip any
size, and pages may **mix orientations** — each `page(size)` carries its own
dimensions:

```kotlin
page(PageSize.A5) { text("Portrait page") }
page(PageSize.A5.landscape) { text("Landscape interlude") }
```

## Padding

Use `Padding.all`, `Padding.symmetric`, or pass each side explicitly. Set a
document default with `defaultPagePadding`, override per page with `padding`.

## Headers, footers & page numbers

Every logical page can declare a header, footer, and watermark. Headers and
footers receive a `PageContext` carrying the current page number and the total
page count — accurate even when content slices across multiple physical pages,
because the renderer does a counting dry-run before the real pass.

```kotlin
page {
    pageBreakStrategy = PageBreakStrategy.Slice

    header { ctx ->
        row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
            text("Quarterly Report") { bold = true; fontSize = 12.sp }
            text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                fontSize = 11.sp; color = PdfColor.Gray
            }
        }
        divider(thickness = 0.5.dp, color = PdfColor.LightGray)
    }

    footer { ctx ->
        divider(thickness = 0.5.dp, color = PdfColor.LightGray, style = LineStyle.Dashed)
        text("conamobile · pdfkmp") {
            fontSize = 10.sp; color = PdfColor.Gray; align = TextAlign.Center
        }
    }

    // … body content
}
```

Header is rendered inside the top page-padding band, footer inside the bottom
padding band.

### Book-style chrome with PageContext

`PageContext` carries `isFirst` / `isLast` / `isEven` / `isOdd` alongside
`pageNumber` / `totalPages`, so you can drop chrome on the cover and mirror
headers between verso and recto pages:

```kotlin
page(PageSize.A5) {
    header { ctx ->
        when {
            ctx.isFirst -> Unit                              // no chrome on the cover
            ctx.isEven  -> row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                text("Title"); text("${ctx.pageNumber}")     // verso: title left, number right
            }
            else        -> row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                text("${ctx.pageNumber}"); text("Title")     // recto: mirrored
            }
        }
    }
}
```

## Watermark

```kotlin
page {
    watermark {
        aligned(BoxAlignment.Center) {
            text("DRAFT") {
                fontSize = 120.sp; bold = true
                color = PdfColor(0.92f, 0.92f, 0.95f)
            }
        }
    }
    // … body content
}
```

!!! note "Watermark renders behind the body"
    The watermark is drawn behind the body content of every physical page, not in
    front. To stamp something visible above content, draw it at the end of the
    body or use a `box` with a top child.

## Metadata

`metadata { … }` sets the info dictionary plus accessibility / compliance flags:

```kotlin
metadata {
    title = "Quarterly Report"
    author = "PdfKmp"
    subject = "Sales summary"
    keywords = "sales, q1"     // String?, comma-separated — not a List
    creator = "Acme Reporting" // application that created the source content
    language = "en"            // /Lang for accessibility (JVM tagged output)
    pdfACompliance = true      // best-effort PDF/A-2b (JVM only)
}
```

`keywords` and `creator` are both `String?`. `producer` is also exposed and
defaults to `"PdfKmp"`, so you rarely set it by hand.

See [Forms & security](forms-security.md) for the PDF/A and language details, and
[Platform parity](platform-parity.md) for which backends write the info
dictionary.

## See also

- [Layout containers](layout.md) — page-break strategies in depth.
- [Links, bookmarks & TOC](navigation.md).
- `Samples.pageChrome()`, `Samples.pageTemplates()`, `Samples.customPadding()`.
