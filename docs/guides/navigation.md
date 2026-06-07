# Links, bookmarks & TOC

## Hyperlinks

`link(url) { … }` wraps any content in a clickable region:

```kotlin
link(url = "https://github.com/conamobiledev/PdfKmp") {
    text("github.com/conamobiledev/PdfKmp") {
        color = PdfColor.Blue; underline = true
    }
}

link(url = "mailto:hello@example.com") {
    text("hello@example.com") { color = PdfColor.Blue; underline = true }
}

// Whole card clickable:
link(url = "https://example.com") {
    card(border = BorderStroke(1.dp, PdfColor.Blue)) {
        text("Visit site →") { color = PdfColor.Blue; bold = true }
    }
}
```

All three native platforms produce **real, clickable PDF link annotations** that
work in any reader (Preview, Adobe Reader, Chrome): iOS via
`UIGraphicsSetPDFContextURLForRect`, Desktop via PDFBox `PDAnnotationLink`, and
Android via a post-processing incremental update applied in `finish()` (since
`android.graphics.pdf.PdfDocument` exposes no annotation API). The post-processor
is defensive: any parse surprise returns the original bytes unchanged.

## Internal links, anchors & cross-references

`anchor("id")` marks a jump target; `linkToAnchor(anchor = "id") { … }` makes
content clickable to it (forward references resolve at `finish()`). Both are
clickable on all three native platforms (Android via the same post-processor):

```kotlin
anchor("intro")
text("1. Introduction") { fontSize = 22.sp; bold = true }
// … later, on another page …
linkToAnchor(anchor = "intro") {
    text("← Back to the introduction") { color = PdfColor.Blue; underline = true }
}
```

## Bookmarks & outline

`bookmark("title", level)` adds an entry to the reader's outline sidebar — place
it right before the heading it labels:

```kotlin
bookmark("Introduction")             // level 0
text("1. Introduction") { fontSize = 22.sp; bold = true }
bookmark("Motivation", level = 1)
text("1.1 Motivation") { fontSize = 16.sp; bold = true }
```

The outline works on **all three native platforms** (Android via the
post-processor; iOS and Desktop natively).

## Table of contents

`tableOfContents()` expands every bookmark into a clickable row — title, dotted
leader, resolved page number — using a dry-run pagination pass so forward
references (the TOC usually sits before the chapters) and the page shift the TOC
itself introduces both come out correct.

```kotlin
page {
    text("Contents") { fontSize = 26.sp; bold = true }
    tableOfContents(maxLevel = 1)        // 0 = chapters only, 1 = + sections, …
}
page {
    bookmark("Introduction")
    text("1. Introduction") { fontSize = 22.sp; bold = true }
}
```

!!! warning "Page body only"
    `tableOfContents()` is only valid in a page body — headers, footers, and
    watermarks are rebuilt per physical page and cannot host one.

!!! note "Web backend"
    The Wasm backend writes named destinations, the outline, and the info
    dictionary, so links / bookmarks / TOC carry over to the browser too. See
    [Web (Kotlin/Wasm)](web.md).

## See also

- [Platform parity](platform-parity.md) — the per-platform support matrix.
- `Samples.navigation()`, `Samples.pageChrome()`.
