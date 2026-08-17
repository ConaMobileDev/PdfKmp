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

### Which URLs become annotations

Only four schemes are embedded: **`http`, `https`, `mailto`, `tel`**. Anything
else — `javascript:`, `file:`, `data:`, `content:`, `intent:`, a relative path,
a bare `www.example.com`, a scheme-relative `//host`, or any URL containing a
control character — is **skipped**. The wrapped content is still drawn, just
without the annotation, and the skip is reported through `PdfLog`:

```kotlin
link(url = "javascript:alert(1)") {
    text("clickable?")   // drawn, but no annotation is written
}
```

A generated PDF outlives the app that wrote it and is opened by arbitrary
readers, so an annotation carrying `javascript:` or `file:` is a
code-execution / local-file channel in every reader that honours those schemes.
That matters most when link targets come from untrusted input — a CMS field, a
server response, user-authored markdown.

Pre-flight a URL with the same rule the DSL applies:

```kotlin
if (PdfUrls.isSafeExternalUrl(candidate)) {
    link(url = candidate) { text(label) { color = PdfColor.Blue; underline = true } }
} else {
    text(label)          // style it as plain text — no click is coming
}
```

`pdfkmp-viewer` applies the same allowlist a second time, before a tapped link
reaches the OS, so a document authored elsewhere cannot turn a tap into an
OS-level deep link.

For an internal-distribution document whose targets you control — a company
deep link, say — widen the set once at startup:

```kotlin
PdfUrls.allowedSchemes = PdfUrls.DEFAULT_ALLOWED_SCHEMES + "myapp"
```

!!! warning "This is a process-wide switch"
    It governs annotation writing *and* the viewer's tap handling, so adding
    `javascript` or `file` re-opens the channels the default set exists to
    close. Never derive it from document content.

!!! note "Internal navigation is unaffected"
    `linkToAnchor` targets an in-document anchor rather than an external URL and
    never goes through the allowlist.

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
