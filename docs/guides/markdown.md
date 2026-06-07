# Markdown to PDF

The optional `io.github.conamobiledev:pdfkmp-markdown` module renders a
CommonMark-lite subset through the PdfKmp DSL with a single extension on
`ContainerScope`.

Install it alongside the core dependency:

```kotlin
implementation("io.github.conamobiledev:pdfkmp:1.1.1")
implementation("io.github.conamobiledev:pdfkmp-markdown:1.1.1")
```

## Usage

```kotlin
import com.conamobile.pdfkmp.markdown.markdown
import com.conamobile.pdfkmp.markdown.MarkdownTheme

pdf {
    page {
        markdown(
            """
            # Release notes
            **Bold**, *italic*, `code`, ~~strike~~ and [a link](https://example.com).
            - bullet one
            - bullet two

            > A blockquote.

            | Col A | Col B |
            |-------|-------|
            | 1     | 2     |
            """.trimIndent(),
            theme = MarkdownTheme(),
        )
    }
}
```

## Supported syntax

| Syntax | Supported |
|---|---|
| ATX headings (`#` … `######`) | ✅ |
| Paragraphs | ✅ |
| Inline `**bold**` | ✅ |
| Inline `*italic*` / `_italic_` | ✅ (both asterisk and underscore) |
| Inline `***bold italic***` | ✅ (combined emphasis) |
| Inline `` `code` `` | ✅ |
| Inline `~~strike~~` | ✅ |
| Inline `[text](url)` | ✅ (clickable only when the paragraph *is* the link — see below) |
| Ordered & unordered lists | ✅ |
| Fenced code blocks | ✅ |
| Blockquotes | ✅ |
| Horizontal rules | ✅ |
| GitHub pipe tables | ✅ |
| Anything else | degrades to plain paragraph text (never throws) |

## Theme

`MarkdownTheme()` carries every styling knob; pass a customised instance to
restyle the output. The data class has five fields:

| Field | Type | Default | Purpose |
|---|---|---|---|
| `baseTextStyle` | `TextStyle` | `TextStyle()` | Body text style. Headings scale its `fontSize`; all other blocks inherit its colour, font, and size. |
| `codeBackground` | `PdfColor` | light grey (`0.95, 0.95, 0.95`) | Fill behind fenced and inline code. |
| `linkColor` | `PdfColor` | `PdfColor.Blue` | Colour of link text and the underline drawn under inline links. |
| `headingScales` | `List<Float>` | `[2.0, 1.6, 1.3, 1.15, 1.05, 1.0]` | Six multipliers applied to `baseTextStyle.fontSize` for `h1`..`h6`. Missing levels fall back to `1f`. |
| `blockSpacing` | `Dp` | `8.dp` | Vertical gap inserted between consecutive blocks. |

```kotlin
MarkdownTheme(
    baseTextStyle = TextStyle(fontSize = 11.sp, color = PdfColor.DarkGray),
    linkColor = PdfColor(0.1f, 0.45f, 0.85f),
    headingScales = listOf(2.2f, 1.7f, 1.4f, 1.2f, 1.05f, 1f),
    blockSpacing = 10.dp,
)
```

## Limitations

!!! warning "Link clickability"
    A link gets a real clickable area **only** when a paragraph is *entirely* a
    single `[text](url)`. A link *within* running text is styled (coloured +
    underlined) but not clickable.

!!! warning "No monospace font"
    Code has **no guaranteed monospace font** (PdfKmp bundles none). It renders in
    the base font at a slightly smaller size on the theme's code background, so
    column alignment in code blocks is approximate.

## See also

- [Text & typography](text.md) — the rich-text engine markdown builds on.
- [Links, bookmarks & TOC](navigation.md) — how standalone links become clickable.
